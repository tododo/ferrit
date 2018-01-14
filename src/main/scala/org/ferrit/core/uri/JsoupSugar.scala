package org.ferrit.core.uri

import org.jsoup.nodes.Element

import scala.collection.JavaConverters._


object JsoupSugar {

  import scala.language.implicitConversions
  
  /**
   * Converts a Jsoup Elements to Seq[Element]
   * The implementation uses an Iterable because Elements is an Iterable.
   *
   * Usage: org.ferrit.core.util.JsoupSugar.elementsToSeq
   *
   * This then allows for invocations like this:
   *
   * <ul>
   *   <li>doc.select("selector").toSeq  ==> to get Seq[Element]</li>
   *   <li>doc.select("selector").headOption  ==> to get Option[Element]</li>
   *   <li>doc.select("selector").nonEmpty  ==> better than !elements.isEmpty</li>
   * </ul>
   */  
  implicit def elementsToSeq(elms: java.lang.Iterable[Element]):Seq[Element] = 
    elms.asScala.toSeq

}