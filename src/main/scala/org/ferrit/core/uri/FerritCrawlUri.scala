package org.ferrit.core.uri

import java.net.{URLDecoder, URLEncoder}
import java.nio.charset.Charset

import scala.annotation.tailrec
import scala.collection.immutable.SortedMap
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.model.Uri.ParsingMode
import FerritUriReader._
import akka.http.scaladsl.model.Uri.Query
import akka.parboiled2.ParserInput


/**
  * An implementation of CrawlUri backed by the Spray Uri class.
  */
case class FerritCrawlUri(originalUri: String) extends CrawlUri {

  require(
    originalUri != null && originalUri.trim.nonEmpty,
    "the URI string was missing or empty"
  )

  /**
    * Internally we store a cached copy of the normalized URI
    * which unfortunately doubles the storage required becase the original
    * URI passed in to the constructor is also retained.
    * This could perhaps be replaced by a constructor in the companion
    * object that discards the original uncleansed URI after construction,
    * but to do so means losing the original URI value which sometimes is
    * useful to keep a hold of.
    */
  private[uri] val normalUri: String = render(getCrawlableUri)

  /**
    * Override default case class behaviour.
    * Is required in this case because we don't want to consider the
    * raw input URI for comparison, but instead the normalized URI, else
    * 'URI already seen' tests will fail to handle duplicates.
    */
  override def equals(a: Any): Boolean =
    if (a != null) a equals normalUri // avoid calling a.toString if possible
    else normalUri equals a.toString

  override def hashCode: Int = normalUri.hashCode

  override def toString: String = normalUri

  override def crawlableUri: String = normalUri

  override def reader: UriReader = new FerritUriReader(originalUri, this)


  override def absoluteCrawlableUri(base: CrawlUri): CrawlUri = {
    val baseUri: Uri = makeUri(base.crawlableUri)
    val absUri: Uri = getCrawlableUri.resolvedAgainst(baseUri)
    FerritCrawlUri(render(absUri))
  }

  private def getCrawlableUri: Uri = {
    val sreader = reader.asInstanceOf[FerritUriReader]

    sreader.uri.withoutFragment.withQuery(
      Query(sreader.sortedQueryMap -- CrawlUri.SessionIdKeys)
    )
  }

  private def render(uri: Uri): String = {
    uri.toString()
    //    uri.render(new StringRendering, UTF8).get
  }


}

class FerritUriReader(
                       val uriString: String,
                       override val crawlUri: CrawlUri) extends UriReader {

  val uri: Uri = makeUri(uriString)

  override def scheme: String = uri.scheme

  override def authority: String = uri.authority.toString

  override def path: String = uri.path.toString

  /**
    * Spray converts default ports for schemes like http or ssh to 0
    */
  override def schemeToPort: String = {
    val a = uri.authority
    val port = uri.authority.port
    val p = if (port == 0) "" else ":" + port
    uri.scheme + "://" + a.host + p
  }

  /**
    * Returns a new Query based on the given Query with
    * the key values sorted alphanumerically by key.
    * The intention is an additional normalization step
    * to reduce chance of duplicate URI being crawled.
    * Adapted from spray.http.Uri.Query.toMap method
    */
  def sortedQueryMap: SortedMap[String, String] = {
    @tailrec
    def append(map: SortedMap[String, String], q: Query): SortedMap[String, String] =
      if (q.isEmpty) map
      else append(map.updated(q.key, q.value), q.tail)

    append(SortedMap.empty, uri.query())
  }

}

object FerritUriReader {

  /**
    * The choice of ParsingMode influences treatment of query string parsing.
    * RelaxedWithRawQuery is the most lenient and does not touch query string,
    * but tradeoff is that parsing Query into parts is not possible.
    * The Relaxed setting allows  query to be parsed into key values,
    * making it possible to remove parameters, but values get decoded.
    *
    * Newline characters: Windows \r\n, Max OS \n, Unix \r
    *
    * @see http://stackoverflow.com/questions/15433188/r-n-r-n-what-is-the-difference-between-them
    *
    */
  def makeUri(uri: String): Uri = {
    val u = Uri(Uri.normalize(ParserInput(clean(uri)), Charset.forName("UTF8"), ParsingMode.Relaxed))

    Uri.from(u.scheme, u.authority.userinfo, u.authority.host.toString(), u.authority.port, u.path.toString(),
      u.rawQueryString, Some(u.fragment.toString), Uri.ParsingMode.Relaxed)
  }


  private def clean(uri: String): String = {
    def m(url: String) = url.indexOf('#') > 0 match {
      case true => url.substring(url.indexOf('?') + 1, url.indexOf('#'))
      case _ => url.substring(url.indexOf('?') + 1)
    }

    def n(c: String): String = URLDecoder.decode(c).equals(c) match {
      case true => URLEncoder.encode(c)
      case _ => c
    }

    val url = uri.replaceAll(" ", "%20") // Spray blows up with spaces or newline characters
      .replaceAll("\r", "") // Remove 3 kinds of newline: \r\n, \r, \n
      .replaceAll("\n", "")

    if (url.contains('?')) {
      url.substring(0, url.indexOf('?')) + '?' + m(url).split('&').map(e => e.split('=').map(p => n(p)).mkString("=")).mkString("&")
    } else
      url
  }
}