package org.ferrit.dao.cassandra

import com.datastax.driver.core.{Cluster, Session}
import com.datastax.driver.core.policies.Policies
import com.typesafe.config.Config


class CassandraPersistenceManager(config: CassandraConfig) {
  
  val cluster: Cluster = CassandraPersistenceManager.initCluster(config)
  val session: Session = cluster.connect(config.keyspace)
  
  def shutdown():Unit = {
    cluster.close()
  }

  def getColumnTTL(config: Config):CassandraColumnTTL =   
    CassandraColumnTTL(
      CassandraTables.AllTables.map({t => 
        (t -> config.getInt(s"persistence.cassandra.tableColumnTTL.$t"))
      }).toMap
    )
  
}

object CassandraPersistenceManager {

  /**
   * Best to have this in an object, can be used in tests as well.
   */
  def initCluster(config: CassandraConfig):Cluster = 
    Cluster.builder()
      .addContactPoints(config.nodes.toArray: _*)
      .withPort(config.port)
      .withRetryPolicy(Policies.defaultRetryPolicy())
      .build()

}

object CassandraTables {
  
  val Crawler = "crawler"
  val CrawlJobByCrawler = "crawl_job_by_crawler"
  val CrawlJobByDate = "crawl_job_by_date"
  val FetchLog = "fetch_log"
  val Document = "document"
  val DocumentMetaData = "document_metadata"

  val AllTables = Seq(
    //Crawler,
    CrawlJobByCrawler,
    CrawlJobByDate,
    FetchLog,
    Document,
    DocumentMetaData
  )

}

case class CassandraConfig(
  keyspace: String, 
  nodes: Seq[String],
  port: Int
)