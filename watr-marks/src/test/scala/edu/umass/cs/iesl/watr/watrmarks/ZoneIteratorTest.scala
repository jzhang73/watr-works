package edu.umass.cs.iesl.watr
package watrmarks

import org.scalatest._

class ZoneIteratorTest extends FlatSpec {

  behavior of "zone iterator"

  def is = getClass().getResourceAsStream("/spatial/0575.pdf.cermine-zones.json")
  def loadPageIterator = ZoneIterator.load(is).get

  it should "load info from json" in new SpatialJsonFormat {
    ZoneIterator.load(is).isDefined
  }


  it should "read in zone desc. from json"  in {
    val pageIter = loadPageIterator
    val LineLabel = new Label("bx", "line")
    val lines = pageIter.getZones(LineLabel)
    lines.foreach { line =>
      val bboxes = line.getBoundingBoxes.map(_.bbox.prettyPrint).mkString(", ")
      println(s"line = ${line.getText}   bbox = ${bboxes}")

      line.getTokens.map {case (tokenZone, tokenLabel) =>
        println(s"${tokenLabel}:  ${tokenZone.bboxes.map(_.bbox.prettyPrint)}")
      }
    }

  }
}
