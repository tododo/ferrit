package org.ferrit.core.crawler

import java.time.{Duration, LocalDateTime}

import akka.actor.{Actor, ActorRef}
import akka.event.Logging
import akka.pattern.{ask, pipe}
import akka.routing.Listeners
import akka.util.Timeout
import org.ferrit.core.crawler.FetchMessages._
import org.ferrit.core.http._
import org.ferrit.core.model.CrawlJob
import org.ferrit.core.parser.{ContentParser, ParserResult}
import org.ferrit.core.robot.RobotRulesCacheActor.{Allow, DelayFor}
import org.ferrit.core.uri.{CrawlUri, FetchJob, Frontier, UriCache}
import org.ferrit.core.util._

import scala.concurrent.Future
import scala.util.{Failure, Success}
import scala.concurrent.duration._

/**
 * This has become a big ball of mud that needs splitting up.
 * Has too many responsibilities at the moment.
 */
class CrawlWorker(

  job: CrawlJob,
  config: CrawlConfig,
  frontier: Frontier,
  uriCache: UriCache,
  httpClient: HttpClient,
  robotRulesCache: ActorRef,
  contentParser: ContentParser,
  stopRule: StopRule

  ) extends Actor with Listeners {

  
  import CrawlWorker._

  private [crawler] implicit val execContext = context.system.dispatcher
  private [crawler] val scheduler = context.system.scheduler
  private [crawler] val log = Logging(context.system, getClass)
  private [crawler] val robotRequestTimeout = new Timeout(20.seconds)
  private [crawler] val supportedSchemes = Seq("http", "https")
  private [crawler] val started =  LocalDateTime.now()
  private [crawler] var fcounters = Counters() // fetch attempts
  private [crawler] var rcounters = Counters() // response codes
  private [crawler] var mcounters = MediaCounters() // count media types html, css etc
  private [crawler] var state = CrawlStatus(
    crawlStop = LocalDateTime.now.plusSeconds(config.crawlTimeoutMillis/1000)
  )
  
  override def receive = crawlPending


  def crawlPending: Receive = listenerManagement orElse {
    case Run =>
      val outcome = initCrawler
      outcome
        .pipeTo(sender)
        .map {reply => 
          reply match {
            case StartOkay(_, _) =>
              context.become(crawlRunning)
              gossip(reply)
              self ! NextDequeue
            case StartFailed(_, _) =>
              stopWith(reply)
          }
        }
  }

  def crawlRunning: Receive = listenerManagement orElse {

    case NextDequeue =>
        
      val outcome: CrawlOutcome = stopRule.ask(config, state, fcounters, frontier.size)
      outcome match {
        case KeepCrawling => scheduleNext
        case otherOutcome => stopWith(Stopped(otherOutcome, completeJob(otherOutcome, None)))
      }

    case NextFetch(fe) => if (state.alive) fetchNext(fe)

    case StopCrawl => state = state.stop // Stopping not immediate if in a fetch

  }

  private def stopWith(msg: Any):Unit = {
    state = state.dead
    gossip(msg)
    context.stop(self)
  }

  private def stopWithFailure(t: Throwable):Unit = {
    val outcome = InternalError("Crawler failed to complete: " + t.getLocalizedMessage, t)
    stopWith(
      Stopped(outcome, completeJob(outcome, Some(t)))
    )
  }

  private def initCrawler: Future[Started] = config.validated match {  
    case Failure(t) => Future.successful(StartFailed(t, config))
    case Success(b) =>
      val jobs = config.seeds.map(s => FetchJob(s, 0)).toSet
      enqueueFetchJobs(jobs)
        .map(_ => StartOkay("Started okay", job))
        .recover({ case throwable => StartFailed(throwable, config) })
  }
  

  private def completeJob(outcome: CrawlOutcome, throwOpt: Option[Throwable]):CrawlJob = {
    
    val finished = LocalDateTime.now()
    val message = throwOpt match {
      case Some(t) => outcome.message + ": " + t.getLocalizedMessage
      case None => outcome.message
    }

    job.copy(
      snapshotDate = LocalDateTime.now(),
      finishedDate = Some(finished),
      duration = Duration.between(started, finished).toMillis,
      outcome = Some(outcome.state),
      message = Some(message),
      urisSeen = uriCache.size,
      urisQueued = frontier.size,
      fetchCounters = fcounters.counters,
      responseCounters = rcounters.counters,
      mediaCounters = mcounters.counters
    )
  }

  /**
   * Must batch enqueue FetchJob so that async fetch decisions about
   * all the FetchJob are made BEFORE trying to access Frontier and UriCache
   * to prevent a race condition when accessing the Frontier.
   */
  private def enqueueFetchJobs(fetchJobs: Set[FetchJob]):Future[Unit] = {
    
    val future:Future[Set[(FetchJob, CanFetch)]] = Future.sequence(
      fetchJobs.map({f => isFetchable(f)})
    )

    future.recoverWith({ case t => Future.failed(t) })

    future.map(
      _.map({pair =>
        val f = pair._1
        val d = pair._2
        dgossip(FetchDecision(f.uri, d))
        if (OkayToFetch == d) {
          frontier.enqueue(f) // only modify AFTER async fetch checks
          uriCache.put(f.uri) // mark URI as seen
          dgossip(FetchQueued(f))   
        }
      })
    )
  }

  // Must check robot rules after UriFilter test
  // to avoid unnecessary downloading of robots.txt files for
  // sites that will never be visited anyway and robot fetch fails
  // on unsupported schemes like mailto/ftp.

  private def isFetchable(f: FetchJob):Future[(FetchJob, CanFetch)] = {
    try {
      
      val uri = f.uri
      val scheme = uri.reader.scheme

      if (uriCache.contains(uri)) {
        Future.successful((f, SeenAlready))
        
      } else if (supportedSchemes.find(scheme == _).isEmpty) {
        Future.successful((f, UnsupportedScheme))

      } else if (!config.uriFilter.accept(uri)) {
        Future.successful((f, UriFilterRejected))

      } else {
        robotRulesCache
          .ask(Allow(config.getUserAgent, uri.reader))(robotRequestTimeout)
          .mapTo[Boolean]
          .map(ok => {
            val d = if (ok) OkayToFetch else RobotsExcluded
            (f, d) // resolved as ...
          })
      }

    } catch {
      case t: Throwable => Future.failed(t)
    }
  }

  private def scheduleNext:Unit = {
    frontier.dequeue match {
      case Some(f: FetchJob) =>
        getFetchDelayFor(f.uri) map({delay =>
          gossip(FetchScheduled(f, delay))
          scheduler.scheduleOnce(delay.milliseconds, self, NextFetch(f))
        })
      case None => // empty frontier, code smell to fix
    }
  }

  private def fetchNext(f: FetchJob):Unit = {

    def doNext = self ! NextDequeue
    val stopwatch = new Stopwatch

    val result = (for {
      response <- fetch(f)
      parseResultOpt = parseResponse(response)

    } yield {

      emitFetchResult(f, response, parseResultOpt, stopwatch.duration)
      
      parseResultOpt match {
        case None => doNext
        case Some(parserResult) =>
          if (f.depth >= config.maxDepth) {
            dgossip(DepthLimit(f))
            doNext
          } else {

            var uris = Set.empty[CrawlUri]
            var errors = Set.empty[Option[String]]

            parserResult.links.foreach(l => l.crawlUri match {
              case Some(uri) => uris = uris + uri
              case _ => errors = errors + l.failMessage
            })
            errors.foreach(_ match {
              case Some(msg) => log.error(s"URI parse fail: [$msg]")
              case _ => 
            })

            val jobs = uris.map(FetchJob(_, f.depth + 1))

            // must enqueue BEFORE next fetch
            enqueueFetchJobs(jobs)
              .map({_ => doNext})
              .recover({ case t => stopWithFailure(t) })
          }
      }
    })

    result.recover({
      case t =>
        gossip(FetchError(f.uri, t))
        doNext
    })
    
  }

  private def fetch(f: FetchJob): Future[Response] = {

    val uri = f.uri
    val request = Get(config.getUserAgent, uri)

    def onRequestFail(t: Throwable) = {

      // Handle internal error (as distinct from HTTP request error
      // on target server. Synthesize Response so that crawl can continue.

      log.error(t, "Request failed, reason: " + t.getLocalizedMessage)
      gossip(FetchError(uri, t))

      Future.successful( // technically an async success
        DefaultResponse(
          -1, // internal error code
          Map.empty, 
          Array.empty[Byte], // t.getMessage.getBytes,
          Stats.empty, 
          request
        )
      )
    }

    fcounters = fcounters.increment(FetchAttempts)
    dgossip(FetchGo(f))
    
    httpClient.request(request).map({response =>
        dgossip(FetchResponse(uri, response.statusCode))
        response
    }).recoverWith({
      case t: Throwable => onRequestFail(t)
    })

  }

  private def emitFetchResult(
    fetchJob: FetchJob, 
    response: Response, 
    result: Option[ParserResult],
    duration: Long):Unit = {

    gossip(FetchResult(
      response.statusCode,
      fetchJob,
      job.copy(
        snapshotDate = LocalDateTime.now,
        duration = Duration.between(started, LocalDateTime.now).toMillis,
        urisSeen = uriCache.size,
        urisQueued = frontier.size,
        fetchCounters = fcounters.counters,
        responseCounters = rcounters.counters,
        mediaCounters = mcounters.counters
      ),
      response,
      duration, // Represents combined fetch + parse not including enqueue time
      result
    ))
  }

  private def parseResponse(response: Response):Option[ParserResult] = {

    rcounters = rcounters.increment(""+response.statusCode)
    response.statusCode match {
      case code if (code >= 200 && code <= 299) =>
        fcounters = fcounters.increment(FetchSucceeds)
        mcounters = mcounters.add(
          response.contentType.getOrElse("undefined"), 1, response.contentLength
        )
        if (!contentParser.canParse(response)) {
          None
        } else {
          Some(contentParser.parse(response))
        }
        
      case code if (code >= 300 && code <= 399) =>
        fcounters = fcounters.increment(FetchRedirects)
        None

      case other_codes =>
        fcounters = fcounters.increment(FetchFails)
        None
    }
  }

  /**
   * Compute the politeness delay before the next fetch.
   * Two possible delay values need considering:
   *
   *     1. A crawl-delay directive in robots.txt file, if it exists
   *     2. The default delay in the CrawlConfig
   *
   * If both values are available then the longest is chosen.
   */
  private def getFetchDelayFor(uri: CrawlUri):Future[Long] = {
    val defDelay = config.crawlDelayMillis
    robotRulesCache
      .ask(DelayFor(config.getUserAgent, uri.reader))(robotRequestTimeout)
      .mapTo[Option[Int]]
      .map(_ match {
          case Some(rulesDelay) => Math.max(rulesDelay, defDelay)
          case None => defDelay
      })
  }

  /**
   * Uncomment to enable additional gossip for debugging,
   * but realise that this will generate a considerably larger 
   * number of Actor messages as a consequence.
   */
  private def dgossip(msg: Any) = {} //gossip(msg)

}

object CrawlWorker {
  
  // Public messages
  case object Run
  case object StopCrawl

  sealed abstract class Started
  case class StartOkay(msg: String, job: CrawlJob) extends Started()
  case class StartFailed(t: Throwable, config: CrawlConfig) extends Started()
  case class Stopped(outcome: CrawlOutcome, job: CrawlJob)
  case object EmptyFrontier

  // Internal messages
  private [crawler] case object NextDequeue
  private [crawler] case class  NextFetch(f: FetchJob)

  val FetchAttempts = "FetchAttempts"
  val FetchSucceeds = "FetchSucceeds"
  val FetchFails = "FetchFails"
  val FetchRedirects = "Redirects"

}