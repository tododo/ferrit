package org.ferrit.server

import spray.routing.Rejection

case class BadEntityRejection(name: String, value: String) extends Rejection