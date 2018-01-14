package org.ferrit.core.test

import org.ferrit.core.http.{DefaultResponse, Request, Response, Stats}

/**
 * Utility used with FakeHttpClient, a fake response minus the Request param 
 * that is only available at request time.
 */
case class PartResponse(
  status: Int, 
  headers: Map[String,Seq[String]],
  content: String
) {
  
  def toResponse(request: Request, stats: Stats = Stats.empty):Response = {
    DefaultResponse(status, headers, content.getBytes, stats, request)
  }

}
