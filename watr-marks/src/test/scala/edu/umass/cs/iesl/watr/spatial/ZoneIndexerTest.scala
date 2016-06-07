package edu.umass.cs.iesl.watr
package spatial

import org.scalatest._


class ZoneIndexerTest extends FlatSpec {
  behavior of "zone indexing"
  val LB = watrmarks.StandardLabels

  def is = getClass().getResourceAsStream("/spatial/0575.pdf.cermine-zones.json")
  def loadPageIterator = ZoneIterator.load(is).get

  it should "load info from json" in new SpatialJsonFormat {
    ZoneIterator.load(is).isDefined
  }


  it should "read in zone desc. from json"  in {
    val pageIter = loadPageIterator
    val lines = pageIter.getZones(LB.Line)
    // val lines = pageIter.getZones(LB.Zone)
    lines.foreach { line =>
      val bboxes = line.getBoundingBoxes.map(_.bbox.prettyPrint).mkString(", ")
      println(s"zone = ${line.getText}   bbox = ${bboxes}")

      line.getTokens.map {case (tokenZone, tokenLabel) =>
        println(s"${tokenLabel}:  ${tokenZone.bboxes.map(_.bbox.prettyPrint)}")
      }
    }

  }
}
