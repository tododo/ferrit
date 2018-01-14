package org.ferrit.dao

import org.ferrit.core.model.Crawler
import org.ferrit.core.crawler.CrawlConfig
import org.ferrit.core.filter.FirstMatchUriFilter
import org.ferrit.core.filter.FirstMatchUriFilter.{Accept, Reject}
import org.ferrit.core.uri.CrawlUri
import org.scalatest.Matchers


class ITestCrawlerDAO extends AbstractDAOTest with Matchers {

  behavior of "CrawlerDAO"

  val crawlerDao = daoFactory.crawlerDao

  def makeCrawler(crawlerId: String) = {
    val site = "http://site.net"
    Crawler(
      crawlerId,
      CrawlConfig(
        id = crawlerId,
        userAgent = Some("Test Agent"),
        crawlerName = "Test Crawler",
        seeds = Seq(CrawlUri("http://site.net")),
        uriFilter = new FirstMatchUriFilter(Seq(
          Reject("http://other-site.net".r),
          Accept(site.r)
        )),
        tests = Some(Seq(s"accept: $site")),
        crawlDelayMillis = 0,
        crawlTimeoutMillis = 10000,
        maxDepth = Int.MaxValue,
        maxFetches = 10000,
        maxQueueSize = 10000,
        maxRequestFails = 0.5
    ))
  }

  it should "insert and read Crawler" in {

    val crawlerId = "1234"
    val crawler = makeCrawler(crawlerId)

    crawlerDao.insert(crawler)
    crawlerDao.find(crawlerId) match {
      case Some(crawler2) =>
        crawler2.config.crawlerName should equal (crawler.config.crawlerName)
      case None => fail("Crawler not found ${config.id}")
    }

    crawlerDao.delete(crawlerId)
    crawlerDao.find(crawlerId) match {
      case Some(crawler2) => fail("Crawler was not deleted")
      case _ =>
    }

  }

  it should "insert and read crawlers" in {

    val max = 9
    (1000 to (1000+max)).foreach(i => crawlerDao.insert(makeCrawler(s"$i")))

    crawlerDao.findAll() match {
      case Seq(c1, c2, c3, c4, c5, c6, c7, c8, c9, _*) => // okay
      case _ => fail(s"At least $max crawlers should exist")
    }

    (1000 to (1000+max)).foreach(i => crawlerDao.delete(s"$i"))

  }

}
