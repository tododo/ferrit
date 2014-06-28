package org.ferrit.server

import spray.http.{StatusCode, StatusCodes}
import spray.httpx.marshalling._
import spray.httpx.PlayJsonSupport._
import spray.routing.{Directives, ExceptionHandler}
import spray.util.LoggingContext
import org.ferrit.core.crawler.CrawlRejectException
import org.ferrit.server.json.PlayJsonImplicits._
import org.ferrit.server.json.ErrorMessage


object CustomExceptionHandler extends Directives {
  
  val ServerErrorMsg = "Apologies, an internal server error occurred whilst handling your request"

  def handler(implicit log: LoggingContext):ExceptionHandler = 
    ExceptionHandler {
      case throwable: Throwable => 
        requestInstance { request =>
          complete {
            
            val (sc: StatusCode, msg: String) = throwable match {

              case cre: CrawlRejectException =>
                StatusCodes.InternalServerError -> ServerErrorMsg
                
              case other =>
                StatusCodes.InternalServerError -> ServerErrorMsg
            }

            log.error(throwable, s"Request exception for $request: $msg")
            sc -> ErrorMessage(sc.intValue, msg)
          }
        }
    }

}