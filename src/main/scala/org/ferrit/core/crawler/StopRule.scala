package org.ferrit.core.crawler

import org.ferrit.core.util.Counters

/**
 * A StopRule decides if a org.ferrit.core.crawler should keep crawling or stop.
 * Before each fetch a org.ferrit.core.crawler queries this rule to find out if
 * it should continue crawling or stop. It returns a CrawlOutcome decision. 
 *
 * A special outcome called KeepCrawling instructs the org.ferrit.core.crawler to keep going.
 * Any other outcome requires the org.ferrit.core.crawler to stop (e.g. StopRequested).
 */
trait StopRule {

  def ask(
    config: CrawlConfig, 
    status: CrawlStatus, 
    counters: Counters, 
    fetchesPending: Int):CrawlOutcome
  
}