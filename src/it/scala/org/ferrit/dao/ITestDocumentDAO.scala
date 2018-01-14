package org.ferrit.dao

import java.nio.ByteBuffer

import org.ferrit.core.model.Document
import org.scalatest.{FlatSpec, Matchers}



class ITestDocumentDAO extends AbstractDAOTest with Matchers {
  
  behavior of "DocumentDAO"  

  val docDao = daoFactory.documentDao


  it should "insert and read a document" in {

    val html = """
      |<!doctype html>
      |<html>
      |<head><title>Page</title></head>
      |<body>
      |<h1>Page</h1>
      |</body>
      |</html>
      """.stripMargin

    val crawlerId = "1234"
    val jobId = "4321"
    val uri = "http://site.net/page1"
    val contentType = "text/html; charset=UTF-8"
    val content: Array[Byte] = html.getBytes

    val doc = Document(
      crawlerId,
      jobId,
      uri,
      contentType,
      content
    )
    
    docDao.insert(doc)

    docDao.find(jobId, uri) match {
      case Some(doc2) => 
        doc.crawlerId should equal (doc2.crawlerId)
        doc.jobId should equal (doc2.jobId)
        doc.uri should equal (doc2.uri)
        doc.contentType should equal (doc2.contentType)
        val html2 = new String(doc2.content, "UTF-8")
        html2 should equal (html)
      case None => fail("Document not found")
    }

  }

}