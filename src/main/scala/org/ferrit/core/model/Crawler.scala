package org.ferrit.core.model

import org.ferrit.core.crawler.CrawlConfig
import org.ferrit.core.util.UniqueId

case class Crawler(
  crawlerId: String, 
  config: CrawlConfig
)

object Crawler {
  
  def create(config: CrawlConfig):Crawler = {
    // create new ID, ignore any value set in config
    val id = UniqueId.next
    val newConfig = config.copy(id=id)
    Crawler(id, newConfig)
  }

}