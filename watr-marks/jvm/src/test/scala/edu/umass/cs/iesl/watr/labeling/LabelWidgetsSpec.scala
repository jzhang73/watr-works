package edu.umass.cs.iesl.watr
package labeling


import geometry._
// import geometry.syntax._
// import textreflow.data._
// import labeling.data._

import TypeTags._
import corpora._
import LabelWidgets._
import LabelWidgetLayoutHelpers._

class LabelWidgetsSpec extends LabelWidgetTestUtil { // FlatSpec with Matchers with CorpusTestingUtil with LabelWidgetLayout {
  def createEmptyDocumentCorpus(): DocumentCorpus = new MemDocstore

  initEmpty()

  val docs = List(
    List("01\n23")
      // List("01\n23", "45\n67")
    // List("01\n23", "45\n67")
  )

  for { (doc, i) <- docs.zipWithIndex } {
    addDocument(DocumentID(s"doc#${i}"), doc)
  }

  behavior of "label widgets"

  // Our invariant condition states:
  //  After running any layout algorithm on a given set of layout widgets,
  //   the intersection of a bbox with any widget, then transformed by that widgets
  //   positioning transform (inverse), gets us a rectangle in the coordinate space
  //   of the target region contained in that widget



  it should "include labeled targets" in {
    val pageRegion = PageRegion(PageID(1), LTBounds(5d, 4d, 3d, 2d) )
    val pageRegion2 = PageRegion(PageID(1), LTBounds(5.1d, 4.1d, 3.1d, 2.1d) )

    val subRegion = PageRegion(PageID(1), LTBounds(5.5d, 4.5d, 0.5d, 1.5d) )
    val subRegion2 = PageRegion(PageID(1), LTBounds(5.6d, 3.5d, 0.1d, 2.5d) )

    val widget0 = col(
      targetOverlay(pageRegion, List(labeledTarget(subRegion), labeledTarget(subRegion2))),
      targetOverlay(pageRegion2, List(labeledTarget(subRegion), labeledTarget(subRegion2)))
    )
    val widgetLayout = layoutWidgetPositions(widget0)

    widgetLayout.positioning
      .foreach{ pos =>
        println(s"${pos}")
        val clipTo = LTBounds(1d, 0d, 1d, 100d)
        val clipped = clipPageRegionFromWidgetSpace(pos, clipTo)
        println(s"  clipped: ${clipped}")

      }
  }

  it should "correctly position overlays" in  {}


  // it should "create columns" in {
  //   val divs = 3
  //   val pageRegions = generatePageRegions(divs)


  //   val widget0 = col(
  //     pageRegions.map(targetOverlay(_, List())):_*
  //   )
  //   val widgetLayout = layoutWidgetPositions(widget0)

  //   // .sortBy({ p => (p.widgetBounds.width) })

  //   widgetLayout.positioning
  //     .foreach{ pos =>
  //       println(s"${pos}")
  //       // val borigin = pos.widgetBounds.moveToOrigin()
  //       // val bwidget = borigin.translate(pos.translation)
  //       // println(s"   borigin: ${borigin}")
  //       // println(s"   bwidget: ${bwidget}")
  //     }

  // }



  it should "create rows" in {
  }

  it should "include inserted text (as textbox)" in {
  }

  it should "include reflows" in {
  }

  it should "include padding" in {
  }

}
