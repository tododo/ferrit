package org.ferrit.server

import akka.event.LoggingAdapter
import akka.http.scaladsl.model.{StatusCode, StatusCodes}
import akka.http.scaladsl.server.{Directives, ExceptionHandler}
import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport
import org.ferrit.core.crawler.CrawlRejectException
import org.ferrit.server.json.ErrorMessage
import org.ferrit.server.json.PlayJsonImplicits._


object CustomExceptionHandler extends Directives with PlayJsonSupport{
  
  val ServerErrorMsg = "Apologies, an internal server error occurred whilst handling your request"

  def handler(implicit log: LoggingAdapter):ExceptionHandler =
    ExceptionHandler {
      case throwable: Throwable =>
        extractRequest { request =>
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