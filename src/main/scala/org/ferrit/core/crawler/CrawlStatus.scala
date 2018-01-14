package org.ferrit.core.crawler

import java.time.LocalDateTime


case class CrawlStatus(

  crawlStart: LocalDateTime = LocalDateTime.now,
  crawlStop: LocalDateTime,
  alive: Boolean = true,
  shouldStop: Boolean = false

) {

  def stop = copy(shouldStop = true)
  def dead = copy(alive = false)
  def now = LocalDateTime.now
  
}
