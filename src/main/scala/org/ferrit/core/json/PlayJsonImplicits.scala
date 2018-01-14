package org.ferrit.core.json

import java.time.{LocalDate, LocalDateTime}

import org.joda.time.DateTime
import play.api.libs.json._

import scala.util.matching.Regex
import org.ferrit.core.crawler.{CrawlConfig, CrawlConfigTester}
import org.ferrit.core.crawler.CrawlConfigTester.{Result, Results}
import org.ferrit.core.filter._
import org.ferrit.core.model.{CrawlJob, DocumentMetaData, FetchLogEntry}
import org.ferrit.core.util.{KeyValueParser, Media}
import org.ferrit.core.uri.{CrawlUri, FerritCrawlUri}

object PlayJsonImplicits {
  
  val Iso8601Format = "yyyy-MM-dd'T'HH:mm:ss.SSSZ"

  implicit val jodaIso8601DateWrites: Writes[DateTime] = new Writes[DateTime] {
    def writes(d: DateTime): JsValue = JsString(d.toString(Iso8601Format))
  }

  implicit val mediaWrites: Writes[Media] = new Writes[Media] {
    def writes(m: Media): JsValue = Json.obj(
      "count" -> m.count, 
      "totalBytes" -> m.totalBytes
    )
  }
  implicit val localDateFormat = new Format[LocalDate] {
    override def reads(json: JsValue): JsResult[LocalDate] =
      json.validate[String].map(LocalDate.parse)

    override def writes(o: LocalDate): JsValue = Json.toJson(o.toString)
  }

  implicit val localDateTimeFormat = new Format[LocalDateTime] {
    override def reads(json: JsValue): JsResult[LocalDateTime] =
      json.validate[String].map(LocalDateTime.parse)

    override def writes(o: LocalDateTime): JsValue = Json.toJson(o.toString)
  }
  implicit val crawlJobWrites = Json.writes[CrawlJob]
  implicit val fetchLogEntryWrites = Json.writes[FetchLogEntry]



  implicit val uriFilterReads = new Reads[UriFilter] {

    import KeyValueParser.parse
    final val FirstMatchUriFilterT = classOf[FirstMatchUriFilter].getName
    final val PriorityRejectUriFilterT = classOf[PriorityRejectUriFilter].getName

    def reads(value: JsValue):JsResult[UriFilter] = {
      
      val rarray: Seq[JsValue] = (value \ "rules").as[JsArray].value
      val rules:Seq[String] = rarray.map(r => r.as[JsString].value)
      val ftype: String = (value \ "filterClass").as[JsString].value

      val filter = ftype match {
        case FirstMatchUriFilterT =>
          import FirstMatchUriFilter.{Accept, Reject}
          new FirstMatchUriFilter(
            parse(Seq("accept", "reject"), rules, {(key:String, value:String) => 
              if ("accept" == key) Accept(value.r) else Reject(value.r)
            })
          )

        case PriorityRejectUriFilterT =>
          import PriorityRejectUriFilter.{Accept, Reject}
          new PriorityRejectUriFilter(
            parse(Seq("accept", "reject"), rules, {(key:String, value:String) => 
              if ("accept" == key) Accept(value.r) else Reject(value.r)
            })
          )
      }

      JsSuccess(filter)
    }

  }

  implicit val firstMatchUriFilterWrites = new Writes[FirstMatchUriFilter] {
    def writes(filter: FirstMatchUriFilter):JsValue = {
      Json.obj(
        "filterClass" -> filter.getClass.getName,
        "rules" -> filter.rules.map({r => s"${r.name}: ${r.regex.toString}"})
      )
    }
  }

  implicit val priorityRejectUriFilterWrites = new Writes[PriorityRejectUriFilter] {
    def writes(filter: PriorityRejectUriFilter):JsValue = {
      Json.obj(
        "filterClass" -> filter.getClass.getName,
        "rules" -> filter.rules.map({r => s"${r.name}: ${r.regex.toString}"})
      )
    }
  }

  implicit val uriFilterWrites = new Writes[UriFilter] {
    def writes(filter: UriFilter):JsValue = {
      filter match {
        case f: FirstMatchUriFilter => 
          firstMatchUriFilterWrites.writes(filter.asInstanceOf[FirstMatchUriFilter])
        case p: PriorityRejectUriFilter => 
          priorityRejectUriFilterWrites.writes(filter.asInstanceOf[PriorityRejectUriFilter])
        case _ => throw new IllegalArgumentException(s"No writer for ${filter.getClass.getName}")
      }
    }
  }

  implicit val crawlUriReads = new Reads[CrawlUri] {
    def reads(value: JsValue):JsResult[CrawlUri] = {
      val uri = CrawlUri(value.as[JsString].value)
      JsSuccess(uri)
    }
  }

  implicit val crawlUriWrites = new Writes[CrawlUri] {
    def writes(uri: CrawlUri):JsValue = {
      JsString(uri.asInstanceOf[FerritCrawlUri].originalUri)
    }
  }

  implicit val crawlConfigReads = Json.reads[CrawlConfig]
  implicit val crawlConfigWrites = Json.writes[CrawlConfig]

  implicit val crawlConfigTestResultWrites = Json.writes[CrawlConfigTester.Result]
  implicit val crawlConfigTestResultsWrites = Json.writes[CrawlConfigTester.Results]

  implicit val documentMetaReads = Json.reads[DocumentMetaData]
  implicit val documentMetaWrites = Json.writes[DocumentMetaData]

}