package org.ferrit.server.json

case class Id(id: String)
case class Message(message: String)
case class ErrorMessage(statusCode: Int, message: String)
