package org.ferrit.server

import akka.http.scaladsl.server.Rejection

case class BadParamRejection(name: String, value: String) extends Rejection