package org.ferrit.core.test

import org.ferrit.core.http.{Get, Response}
import org.ferrit.core.uri.CrawlUri
import org.scalatest.{FlatSpec, Matchers}

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

/**
 * These tests test the testing tools themselves.
 */
class TestFakeHttpClients extends FlatSpec with Matchers {
  
  behavior of classOf[LinkedListHttpClient].getSimpleName

  
  it should "return pages within a given number range" in {
    
    val numPages = 100
    val client = new LinkedListHttpClient("http://site.net", numPages)

    def request(uri: String):Response = {
      val r = Get("*", CrawlUri(uri), Map.empty)
      Await.result(client.request(r), 100.milliseconds)
    }

    // The page numbering is zero based.
    // The 0th page is the index.
    // Page N is in fact shown as page{N-1}.html

    // The first page (the index) returns second page (e.g. page 1)
    request("http://site.net")
      .contentString
      .contains("""<a href="http://site.net/page1.html">""") should equal (true)

    // Second points to third (e.g. page1 links to page2)
    request("http://site.net/page1.html")
      .contentString
      .contains("""<a href="http://site.net/page2.html">""") should equal (true)
    
    // Penultimate points to last (page98 links to page99)
    request("http://site.net/page98.html")
      .contentString
      .contains("""<a href="http://site.net/page99.html">""") should equal (true)
  
    // Last page is 99 but should not contain any link
    request("http://site.net/page100.html")
      .contentString
      .contains("""<a href="http://site.net/page100.html">""") should equal (false)
  
    // There is no page0 because the index is reserved for that
    request("http://site.net/page0.html").statusCode should equal (404)

    // Other requests that should 404
    request("http://site.net/page101.html").statusCode should equal (404)
    request("http://site.net:8080/page1.html").statusCode should equal (404)
    request("http://site2.net/page1.html").statusCode should equal (404)
    
  }

}