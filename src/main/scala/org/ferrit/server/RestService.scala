package org.ferrit.server

import java.time.LocalDate
import java.time.format.DateTimeParseException

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.event.LoggingAdapter
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Directive1, Route}
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.util.Timeout
import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport
import org.ferrit.core.crawler.{CrawlConfig, CrawlConfigTester}
import org.ferrit.core.crawler.CrawlerManager._
import org.ferrit.core.json.PlayJsonImplicits._
import org.ferrit.core.model.{CrawlJob, Crawler}
import org.ferrit.dao._
import org.ferrit.server.RestServiceRoutes._
import org.ferrit.server.json.PlayJsonImplicits._
import org.ferrit.server.json.{Id, Message}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

/**
  * Makes the crawler service available as a REST API.
  */
class RestService(
                   override val ferrit: ActorRef,
                   val daoFactory: DAOFactory,
                   override val crawlerManager: ActorRef,
                   override val logger: ActorRef

                 ) extends RestServiceRoutes with Actor with ActorLogging {

  implicit val customRejectionHandler = CustomRejectionHandler.customRejectionHandler

  implicit def customExceptionHandler(implicit logAdapter: LoggingAdapter) = CustomExceptionHandler.handler(logAdapter)

  implicit val logAdapter = log


  val actorContext = context
  override implicit val executor: ExecutionContext = actorContext.dispatcher
  override implicit val system: ActorSystem = actorContext.system
  implicit val materializer = ActorMaterializer()


  def receive: Receive = start() // runRoute wrapper
  def createJournal = context.actorOf(Props(classOf[Journal], daoFactory))

  override val fleDao: FetchLogEntryDAO = daoFactory.fetchLogEntryDao
  override val crawlJobDao: CrawlJobDAO = daoFactory.crawlJobDao
  override val crawlerDao: CrawlerDAO = daoFactory.crawlerDao
  override val docMetaDao: DocumentMetaDataDAO = daoFactory.documentMetaDataDao


  def start(): Receive = {
    case StartService(port, host) => startService(host, port)

  }

  def startService(host: String, port: Int): Unit = {


    val bindingFuture = Http().bindAndHandle(routes, host, port)

    context.become(waitForShutdown(bindingFuture))
  }

  def waitForShutdown(bindingFuture: Future[ServerBinding]): Receive = {
    case StopService => stopService(bindingFuture)
  }

  private def stopService(bindingFuture: Future[ServerBinding]): Unit = {
    implicit val executionContext = context.dispatcher
    Await.ready(bindingFuture.flatMap(_.unbind()), Duration.Inf)
  }


}

trait RestServiceRoutes extends PlayJsonSupport {

  import RestServiceRoutes._

  implicit val executor: ExecutionContext

  implicit val system: ActorSystem

  val ferrit: ActorRef
  val crawlerManager: ActorRef
  val logger: ActorRef

  def createJournal: ActorRef

  val fleDao: FetchLogEntryDAO
  val crawlJobDao: CrawlJobDAO
  val crawlerDao: CrawlerDAO
  val docMetaDao: DocumentMetaDataDAO

  // General ask timeout
  val askTimeout = new Timeout(3.seconds)

  // Provide generous Timeout when starting a job. Seeds need enqueing
  // which in turn requires fetching robots.txt to be sure they are valid.
  val startJobTimeout = new Timeout(30.seconds)

  val shutdownDelay = 1.second
  val webDirectory = "web"

