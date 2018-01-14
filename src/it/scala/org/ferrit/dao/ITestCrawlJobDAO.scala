package org.ferrit.dao

import java.time.{LocalDate, LocalDateTime}

import scala.util.Random
import org.ferrit.core.model.CrawlJob
import org.ferrit.core.util.Media
import org.scalatest.Matchers


class ITestCrawlJobDAO extends AbstractDAOTest with Matchers {

  behavior of "CrawlJobDAO"

  val jobDao = daoFactory.crawlJobDao

  it should "insert and read start job" in {

    val crawlerId = "1234"
    val jobId = makeStringId
    val job = CrawlJob(
      crawlerId = crawlerId,
      crawlerName = "Test Crawl",
      jobId = jobId,
      node = "localhost",
      snapshotDate = LocalDateTime.now(),
      partitionDate = LocalDate.now(),
      createdDate = LocalDateTime.now(),
      finishedDate = None,
      duration = 0,
      outcome = None,
      message = None,
      urisSeen = 0,
      urisQueued = 0,
      fetchCounters = Map.empty,
      responseCounters = Map.empty,
      mediaCounters = Map.empty
    )

    jobDao.insertByCrawler(Seq(job))
    jobDao.insertByDate(Seq(job))

    jobDao.find(crawlerId, jobId) match {
      case Some(job2) => job2 should equal (job)
      case None => fail(s"Job not found")
    }

  }

  it should "insert and read job with end portion" in {

    val crawlerId = "4321"
    val jobId = makeStringId
    val job = CrawlJob(
      crawlerId = crawlerId,
      crawlerName = "Test Crawl 2",
      jobId = jobId,
      node = "localhost",
      snapshotDate = LocalDateTime.now(),
      partitionDate = LocalDate.now(),
      createdDate = LocalDateTime.now(),
      finishedDate = Some(LocalDateTime.now()),
      duration = 0,
      outcome = Some("Completed Okay"),
      message = None,
      urisSeen = 0,
      urisQueued = 0,
      fetchCounters = Map.empty,
      responseCounters = Map.empty,
      mediaCounters = Map.empty
    )

    jobDao.insertByCrawler(Seq(job))
    jobDao.insertByDate(Seq(job))

    jobDao.find(crawlerId, jobId) match {
      case Some(job2) => job2 should equal (job)
      case None => fail(s"Job not found")
    }

  }

  it should "bulk insert and read jobs" in {

    val crawlerId = makeStringId
    val maxJobs = 100
    val randomDateRange = LocalDate.now()
        .plusDays(Random.nextInt(365))


    val jobs:Seq[CrawlJob] = (0 until maxJobs).map({i =>
      CrawlJob(
        crawlerId = crawlerId,
        crawlerName = s"Test Crawl $i",
        jobId = makeStringId,
        node = "localhost",
        snapshotDate = LocalDateTime.now(),
        partitionDate = randomDateRange,
        createdDate = LocalDateTime.now(),
        finishedDate = Some(LocalDateTime.now()),
        duration = i,
        outcome = Some("Completed Okay"),
        message = None,
        urisSeen = i,
        urisQueued = i,
        fetchCounters = Map("total" -> i),
        responseCounters = Map("200" -> i),
        mediaCounters = Map("text/html" -> Media(i, i*100))
      )
    })

    jobDao.insertByCrawler(jobs)

    jobDao.find(crawlerId) match {
      case Nil => fail(s"No jobs found")
      case jobs => jobs.size should equal (maxJobs)
    }

    jobDao.insertByDate(jobs)

    jobDao.find(randomDateRange) match {
      case Nil => fail(s"No jobs found")
      case jobs => jobs.size should equal (maxJobs)
    }

  }

}