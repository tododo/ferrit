package org.ferrit.core.model

import java.time.LocalDateTime

case class DocumentMetaData(
  crawlerId: String,
  jobId: String,
  uri: String,
  contentType: String,
  contentLength: Int,
  depth: Int,
  fetched: LocalDateTime,
  responseStatus: String
)