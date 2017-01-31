package edu.umass.cs.iesl.watr
package display

import org.scalatest._

import geometry._
import textreflow._
import textreflow.data._
import display.data._
import utils.EnrichNumerics._
import TypeTags._

// import watrmarks.{StandardLabels => LB}
class LabelWidgetsSpec extends FlatSpec with Matchers with PlainTextReflow with LabelWidgetBasics {

  behavior of "label widgets"

  val bbox = LTBounds(20d, 12d, 10d, 10d)
  val tr = TargetRegion(RegionID(0), DocumentID("doc-id-0"), PageID(23), bbox)

  def stringToReflow(s: String): TextReflow =
    stringToTextReflow(s)(DocumentID("doc-id-0"), PageID(23))

  def reg0: TargetRegion = tr
  def sel0: TargetRegion = tr
  def sel1: TargetRegion = tr

  it should "specify widget layout" in {

    val reflow0 = stringToReflow("lime _{^{ﬂ}a}vor")
    def range0: RangeInt = RangeInt(1, 3)

    val w1 = LW.targetOverlay(reg0, List(
      LW.labeledTarget(sel0),
      LW.labeledTarget(sel1)
    ))

    val w2 = LW.col(
      LW.reflow(reflow0),
      LW.reflow(reflow0)
    )

    val row1 = LW.row(w1, w2)

    val panel1 =  LW.panel(row1)

    println("layout")
    println(
      prettyPrintLabelWidget(panel1)
    )

    println("positioned")
    val abs0 = absPositionLabelWidget(panel1)
    println(
      prettyPrintLabelWidget(abs0)
    )

    // // approve all selections within layout regions:
    // button(approveSelections(w2))
    // button(approveSelections(row1))
  }


  // it should "use heatmaps" in {
  //   val hm = LW.heatmap(
  //     LW.targetImage(reg0), List(
  //       LW.labeledTarget(tr, LB.Authors, 10.0d),
  //       LW.labeledTarget(tr, LB.Authors, 3.3d),
  //       LW.labeledTarget(tr, LB.Authors, 55.0d)
  //     )
  //   )
  //   println("positioned")
  //   val abs0 = absPositionLabelWidget(hm)
  //   println(
  //     prettyPrintLabelWidget(abs0)
  //   )
  // }
}