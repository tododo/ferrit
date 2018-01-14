package org.ferrit.server

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.event.Logging
import org.ferrit.core.crawler.{CrawlLog, CrawlerManager}
import org.ferrit.core.http.{HttpClient, HttpClientConfig, NingAsyncHttpClient}
import org.ferrit.core.robot.{DefaultRobotRulesCache, RobotRulesCacheActor}
import org.ferrit.dao.DAOFactory
import org.ferrit.dao.cassandra.{CassandraConfig, CassandraDAOFactory, CassandraPersistenceManager}
import org.ferrit.server.RestServiceRoutes._

import scala.concurrent.duration._

/**
  * The service bootstrap class.
  */
object Ferrit extends App {

  case object Start

  case object Shutdown

  val system = ActorSystem(classOf[Ferrit].getSimpleName)
  system.actorOf(Props[Ferrit]) ! Ferrit.Start

}

class Ferrit extends Actor {

  import Ferrit._

  implicit val actorSystem = context.system
  implicit val execContext = context.dispatcher

  val log = Logging(context.system, getClass)
  val config = context.system.settings.config

  lazy val persistenceContext = {
    val cc = CassandraConfig(
      config.getString("persistence.cassandra.keyspace"),
      Seq(config.getString("persistence.cassandra.node")),
      config.getInt("persistence.cassandra.port")
    )
    new CassandraPersistenceManager(cc)
  }

  def receive = {
    case Start => init()
  }

  def awaitShutdown(httpClient: HttpClient, crawlerManager: ActorRef, restService: ActorRef): Receive = {
    case Shutdown => shutdown(httpClient, crawlerManager, restService)
  }

  def init(): Unit = {

    welcomeBanner.foreach(log.info)

    val maxCrawlers = config.getInt("app.crawler.max-crawlers")
    val userAgent = config.getString("app.crawler.user-agent")
    val host = config.getString("app.server.host")
    val port = config.getString("app.server.port").toInt

    val httpConfig = HttpClientConfig()
    val httpClient = new NingAsyncHttpClient(httpConfig)

    val daoFactory: DAOFactory = new CassandraDAOFactory(
      persistenceContext.getColumnTTL(config),
      persistenceContext.session
    )

    val robotRulesCache = context.system.actorOf(Props(
      classOf[RobotRulesCacheActor],
      new DefaultRobotRulesCache(httpClient)
    ), "robot-rules-cache")

    val crawlerManager = context.system.actorOf(Props(classOf[CrawlerManager],
      host,
      userAgent,
      maxCrawlers,
      httpClient,
      robotRulesCache),
      "crawler-manager")

    val logger = context.actorOf(Props[CrawlLog])

    val restService = context.actorOf(Props(
      classOf[RestService],
      self,
      daoFactory,
      crawlerManager,
      logger),
      "rest-service")

    val home = s"http://$host:$port"

    restService ! StartService(port, host)

//    IO(Http) ! Http.Bind(restService, host, port = port)

    log.info(s"Server started on $home")

    context.become(awaitShutdown(httpClient, crawlerManager, restService))
  }

  def welcomeBanner: List[String] =
    List(
      """                                 _ _                """,
      """              ____ __  _ _  _ _ (_| )_              """,
      """-------------| __// _)| '_)| '_)| | |_--------------""",
      """-------------| _| \__)|_|--|_|--|_|\__)-------------""",
      """=============|_|====================================""",
      """                                                    """,
      """------------ THE  W E B  C R A W L E R -------------""",
      ""
    )

  /**
    * Dead letter notification during shutdown is disabled.
    *
    * @see application.conf: log-dead-letters-during-shutdown
    */
  def shutdown(httpClient: HttpClient, crawlerManager: ActorRef, restService: ActorRef): Unit = {

    val delay = 3.seconds

    log.info(s"Shutdown requested, going for shutdown in $delay")
    httpClient.shutdown()
    persistenceContext.shutdown()

    // TODO: ask crawler manager to stop jobs ...

    context.system.scheduler.scheduleOnce(2.seconds) {
      try {
        restService ! StopService
//        IO(Http).ask(Http.CloseAll)(1.second).await
      } finally {
        log.info("Goodbye!")
        context.system.terminate()
      }
    }
  }

}
