package org.ferrit.core.util

import org.scalatest.{FlatSpec, Matchers}


class TestCounters extends FlatSpec with Matchers {

  behavior of "TestCounters"

  it should "increment counters" in {
    
    val c = new Counters
    c.get("apples") should equal (0)
    c.get("pears") should equal (0)
    
    val c2 = c.increment("apples")
    c2.get("apples") should equal (1)
    c2.get("pears") should equal (0)
  }

}