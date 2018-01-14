package org.ferrit.core.model

import java.time.{LocalDate, LocalDateTime}

import org.ferrit.core.crawler.CrawlConfig
import org.ferrit.core.util.{Media, UniqueId}

sealed case class CrawlJob(
  crawlerId: String,
  crawlerName: String,
  jobId: String,
  node: String,
  partitionDate: LocalDate,
  snapshotDate: LocalDateTime,
  createdDate: LocalDateTime,
  finishedDate: Option[LocalDateTime],
  duration: Long,
  outcome: Option[String],
  message: Option[String],
  urisSeen: Int,
  urisQueued: Int,
  fetchCounters: Map[String, Int],
  responseCounters: Map[String, Int],
  mediaCounters: Map[String, Media]
  ) {

  def isFinished: Boolean = finishedDate.nonEmpty

}

object CrawlJob {

  def create(config: CrawlConfig, node: String) = CrawlJob(
    crawlerId = config.id,
    crawlerName = config.crawlerName,
    jobId = UniqueId.next,
    node = node,
    partitionDate = LocalDateTime.now().toLocalDate,
    snapshotDate = LocalDateTime.now,
    createdDate = LocalDateTime.now,
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

}
