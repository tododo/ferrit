package org.ferrit.core.test

import org.ferrit.core.robot.RobotRulesCache
import org.ferrit.core.uri.UriReader

import scala.concurrent.{ExecutionContext, Future}


class MockRobotRulesCache(implicit ec: ExecutionContext) extends RobotRulesCache {

  override def getDelayFor(userAgent: String, reader: UriReader):Future[Option[Int]] = Future.successful(None)
  override def allow(ua: String, reader: UriReader) = Future {true}
  
}