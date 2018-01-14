package org.ferrit.server

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{Directives, MalformedRequestContentRejection, RejectionHandler, ValidationRejection}
import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport
import org.ferrit.server.json.ErrorMessage
import org.ferrit.server.json.PlayJsonImplicits._

object CustomRejectionHandler extends Directives with PlayJsonSupport{
  
  val UnmarshallErrorMsg = "The request entity could not be unmarshalled."
  val BadEntityMsg = "No %s found with identifier [%s]"
  val BadParamMsg = "Parameter [%s] has invalid value [%s]"


  val customRejectionHandler: RejectionHandler = RejectionHandler.newBuilder().handle {

    case MalformedRequestContentRejection(msg, cause) =>
      complete {
        val sc = StatusCodes.BadRequest
        sc -> ErrorMessage(sc.intValue, UnmarshallErrorMsg)
      }
    case ValidationRejection(msg, cause) =>
      complete {
        val sc = StatusCodes.BadRequest
        sc -> ErrorMessage(sc.intValue, UnmarshallErrorMsg)
      }
    case BadEntityRejection(name, value) =>
      complete {
        val sc = StatusCodes.NotFound
        sc -> ErrorMessage(sc.intValue, BadEntityMsg.format(name, value))
      }

    case BadParamRejection(name, value) =>
      complete {
        val sc = StatusCodes.BadRequest
        sc -> ErrorMessage(sc.intValue, BadParamMsg.format(name, value))
      }

  }.result()

}