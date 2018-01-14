package org.ferrit.dao.cassandra

import java.time.{LocalDate, LocalDateTime, Month, ZoneId}
import java.util.Date

import com.datastax.driver.core.{ResultSet, Row}
import com.datastax.driver.core.{LocalDate => CassLocalDate}

object CassandraDAO {

  import scala.language.implicitConversions

  implicit def toDate(localDateTime: LocalDateTime): Date = {
    if (localDateTime != null)
      Date.from(localDateTime.atZone(ZoneId.systemDefault()).toInstant)
    else
      null
  }

  implicit def toDateOption(localDateTime: Option[LocalDateTime]): Date = {
    if (localDateTime.nonEmpty)
      Date.from(localDateTime.get.atZone(ZoneId.systemDefault()).toInstant)
    else
      null
  }

  implicit def toCassandraLocalDate(date: LocalDate): CassLocalDate = {
    if (date != null)
      CassLocalDate.fromYearMonthDay(date.getYear, date.getMonthValue, date.getDayOfMonth)
    else
      null
  }

  implicit def toDate(localDate: CassLocalDate): LocalDate = {
    if (localDate != null)
      LocalDate.of(localDate.getYear, localDate.getMonth, localDate.getDay)
    else
      null
  }


  implicit def toDateTime(date: Date): LocalDateTime = {
    if (date != null) LocalDateTime.ofInstant(date.toInstant, ZoneId.systemDefault()) else null
  }

  implicit def toDateTimeOption(date: Date): Option[LocalDateTime] = {
    if (date != null) Some(LocalDateTime.ofInstant(date.toInstant, ZoneId.systemDefault())) else None
  }

  implicit def fromStringOption(stringOpt: Option[String]): String = if (stringOpt.nonEmpty) stringOpt.get else null

  implicit def toStringOption(string: String): Option[String] = if (string != null) Some(string) else None

  def mapOne[T](rs: ResultSet)(fn: Row => T): Option[T] = {
    val iter = rs.iterator()
    if (iter.hasNext) Some(fn(iter.next()))
    else None
  }

  def mapAll[T](rs: ResultSet)(fn: Row => T): Seq[T] = {
    val iter = rs.iterator()
    val items = new scala.collection.mutable.ListBuffer[T]
    while (iter.hasNext()) {
      items.append(fn(iter.next()))
    }
    items.toList
  }

}
