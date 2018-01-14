package org.ferrit.core.test

import java.util.concurrent.TimeoutException

import org.ferrit.core.http.{HttpClient, Request, Response}
import org.ferrit.core.util.Headers

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future, Promise}


trait FakeHttpClient extends HttpClient {

  override def shutdown() = {}

  override def request(request: Request):Future[Response] = {
    blockingDelay
    Future {
      handleRequest(request)
    }
  }
  def calculateLength(): Int = {
    val responses = getTrackedResponses()
    val l = for (e <- responses) yield e._2.content.length()
    l.foldLeft(0) { _ + _}
  }

  def getTrackedResponses(): Map[String, PartResponse] = {
    scala.collection.immutable.Map()
  }

  def responseDelay:Duration = 0.milliseconds

  /**
   * Implementations override with a provided ExecutionContext
   */
  implicit val _ec: ExecutionContext

  /**
   * Implementations handle this without needing to mess with futures
   */
  def handleRequest(request: Request):Response
  

  private [test] def blockingDelay:Unit = {
    try {
      
      // A better way would be to use after()
      // and import akka.pattern.after
      // But how to obtain a Scheduler?
      // See v.klang's answer: 
      // http://stackoverflow.com/questions/16359849/scala-scheduledfuture
      // http://doc.akka.io/api/akka/2.2.1/#akka.pattern.package
      Await.ready(Promise[Unit]().future, responseDelay)
      
    } catch {
      case t: TimeoutException =>
    }
  }
 
}

object FakeHttpClient {
  
  val NotFound = PartResponse(404, Map.empty, "The resource you requested could not be found")
  val ServerError = PartResponse(500, Map.empty, "Internal server error")
  val Redirect = PartResponse(301, Map.empty, "")

  def OkayResponse(headers: Map[String,Seq[String]], content: String) = 
    PartResponse(200, headers, content)

  def HtmlResponse(content: String) =
    OkayResponse(Map(Headers.ContentTypeTextHtmlUtf8), content)

  def TextResponse(content: String) =
    OkayResponse(Map(Headers.ContentTypeTextUtf8), content)

  def CssResponse(content: String) = 
    OkayResponse(Map(Headers.ContentTypeTextCssUtf8), content)

}
