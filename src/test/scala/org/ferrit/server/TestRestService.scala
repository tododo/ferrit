package org.ferrit.server

import java.time.{LocalDate, LocalDateTime, ZoneId}

import akka.actor.{Actor, ActorContext, ActorRef, ActorSystem, Props}
import akka.event.LoggingAdapter
import akka.http.scaladsl.model.{HttpEntity, MediaTypes, StatusCodes}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.testkit.TestActorRef
import org.ferrit.core.crawler.{CrawlConfig, CrawlLog}
import org.ferrit.core.crawler.CrawlerManager._
import org.ferrit.core.filter.PriorityRejectUriFilter
import org.ferrit.core.filter.PriorityRejectUriFilter.Accept
import org.ferrit.core.json.PlayJsonImplicits._
import org.ferrit.core.model.{CrawlJob, Crawler, DocumentMetaData, FetchLogEntry}
import org.ferrit.core.uri.CrawlUri
import org.ferrit.core.util.Media
import org.ferrit.dao._
import org.ferrit.server.json.PlayJsonImplicits._
import org.ferrit.server.json.{Id, Message}
import org.scalamock.scalatest.MockFactory
import org.scalatest.{FlatSpec, Matchers}
import play.api.libs.json._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}


class TestRestService extends FlatSpec with MockFactory with Matchers with ScalatestRouteTest {

  // See: Spray's RequestBuildingExamplesSpec for more ...

  val crawlerManager: ActorRef = TestActorRef[Placeholder]

  class TestFerritRoutes(override val crawlerManager: ActorRef)(override implicit val system: ActorSystem,
                                                                override implicit val executor: ExecutionContext,
  ) extends RestServiceRoutes {
    //    override def actorRefFactory = system

    override val fleDao: FetchLogEntryDAO = stub[FetchLogEntryDAO]
    override val crawlJobDao: CrawlJobDAO = stub[CrawlJobDAO]
    override val crawlerDao: CrawlerDAO = stub[CrawlerDAO]
    override val docMetaDao: DocumentMetaDataDAO = stub[DocumentMetaDataDAO]

    override def createJournal: ActorRef = TestActorRef[Placeholder]

    override val ferrit: ActorRef = TestActorRef[Ferrit]
    override val logger: ActorRef = TestActorRef[CrawlLog]


  }


  trait TestContext {

    val testRestService: RestServiceRoutes = new TestFerritRoutes(crawlerManager = crawlerManager)
    val crawlerId = "good-crawler-id"
    val badCrawlerId = "bad-crawler-id"
    val jobId = "good-job-id"
    val badJobId = "bad-job-id"

    private val uris = Seq(
      "http://www.website.com",
      "http://www.website.com/products",
      "http://www.website.com/contact"
    )

    val config = makeConfig(crawlerId, "http://www.website.com")
    val configs = Seq(config)
    val crawlers = configs.map(c => Crawler(c.id, c))
    val crawler = crawlers(0)

    val crawlJobs = Seq(
      makeJob(crawlerId, "1234", LocalDateTime.now),
      makeJob(crawlerId, "5678", LocalDateTime.now)
    )
    val crawlJob = crawlJobs(0)

    val docs = uris.map(uri => makeDoc(crawlerId, jobId, uri))
    val doc = docs(0)
    val fetches = uris.map(uri => makeFetch(crawlerId, jobId, uri))
    val fetch = fetches(0)

    val configJson = Json.prettyPrint(Json.toJson(config))
    val configsJson = Json.prettyPrint(Json.toJson(configs))
    val crawlJobJson = Json.prettyPrint(Json.toJson(crawlJob))
    val crawlJobsJson = Json.prettyPrint(Json.toJson(crawlJobs))
    val docJson = Json.prettyPrint(Json.toJson(doc))
    val docsJson = Json.prettyPrint(Json.toJson(docs))
    val fetchJson = Json.prettyPrint(Json.toJson(fetch))
    val fetchesJson = Json.prettyPrint(Json.toJson(fetches))
    val crawlerIdJson = Json.prettyPrint(Json.toJson(Id(crawlerId)))
    val badCrawlerIdJson = Json.prettyPrint(Json.toJson(Id(badCrawlerId)))
    implicit val rejectionHandler = CustomRejectionHandler.customRejectionHandler
    implicit val logAdapter = system.log

