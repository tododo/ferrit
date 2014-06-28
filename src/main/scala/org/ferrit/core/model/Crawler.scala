package org.ferrit.core.model

import org.ferrit.core.crawler.CrawlConfig

case class Crawler(
  crawlerId: String, 
  config: CrawlConfig
)

object Crawler {
  
  def create(config: CrawlConfig):Crawler = {
    // create new ID, ignore any value set in config
    val id = java.util.UUID.randomUUID().toString()
    val newConfig = config.copy(id=id)
    Crawler(id, newConfig)
  }

}