package org.ferrit.server

import spray.routing.Rejection

case class BadParamRejection(name: String, value: String) extends Rejection