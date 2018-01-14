package org.ferrit.dao

import scala.util.Random
import org.scalatest.{BeforeAndAfterAll, FlatSpec}
import com.datastax.driver.core.{Cluster, Session}
import com.datastax.driver.core.policies.Policies
import org.ferrit.dao.cassandra._

object TestDB {
  val config = CassandraConfig("ferrit", Seq("127.0.0.1"), 9042)
}


abstract class AbstractDAOTest(

  val cluster: Cluster = CassandraPersistenceManager.initCluster(TestDB.config)

  ) extends FlatSpec with BeforeAndAfterAll {

  implicit val session: Session = cluster.connect(TestDB.config.keyspace)

  val ttl =
    CassandraColumnTTL(
      CassandraTables.AllTables.map(t => (t -> 60*60*24)).toMap
    )


  val daoFactory: DAOFactory = new CassandraDAOFactory(ttl, session)

  override def beforeAll():Unit = {}

  override def afterAll():Unit = {
    cluster.close()
    Thread.sleep(50)
  }

  def makeStringId:String = "" + Random.nextInt(1000000)

}