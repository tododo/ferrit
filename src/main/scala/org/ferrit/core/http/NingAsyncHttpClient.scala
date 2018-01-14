package org.ferrit.core.http

import java.io.ByteArrayOutputStream
import java.util.concurrent.{Future => JFuture}
import java.util.{List => JList, Map => JMap, Set => JSet}

import com.ning.http.client.AsyncHandler.STATE
import com.ning.http.client.{AsyncHandler, AsyncHttpClient, AsyncHttpClientConfig, HttpResponseBodyPart, HttpResponseHeaders, HttpResponseStatus, Response => NingResponse}
import org.ferrit.core.http.{Response => OurResponse}

import scala.collection.JavaConverters._
import scala.concurrent.{Future, Promise}

/**
 * HttpClient implementation that uses the Ning AsyncHttpClient.
 */
class NingAsyncHttpClient(config: HttpClientConfig) extends HttpClient {

  private val client = {
    val conf = new AsyncHttpClientConfig.Builder()
      .setAllowPoolingConnections(config.useConnectionPooling)
      .setRequestTimeout(config.requestTimeout)
      .setFollowRedirect(config.followRedirects)
      .setCompressionEnforced(config.useCompression)
      .build()
    new AsyncHttpClient(conf)
  }

  override def shutdown(): Unit = client.close()

  override def request(request: Request): Future[OurResponse] = {
    
    val allHeaders = request.headers ++ Map[String, String](
      "User-Agent" -> request.userAgent,
      "Pragma" -> "no-cache",
      "Cache-Control" -> "no-cache",
      "Connection" -> "keep-alive",
      "Keep-Alive" -> s"${config.keepAlive}"
    )

    val promise = Promise[OurResponse]()
    val handler = createAsyncHandler(request, promise)
    val reqBuilder = client.prepareGet(request.crawlUri.crawlableUri)

    allHeaders.foreach(pair => reqBuilder.addHeader(pair._1, pair._2))
    reqBuilder.execute(handler)
    promise.future

  }

  def createAsyncHandler(request: Request, promise:Promise[OurResponse]):AsyncHandler[OurResponse] = {
    new Handler(request, promise)
  }
  
  /**
   * Implements AsyncDirectly but we don't get the Ning Response in onCompleted()
   */
  class Handler(request: Request, promise:Promise[OurResponse]) extends AsyncHandler[OurResponse] {

    var bytes = new ByteArrayOutputStream
    var headerMap: Map[String, List[String]] = Map.empty
    var statusCode: Int = -1
    var length = 0

    var timeStatus: Long = _
    var timeHeaders: Long = _
    var timeCompleted: Long = _

    def now():Long = System.currentTimeMillis
    var start = now

    def onStatusReceived(status: HttpResponseStatus):STATE = {
      statusCode = status.getStatusCode()
      timeStatus = now() - start
      STATE.CONTINUE // what if status code > = 500?
    }

    def onHeadersReceived(h: HttpResponseHeaders):STATE = {
      // headers is a FluentCaseInsensitiveStringsMap
      val set: JSet[JMap.Entry[String, JList[String]]] = h.getHeaders().entrySet()
      set.asScala.foreach(e => 
        headerMap += (e.getKey() -> e.getValue().asScala.toList)
      )
      timeHeaders = now() - start
      STATE.CONTINUE
    }

    def onBodyPartReceived(bodyPart: HttpResponseBodyPart): STATE = {
      val b: Array[Byte] = bodyPart.getBodyPartBytes()
      length += b.length
      bytes.write(b)
      if (maxSizeExceeded) STATE.ABORT else STATE.CONTINUE
    }

    def onCompleted():OurResponse = {
      if (maxSizeExceeded) 
        throw new Exception(s"Max content size exceeded: ${config.maxContentSize}")
      else {
        timeCompleted = now() - start
        val response =
          DefaultResponse(
            statusCode,
            headerMap,
            bytes.toByteArray(),
            Stats(timeStatus, timeHeaders, timeCompleted),
            request
          )
        promise.success(response)
        response // only needed for the Ning AsyncHandler, not the Promise
      }
    }

    def maxSizeExceeded = length > config.maxContentSize

    def onThrowable(t: Throwable):Unit = promise.failure(t)

  }

}