package org.ferrit.dao.cassandra

import com.datastax.driver.core._
import org.ferrit.core.model.FetchLogEntry
import org.ferrit.dao.FetchLogEntryDAO
import org.ferrit.dao.cassandra.CassandraDAO._


class CassandraFetchLogEntryDAO(ttl: CassandraColumnTTL)(implicit session: Session) extends FetchLogEntryDAO {
  
  import CassandraTables.{FetchLog => FetchLogTable}

  val timeToLive = ttl.get(FetchLogTable)

  val stmtInsert: PreparedStatement = session.prepare(
    s"INSERT INTO $FetchLogTable (" + 
    "  crawler_id, job_id, log_time, uri, uri_depth, status_code, " + 
    "  content_type, content_length, links_extracted, " + 
    "  fetch_duration, request_duration, parse_duration, " + 
    "  uris_seen, uris_queued, fetches " +
    ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) USING TTL " + timeToLive
  )

  val stmtFindByPk: PreparedStatement = session.prepare(
    s"SELECT * FROM $FetchLogTable WHERE job_id = ?"
  )

  val stmtFindFirst: PreparedStatement = session.prepare(
    s"SELECT * FROM $FetchLogTable WHERE job_id = ? LIMIT 1"
  )

  def insert(fle: FetchLogEntry):Unit = insert(Seq(fle))
  
  def insert(entries: Seq[FetchLogEntry]):Unit = {
    val batch = new BatchStatement
    entries.foreach(e => batch.add(bindFromEntity(stmtInsert.bind(), e)) )
    session.execute(batch)  
  }

  def find(jobId: String):Seq[FetchLogEntry] = {
    val bs = stmtFindByPk.bind().setString("job_id", jobId)
    mapAll(session.execute(bs)) {
      row => rowToEntity(row)
    }
  }

  private [dao] def rowToEntity(row: Row):FetchLogEntry = {
    FetchLogEntry(
      row.getString("crawler_id"),
      row.getString("job_id"),
      row.getTimestamp("log_time"),
//      LocalDateTime.ofInstant(row.getTimestamp("log_time").toInstant, ZoneId.systemDefault()),
      row.getString("uri"),
      row.getInt("uri_depth"),
      row.getInt("status_code"),
      row.getString("content_type"),
      row.getInt("content_length"),
      row.getInt("links_extracted"),
      row.getInt("fetch_duration"),
      row.getInt("request_duration"),
      row.getInt("parse_duration"),
      row.getInt("uris_seen"),
      row.getInt("uris_queued"),
      row.getInt("fetches")
    )   
  }

  private [dao] def bindFromEntity(bs: BoundStatement, fle: FetchLogEntry):BoundStatement = {
    bs.bind()
      .setString("crawler_id", fle.crawlerId)
      .setString("job_id", fle.jobId)
      .setTimestamp("log_time", fle.logTime)
//      .setTimestamp("log_time", Date.from(fle.logTime.atZone(ZoneId.systemDefault()).toInstant))
      .setString("uri", fle.uri)
      .setInt("uri_depth", fle.uriDepth)
      .setInt("status_code", fle.statusCode)
      .setString("content_type", fle.contentType)
      .setInt("content_length", fle.contentLength)
      .setInt("links_extracted", fle.linksExtracted)
      .setInt("fetch_duration", fle.fetchDuration)
      .setInt("request_duration", fle.requestDuration)
      .setInt("parse_duration", fle.parseDuration)
      .setInt("uris_seen", fle.urisSeen)
      .setInt("uris_queued", fle.urisQueued)
      .setInt("fetches", fle.fetches)
  }

}
