package org.ferrit.server

import akka.http.scaladsl.server.Rejection

case class BadEntityRejection(name: String, value: String) extends Rejection