  def routes: Route = {

    pathSingleSlash {
      getFromResource(s"$webDirectory/index.html")
    } ~
      path("crawlers" / Segment / "jobs" / Segment / "fetches") { (crawlerId, jobId) =>
        get {
          withCrawler(crawlerId) { crawler =>
            withCrawlJob(crawlerId, jobId) { job =>
              complete {
                fleDao.find(jobId)
              }
            }
          }
        }
      } ~
      path("crawlers" / Segment / "jobs" / Segment / "assets") { (crawlerId, jobId) =>
        get {
          withCrawler(crawlerId) { crawler =>
            withCrawlJob(crawlerId, jobId) { job =>
              complete {
                docMetaDao.find(jobId)
              }
            }
          }
        }
      } ~
      path("crawlers" / Segment / "jobs" / Segment) { (crawlerId, jobId) =>
        get {
          withCrawler(crawlerId) { crawler =>
            withCrawlJob(crawlerId, jobId) { job =>
              complete(job)
            }
          }
        }
      } ~
      path("crawlers" / Segment / "jobs") { crawlerId =>
        get {
          withCrawler(crawlerId) { crawler =>
            complete(crawlJobDao.find(crawlerId))
          }
        }
      } ~
      path("crawlers" / Segment) { crawlerId =>
        get {
          withCrawler(crawlerId) { crawler =>
            complete(crawler.config)
          }
        } ~
          put {
            entity(as[CrawlConfig]) { config =>
              withCrawler(crawlerId) { crawler =>
                complete {
                  val results: CrawlConfigTester.Results = CrawlConfigTester.testConfig(config)
                  if (results.allPassed) {
                    val config2 = config.copy(id = crawlerId)
                    val crawler = Crawler(crawlerId, config2)
                    crawlerDao.insert(crawler)
                    StatusCodes.Created -> config2
                  } else {
                    StatusCodes.BadRequest -> results
                  }
                }
              }
            }
          } ~
          delete {
            withCrawler(crawlerId) { crawler =>
              complete {
                crawlerDao.delete(crawlerId)
                StatusCodes.NoContent
              }
            }
          }
      } ~
      path("crawlers") {
        get {
          complete {
            crawlerDao.findAll().map(crawler => crawler.config)
          }
        } ~
          post {
            entity(as[CrawlConfig]) { config: CrawlConfig =>
              complete {
                val results: CrawlConfigTester.Results = CrawlConfigTester.testConfig(config)
                if (results.allPassed) {
                  val crawler = Crawler.create(config)
                  crawlerDao.insert(crawler)
                  StatusCodes.Created -> crawler.config
                } else {
                  StatusCodes.BadRequest -> results
                }
              }
            }
          }
      } ~
      path("crawl-config-test") {
        post {
          entity(as[CrawlConfig]) { config =>
            complete {
              val results: CrawlConfigTester.Results = CrawlConfigTester.testConfig(config)
              val sc = if (results.allPassed) StatusCodes.OK else StatusCodes.BadRequest
              sc -> results
            }
          }
        }
      } ~
      path("jobs") {
        get {
          parameter("date" ? DateParamDefault) { dateParam =>
            makeDateKey(dateParam) match {
              case Success(dateKey) => complete(crawlJobDao.find(dateKey))
              case Failure(t) => reject(BadParamRejection("date", dateParam))
            }
          }
        }
      } ~
      path("job-processes") {
        post {
          entity(as[Id]) { crawlerId =>
            withCrawler(crawlerId.id) { crawler =>
              complete {

                crawlerManager
                  .ask(StartJob(crawler.config, Seq(logger, createJournal)))(startJobTimeout)
                  .mapTo[CrawlJob]
                  .map({ job => StatusCodes.Created -> job })
              }
            }
          }
        } ~
          get {
            complete {

              crawlerManager
                .ask(JobsQuery())(askTimeout)
                .mapTo[JobsInfo]
                .map({ jobsInfo => jobsInfo.jobs })
            }
          } ~
          delete {
            complete {

              crawlerManager
                .ask(StopAllJobs())(askTimeout)
                .mapTo[StopAccepted]
                .map(sa => StatusCodes.Accepted -> Message(StopAllJobsAcceptedMsg.format(sa.ids.size)))
            }
          }

      } ~
      path("job-processes" / Segment) { jobId =>
        delete {
          onSuccess((crawlerManager ? StopJob(jobId)) (askTimeout)) {
            case StopAccepted(Seq(id)) => complete(StatusCodes.Accepted -> Message(StopJobAcceptedMsg.format(id)))
            case JobNotFound => reject(BadEntityRejection("crawl job", jobId))
          }
        }
      } ~
      path("shutdown") {
        post {
          complete {
            system.scheduler.scheduleOnce(shutdownDelay) {
              ferrit ! Ferrit.Shutdown
            }
            Message(ShutdownReceivedMsg)
          }
        }
      }
  }

  def withCrawler(crawlerId: String): Directive1[Crawler] = {
    crawlerDao.find(crawlerId) match {
      case None => reject(BadEntityRejection("crawler", crawlerId))
      case Some(crawler) => provide(crawler)
    }
  }

  def withCrawlJob(crawlerId: String, crawlJobId: String): Directive1[CrawlJob] = {
    crawlJobDao.find(crawlerId, crawlJobId) match {
      case None => reject(BadEntityRejection("crawl job", crawlJobId))
      case Some(crawlJob) => provide(crawlJob)
    }
  }
}

object RestServiceRoutes {

  val DateParamDefault = "no-date"
  val DateParamFormat = "YYYY-MM-dd"
  val NoPostToNamedCrawlerMsg = "Cannot post to an existing crawler resource"
  val StopJobAcceptedMsg = "Stop request accepted for job [%s]"
  val StopAllJobsAcceptedMsg = "Stop request accepted for %s jobs"
  val ShutdownReceivedMsg = "Shutdown request received"

  case class StartService(port: Int, host: String)

  case object StopService

  def makeDateKey(dateParam: String): Try[LocalDate] =
    try {
      val dateKey = (if (DateParamDefault == dateParam) {
        LocalDate.now()
      } else {
        import java.time.format.DateTimeFormatter
//        val formatter = DateTimeFormatter.ofPattern(DateTimeFormatter.ISO_LOCAL_DATE)
//        formatter.withZone(ZoneId.systemDefault())
        LocalDate.parse(dateParam, DateTimeFormatter.ISO_LOCAL_DATE)
      })
      Success(dateKey)
    } catch {
      case e: IllegalArgumentException => Failure(e)
      case e: DateTimeParseException => Failure(e)
    }

}