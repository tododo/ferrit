package org.ferrit.core.filter

import org.ferrit.core.filter.PriorityRejectUriFilter._
import org.ferrit.core.uri.CrawlUri

import scala.util.matching.Regex


/**
 * A UriFilter strategy that when encountering a URI that matches both an accept
 * and a reject rule will prioritise the reject rule first.
 * If a URI is not matched by any rule then it is automatically rejected 
 * to prevent the crawl job running on indefinitely.
 *
 */
class PriorityRejectUriFilter(val rules: Seq[Rule]) extends UriFilter {

  private def split = rules.partition(_ match {
    case _:Accept => true
    case _:Reject => false
  })

  private [filter] val accepts = split._1
  private [filter] val rejects = split._2

  override def accept(uri: CrawlUri): Boolean = test(uri).accepted
    
  override def explain(uri: CrawlUri): String =
    test(uri).matchedRule match {
      case Some(a: Accept) => AcceptMsg.format(uri, a.regex)
      case Some(r: Reject) => RejectMsg.format(uri, r.regex)
      case None => RejectDefaultMsg.format(uri)
    }

  def test(uri: CrawlUri): Result = {
    def matchesRule(r: Rule) = r.regex.findPrefixMatchOf(uri.crawlableUri).nonEmpty
    val r = rejects.find(matchesRule)
    if (r.nonEmpty) Result(false, r)
    else {
      val a = accepts.find(matchesRule)
      if (a.nonEmpty) Result(true, a)
      else Result(false, None)
    }
  }

}

object PriorityRejectUriFilter {
  
  sealed abstract class Rule(val regex: Regex, val accept: Boolean) {
    def name:String = getClass.getSimpleName.toLowerCase
  }
  case class Accept(r: Regex) extends Rule(r, true)
  case class Reject(r: Regex) extends Rule(r, false)
  sealed case class Result(accepted: Boolean, matchedRule: Option[Rule])

  val AcceptMsg:String = 
    "The URI [%s] is accepted by pattern [%s]"

  val RejectMsg:String = 
    "The URI [%s] is rejected by pattern [%s]"

  val RejectDefaultMsg:String = 
    "The URI [%s] is rejected because no accept pattern accepted it"

}