    implicit def customExceptionHandler(implicit logAdapter: LoggingAdapter) = CustomExceptionHandler.handler(logAdapter)
  }

  def prettyPrint(s: String) = Json.prettyPrint(Json.parse(s))

  val badCrawlerJson = prettyPrint(
    """{"statusCode":404,"message":"No crawler found with identifier [bad-crawler-id]"}""")
  val badJobJson = prettyPrint("""{"statusCode":404,"message":"No crawl job found with identifier [bad-job-id]"}""")
  val badUnmarshallMsg = prettyPrint("""{"statusCode":400,"message":"The request entity could not be unmarshalled."}""")
  val failedTestMsg = "[http://othersite.com] is rejected because no accept pattern accepted it"


  it should "return 200 for GET /crawlers with crawl configs array" in new TestContext {

    (testRestService.crawlerDao.findAll _).when().returns(crawlers)
    Get("/crawlers") ~> testRestService.routes ~> check {
      responseAs[String] should equal(configsJson)
    }
  }

  it should "return 200 for GET /crawlers/{crawlerId} with single config" in new TestContext {

    (testRestService.crawlerDao.find(_: String)).when(crawlerId).returns(Some(crawler))
    Get(s"/crawlers/$crawlerId") ~> testRestService.routes ~> check {
      responseAs[String] should equal(configJson)
    }
  }

  it should "return 404 for GET /crawlers/{crawlerId} for non-existent crawler" in new TestContext {

    (testRestService.crawlerDao.find(_: String)).when(badCrawlerId).returns(None)


    Get(s"/crawlers/$badCrawlerId") ~> Route.seal(testRestService.routes) ~> check {
      status should equal(StatusCodes.NotFound)
      val response = responseAs[String]
      response should equal(badCrawlerJson)
    }
  }

  it should "return 200 for GET /crawlers/{crawlerId}/jobs with crawler jobs" in new TestContext {

    (testRestService.crawlerDao.find(_: String)).when(crawlerId).returns(Some(crawler))
    (testRestService.crawlJobDao.find(_: String)).when(crawlerId).returns(crawlJobs)
    Get(s"/crawlers/$crawlerId/jobs") ~> testRestService.routes ~> check {
      responseAs[String] should equal(crawlJobsJson)
    }
  }

  it should "return 404 for GET /crawlers/{crawlerId}/jobs for non-existent crawler" in new TestContext {

    (testRestService.crawlerDao.find(_: String)).when(badCrawlerId).returns(None)
    Get(s"/crawlers/$badCrawlerId/jobs") ~> Route.seal(testRestService.routes) ~> check {
      status should equal(StatusCodes.NotFound)
      responseAs[String] should equal(badCrawlerJson)
    }
  }

  it should "return 200 for GET /crawlers/{crawlerId}/jobs/{jobId} with job details" in new TestContext {

    (testRestService.crawlerDao.find(_: String)).when(crawlerId).returns(Some(crawler))
    (testRestService.crawlJobDao.find(_: String, _: String)).when(crawlerId, jobId).returns(Some(crawlJob))
    Get(s"/crawlers/$crawlerId/jobs/$jobId") ~> testRestService.routes ~> check {
      responseAs[String] should equal(crawlJobJson)
    }
  }

  it should "return 404 for GET /crawlers/{crawlerId}/jobs/{jobId} for non-existent crawler" in new TestContext {

    (testRestService.crawlerDao.find(_: String)).when(badCrawlerId).returns(None)
    Get(s"/crawlers/$badCrawlerId/jobs/$jobId") ~> Route.seal(testRestService.routes) ~> check {
      status should equal(StatusCodes.NotFound)
      responseAs[String] should equal(badCrawlerJson)
    }
  }

