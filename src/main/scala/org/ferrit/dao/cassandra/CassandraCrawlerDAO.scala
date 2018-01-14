package org.ferrit.dao.cassandra

import com.datastax.driver.core.{BoundStatement, PreparedStatement, Row, Session}
import org.ferrit.core.json.PlayJsonImplicits
import org.ferrit.core.model.Crawler
import org.ferrit.dao.CrawlerDAO
import org.ferrit.dao.cassandra.CassandraDAO._
import play.api.libs.json.{JsError, JsSuccess, Json}


class CassandraCrawlerDAO(ttl: CassandraColumnTTL)(implicit session: Session) extends CrawlerDAO {

  import CassandraTables.{Crawler => CrawlerTable}


  override def insert(crawler: Crawler):Unit = {
    val bs: BoundStatement = bindFromEntity(stmtInsert.bind(), crawler)
    session.execute(bs)
  }

  override def delete(crawlerId: String):Unit = {
    val bs: BoundStatement = stmtDelete.bind().setString("crawler_id", crawlerId)
    session.execute(bs)
  }

  override def find(crawlerId: String):Option[Crawler] = {
    val bs: BoundStatement = stmtFind.bind().setString("crawler_id", crawlerId)
    mapOne(session.execute(bs)) {
      row => rowToEntity(row)
    }
  }

  override def findAll():Seq[Crawler] = {
    val bs: BoundStatement = stmtFindAll.bind()
    val crawlers = mapAll(session.execute(bs)) {
      row => rowToEntity(row)
    }
    crawlers.sortWith(
      (c1,c2) => c1.config.crawlerName.toLowerCase < c2.config.crawlerName.toLowerCase
    )
  }
  
  val stmtInsert: PreparedStatement = session.prepare(
    s"INSERT INTO $CrawlerTable (crawler_id, config_json) VALUES (?,?)"
  )

  val stmtDelete: PreparedStatement = session.prepare(
    s"DELETE FROM $CrawlerTable WHERE crawler_id = ?"
  )

  val stmtFind: PreparedStatement = session.prepare(
    s"SELECT * FROM $CrawlerTable WHERE crawler_id = ?"
  )

  val stmtFindAll: PreparedStatement = session.prepare(
    s"SELECT * FROM $CrawlerTable"
  )

  private [dao] def rowToEntity(row: Row):Crawler = {
    val crawlerId: String = row.getString("crawler_id")
    val json = row.getString("config_json")
    val ast = Json.parse(json)
    Json.fromJson(ast)(PlayJsonImplicits.crawlConfigReads) match {
      case JsSuccess(config, path) => Crawler(crawlerId, config)
      case JsError(errors) => throw new IllegalArgumentException(s"Cannot parse config $json")
    }
  }

  private [dao] def bindFromEntity(bs: BoundStatement, c: Crawler):BoundStatement = {
    val json = Json.stringify(Json.toJson(c.config)(PlayJsonImplicits.crawlConfigWrites))
    bs.bind()
      .setString("crawler_id", c.crawlerId)
      .setString("config_json", json)
  }

}