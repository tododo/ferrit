package org.ferrit.core.test

import org.ferrit.core.http.{Request, Response}
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext


/**
 * A FakeHttpClient that simply maps an incoming request URI
 * to a canned Response in a map. If the Response is not found 
 * in the map a 404 not found response is returned.
 */
class ParrotHttpClient(responses: Map[String, PartResponse], targetUri: Seq[String] = Seq[String]())(implicit ec:
ExecutionContext)
  extends
  FakeHttpClient {

  val logger = LoggerFactory.getLogger(getClass)
  val responsesForTargetedUri = scala.collection.mutable.HashMap.empty[String, PartResponse]
  require(responses != null, "A Map of request URI to Response is required")

  override implicit val _ec: ExecutionContext = ec

  override def handleRequest(request: Request):Response = {
    val uri: String = request.crawlUri.crawlableUri
    val pr: PartResponse = responses.getOrElse(uri, FakeHttpClient.NotFound)



    if(targetUri.contains(request.crawlUri.crawlableUri))
      responsesForTargetedUri.put(request.crawlUri.crawlableUri, pr)

    pr.toResponse(request)

  }

  override def getTrackedResponses(): Map[String, PartResponse] = {
    responsesForTargetedUri.toMap
  }
}