  it should "return 404 for GET /crawlers/{crawlerId}/jobs/{jobId} for non-existent job" in new TestContext {

    (testRestService.crawlerDao.find(_: String)).when(crawlerId).returns(Some(crawler))
    (testRestService.crawlJobDao.find(_: String, _: String)).when(crawlerId, badJobId).returns(None)
    Get(s"/crawlers/$crawlerId/jobs/$badJobId") ~> Route.seal(testRestService.routes) ~> check {
      status should equal(StatusCodes.NotFound)
      responseAs[String] should equal(badJobJson)
    }
  }

  it should "return 200 for GET /crawlers/{crawlerId}/jobs/{jobId}/assets with asset details" in new TestContext {

    (testRestService.crawlerDao.find(_: String)).when(crawlerId).returns(Some(crawler))
    (testRestService.crawlJobDao.find(_: String, _: String)).when(crawlerId, jobId).returns(Some(crawlJob))
    (testRestService.docMetaDao.find(_: String)).when(jobId).returns(docs)
    Get(s"/crawlers/$crawlerId/jobs/$jobId/assets") ~> testRestService.routes ~> check {
      responseAs[String] should equal(docsJson)
    }
  }

  it should "return 404 for GET /crawlers/{crawlerId}/jobs/{jobId}/assets for non-existent crawler" in new TestContext {

    (testRestService.crawlerDao.find(_: String)).when(badCrawlerId).returns(None)
    Get(s"/crawlers/$badCrawlerId/jobs/$jobId/assets") ~> Route.seal(testRestService.routes) ~> check {
      status should equal(StatusCodes.NotFound)
      responseAs[String] should equal(badCrawlerJson)
    }
  }

  it should "return 404 for GET /crawlers/{crawlerId}/jobs/{jobId}/assets for non-existent job" in new TestContext {

    (testRestService.crawlerDao.find(_: String)).when(crawlerId).returns(Some(crawler))
    (testRestService.crawlJobDao.find(_: String, _: String)).when(crawlerId, badJobId).returns(None)
    Get(s"/crawlers/$crawlerId/jobs/$badJobId/assets") ~> Route.seal(testRestService.routes) ~> check {
      status should equal(StatusCodes.NotFound)
      responseAs[String] should equal(badJobJson)
    }
  }

  it should "return 200 for GET /crawlers/{crawlerId}/jobs/{jobId}/fetches with fetch list" in new TestContext {

    (testRestService.crawlerDao.find(_: String)).when(crawlerId).returns(Some(crawler))
    (testRestService.crawlJobDao.find(_: String, _: String)).when(crawlerId, jobId).returns(Some(crawlJob))
    (testRestService.fleDao.find(_: String)).when(jobId).returns(fetches)
    Get(s"/crawlers/$crawlerId/jobs/$jobId/fetches") ~> testRestService.routes ~> check {
      responseAs[String] should equal(fetchesJson)
    }
  }

  it should "return 404 for GET /crawlers/{crawlerId}/jobs/{jobId}/fetches for non-existent crawler" in new TestContext {

    (testRestService.crawlerDao.find(_: String)).when(badCrawlerId).returns(None)
    Get(s"/crawlers/$badCrawlerId/jobs/$jobId/fetches") ~> Route.seal(testRestService.routes) ~> check {
      status should equal(StatusCodes.NotFound)
      responseAs[String] should equal(badCrawlerJson)
    }
  }

  it should "return 404 for GET /crawlers/{crawlerId}/jobs/{jobId}/fetches for non-existent job" in new TestContext {

    (testRestService.crawlerDao.find(_: String)).when(crawlerId).returns(Some(crawler))
    (testRestService.crawlJobDao.find(_: String, _: String)).when(crawlerId, badJobId).returns(None)
    Get(s"/crawlers/$crawlerId/jobs/$badJobId/fetches") ~> Route.seal(testRestService.routes) ~> check {
      status should equal(StatusCodes.NotFound)
      responseAs[String] should equal(badJobJson)
    }
  }

  it should "return 201 for POST /crawlers with valid crawl config" in new TestContext {

    (testRestService.crawlerDao.insert(_: Crawler)).when(*)
    Post(s"/crawlers", HttpEntity(MediaTypes.`application/json`, configJson)) ~> testRestService.routes ~> check {
      status should equal(StatusCodes.Created)
    }

  }

