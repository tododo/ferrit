package org.ferrit.core.util

/**
  * A map of Media type records keyed by ContentType HTTP header string that
  * store frequency count and total bytes downloaded.
  */
case class MediaCounters(val counters: Map[String, Media] = Map.empty) {

  def get(key: String):Option[Media] = counters.get(key)

  def add(key: String, count: Int, totalBytes: Int):MediaCounters = {
    val m = get(key) match {
      case Some(m) => Media(m.count + count, m.totalBytes + totalBytes)
      case None => Media(count, totalBytes)
    }
    MediaCounters(counters.updated(key, m))
  }

  override def toString = counters.toString.replaceAll("""Map\(""", getClass.getSimpleName + "(")

}

case class Media(count: Int, totalBytes:Int)

object MediaCounters {

  def apply() = new MediaCounters(Map.empty)

}