  it should "return 400 for POST /crawlers when crawl config malformed" in new TestContext {

    val badConfigWithNoName = """{"id":"new", "name":""}"""
    (testRestService.crawlerDao.insert(_: Crawler)).when(*)
    Post(s"/crawlers", HttpEntity(MediaTypes.`application/json`, badConfigWithNoName)) ~> Route.seal(testRestService.routes) ~> check {
      status should equal(StatusCodes.BadRequest)
      responseAs[String] should equal(badUnmarshallMsg)
    }

  }

  it should "return 400 for POST /crawlers when tests in the crawl config fail" in new TestContext {

    val configFailed = config.copy(tests = Some(Seq("should accept: http://othersite.com")))
    val configFailedJson = Json.stringify(Json.toJson(configFailed))
    Post(s"/crawlers", HttpEntity(MediaTypes.`application/json`, configFailedJson)) ~> Route.seal(testRestService.routes) ~> check {
      status should equal(StatusCodes.BadRequest)
      responseAs[String] should include(failedTestMsg)
    }

  }

  it should "return 200 for POST /crawl-config-test" in new TestContext {
    Post(s"/crawl-config-test", HttpEntity(MediaTypes.`application/json`, configJson)) ~> testRestService.routes ~>
      check {
        status should equal(StatusCodes.OK)
      }
  }

  it should "return 400 for POST /crawl-config-test when tests in the crawl config fail" in new TestContext {

    val configFailed = config.copy(tests = Some(Seq("should accept: http://othersite.com")))
    val configFailedJson = Json.stringify(Json.toJson(configFailed))
    Post(s"/crawl-config-test", HttpEntity(MediaTypes.`application/json`, configFailedJson)) ~> Route.seal(testRestService.routes) ~>
      check {
        status should equal(StatusCodes.BadRequest)
        responseAs[String] should include(failedTestMsg)
      }

  }

  it should "return 201 for PUT /crawlers/{crawlerId} with valid crawl config" in new TestContext {

    applyStubForPutHack(testRestService.crawlerDao, crawlerId, crawler)
    (testRestService.crawlerDao.insert(_: Crawler)).when(*)
    Put(s"/crawlers/$crawlerId", HttpEntity(MediaTypes.`application/json`, configJson)) ~> testRestService.routes ~>
      check {
        status should equal(StatusCodes.Created)
      }

  }

  it should "return 404 for PUT /crawlers/{crawlerId} for non-existent crawler" in new TestContext {

    (testRestService.crawlerDao.find(_: String)).when(badCrawlerId).returns(None)
    Put(s"/crawlers/$badCrawlerId", HttpEntity(MediaTypes.`application/json`, configJson)) ~> Route.seal(testRestService.routes) ~>
      check {
        status should equal(StatusCodes.NotFound)
        responseAs[String] should equal(badCrawlerJson)
      }
  }

  it should "return 400 for PUT /crawlers/{crawlerId} when tests in the crawl config fail" in new TestContext {

    val configFailed = config.copy(tests = Some(Seq("should accept: http://othersite.com")))
    val configFailedJson = Json.stringify(Json.toJson(configFailed))
    applyStubForPutHack(testRestService.crawlerDao, crawlerId, crawler)

    Put(s"/crawlers/$crawlerId", HttpEntity(MediaTypes.`application/json`, configFailedJson)) ~> Route.seal(testRestService.routes) ~>
      check {
        status should equal(StatusCodes.BadRequest)
        responseAs[String] should include(failedTestMsg)
      }

  }

  it should "return 204 for DELETE /crawlers/{crawlerId}" in new TestContext {

    (testRestService.crawlerDao.find(_: String)).when(crawlerId).returns(Some(crawler))
    (testRestService.crawlerDao.delete(_: String)).when(crawlerId)
    Delete(s"/crawlers/$crawlerId") ~> testRestService.routes ~> check {
      status should equal(StatusCodes.NoContent)
    }
  }

  it should "return 404 for DELETE /crawlers/{crawlerId} for non-existent crawler" in new TestContext {
    (testRestService.crawlerDao.find(_: String)).when(badCrawlerId).returns(None)
    Delete(s"/crawlers/$badCrawlerId") ~> Route.seal(testRestService.routes) ~> check {
      status should equal(StatusCodes.NotFound)
      responseAs[String] should equal(badCrawlerJson)
    }
  }

  it should "return 200 for GET /jobs" in new TestContext {
    (testRestService.crawlJobDao.find(_: LocalDate)).when(*).returns(crawlJobs)
    Get("/jobs") ~> testRestService.routes ~> check {
      status should equal(StatusCodes.OK)
      responseAs[String] should equal(crawlJobsJson)
    }
  }

  it should "return 200 for GET /jobs and default to today's jobs" in new TestContext {
    val today = LocalDate.now
    (testRestService.crawlJobDao.find(_: LocalDate)).when(today).returns(crawlJobs)
    Get("/jobs") ~> testRestService.routes ~> check {
      status should equal(StatusCodes.OK)
      responseAs[String] should equal(crawlJobsJson)
    }
  }

  it should "return 400 for GET /jobs when date param is invalid" in new TestContext {
    Get("/jobs?date=BAD-DATE") ~> Route.seal(testRestService.routes) ~> check {
      status should equal(StatusCodes.BadRequest)
      responseAs[String] should equal(
        prettyPrint("""{"statusCode":400,"message":"Parameter [date] has invalid value [BAD-DATE]"}""")
      )
    }
  }

  it should "return 201 for POST /job-processes with valid crawler ID" in new TestContext {

    val crawlerManager: ActorRef = system.actorOf(Props(new Actor {
      def receive: Receive = {
        case StartJob(config, listeners) => sender ! crawlJob
      }
    }))
    override val testRestService = new TestFerritRoutes(crawlerManager)
    (testRestService.crawlerDao.find(_: String)).when(crawlerId).returns(Some(crawler))

    Post("/job-processes", HttpEntity(MediaTypes.`application/json`, crawlerIdJson)) ~> testRestService.routes ~>
      check {
        status should equal(StatusCodes.Created)
        responseAs[String] should equal(crawlJobJson)
      }
  }

  it should "return 404 for POST /job-processes for non-existent crawler" in new TestContext {
    (testRestService.crawlerDao.find(_: String)).when(*).returns(None)
    Post("/job-processes", HttpEntity(MediaTypes.`application/json`, badCrawlerIdJson)) ~> Route.seal(testRestService
      .routes) ~> check {
      status should equal(StatusCodes.NotFound)
      responseAs[String] should equal(badCrawlerJson)
    }
  }

  it should "return 200 for GET /job-processes" in new TestContext {
    val crawlerManager: ActorRef = system.actorOf(Props(new Actor {
      def receive: Receive = {
        case JobsQuery() => sender ! JobsInfo(crawlJobs)
      }
    }))
    override val testRestService = new TestFerritRoutes(crawlerManager)
    Get("/job-processes") ~> testRestService.routes ~> check {
      status should equal(StatusCodes.OK)
      responseAs[String] should equal(crawlJobsJson)
    }
  }

  def stubCrawlerManagerForStopJob(jobId: String) = system.actorOf(Props(new Actor {
    def receive: Receive = {
      case StopJob(id) =>
        sender ! (if (jobId == id) StopAccepted(Seq(jobId)) else JobNotFound)
      case StopAllJobs() =>
        sender ! StopAccepted(Seq(jobId))
    }
  }))

  it should "return 202 for DELETE /job-processes/{jobId}" in new TestContext {

    val crawlerManager = stubCrawlerManagerForStopJob(jobId)
    override val testRestService = new TestFerritRoutes(crawlerManager)
    Delete(s"/job-processes/$jobId") ~> testRestService.routes ~> check {
      status should equal(StatusCodes.Accepted)
      responseAs[String] should equal(
        prettyPrint(Json.stringify(Json.toJson(Message(RestServiceRoutes.StopJobAcceptedMsg.format(jobId)))))
      )
    }
  }

  it should "return 404 for DELETE /job-processes/{jobId} when job does not exist" in new TestContext {

    val crawlerManager = stubCrawlerManagerForStopJob(jobId)
    override val testRestService = new TestFerritRoutes(crawlerManager)
    Delete(s"/job-processes/$badJobId") ~> Route.seal(testRestService.routes) ~> check {
      status should equal(StatusCodes.NotFound)
      responseAs[String] should equal(badJobJson)
    }
  }

  it should "return 202 for DELETE /job-processes" in new TestContext {

    val crawlerManager = stubCrawlerManagerForStopJob(jobId)
    override val testRestService = new TestFerritRoutes(crawlerManager)
    Delete("/job-processes") ~> testRestService.routes ~> check {
      status should equal(StatusCodes.Accepted)
      responseAs[String] should equal(
        prettyPrint(Json.stringify(Json.toJson(Message(RestServiceRoutes.StopAllJobsAcceptedMsg.format(1)))))
      )
    }
  }

  it should "parse the date param into a date key" in {
    RestServiceRoutes.makeDateKey("2014-06-28") match {
      case Success(dateKey) => dateKey.toString() should equal("2014-06-28")
      case Failure(t) => fail("Bad date parse")
    }
  }

  // Todo: remove this. Stubbing is needed even though not used in the PUT route.
  // Spray route runner seems to be travelling down the "get" branch just
  // before travelling the "put" branch.

  private def applyStubForPutHack(crawlerDao: CrawlerDAO, id: String, crawler: Crawler) = {
    (crawlerDao.find(_: String)).when(id).returns(Some(crawler))
  }


  /* = = = = = = = = = =  Utility  = = = = = = = = = = */

  private def makeConfig(id: String, uri: String) = CrawlConfig(
    id = id,
    userAgent = Some("Test Agent"),
    crawlerName = "Test Crawler",
    seeds = Seq(CrawlUri(uri)),
    uriFilter = new PriorityRejectUriFilter(Seq(Accept(uri.r))),
    tests = Some(Seq(s"should accept: $uri")),
    crawlDelayMillis = 0,
    crawlTimeoutMillis = 10000,
    maxDepth = 10,
    maxFetches = 10000,
    maxQueueSize = 10000,
    maxRequestFails = 0.5
  )

  private def makeJob(crawlerId: String, jobId: String, jobDate: LocalDateTime) = CrawlJob(
    crawlerId = crawlerId,
    crawlerName = "Test Crawler",
    jobId = jobId,
    node = "localhost",
    partitionDate = jobDate.toLocalDate,
    snapshotDate = jobDate,
    createdDate = jobDate,
    finishedDate = Some(jobDate),
    duration = 1000,
    outcome = Some("Okay"),
    message = Some("Completed Okay"),
    urisSeen = 1000,
    urisQueued = 0,
    fetchCounters = Map.empty[String, Int],
    responseCounters = Map.empty[String, Int],
    mediaCounters = Map.empty[String, Media]
  )

  private def makeDoc(crawlerId: String, jobId: String, uri: String) = DocumentMetaData(
    crawlerId = crawlerId,
    jobId = jobId,
    uri = uri,
    contentType = "text/html;charset=UTF=8",
    contentLength = 30000,
    depth = 0,
    fetched = LocalDateTime.now(ZoneId.systemDefault()),
    responseStatus = "200"
  )

  private def makeFetch(crawlerId: String, jobId: String, uri: String) = FetchLogEntry(
    crawlerId = crawlerId,
    jobId = jobId,
    logTime = LocalDateTime.now(ZoneId.systemDefault()),
    uri = uri,
    uriDepth = 0,
    statusCode = 200,
    contentType = Some("text/html;charset=UTF-8"),
    contentLength = 30000,
    linksExtracted = 200,
    fetchDuration = 1005,
    requestDuration = 1000,
    parseDuration = 5,
    urisSeen = 1,
    urisQueued = 0,
    fetches = 1
  )

}

class Placeholder extends Actor {
  override def receive = {
    case _ =>
  }
}