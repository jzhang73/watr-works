package edu.umass.cs.iesl.watr
package textgrid

import TypeTags._
import textboxing.{TextBoxing => TB}, TB._
import org.scalatest._
import watrmarks.Label
import utils.GraphPaper
import utils.ScalazTreeImplicits._

trait TextGraphSpec extends FreeSpec with Matchers with TextGraphConstruction {

  import geometry.GeometryTestUtils

  val Authors = Label.auto
  val Author = Label.auto
  val FirstName = Label.auto
  val MiddleName = Label.auto
  val LastName = Label.auto
  val Journal = Label.auto
  val RefMarker = Label.auto
  val RefNumber = Label.auto

  def infobox(heading: String, b: TB.Box): Unit = {
    info(heading)
    info("\n" + indent(4, b).toString() + "\n")
  }

  val stableId = DocumentID("SampleDocument")

  val sampleDoc = "abc\ndef\nghi"

  def makeSample(): TextGraphJvm = {
    val textGraph = TextGraphJvm.create(stableId)
    val cells = stringToTextGraphCells(stableId, sampleDoc, PageNum(3))

    cells.foreach { row =>
      textGraph.appendRow(row)
    }

    textGraph
  }

  def makeBishopClarkSample(): TextGraphJvm = {
    val labelSpans = List(
      ((0, 2), RefMarker, None ),
      ((0, 1), RefNumber, Some(RefMarker) ),
      ((2, 5), Authors  , None ),
      ((2, 2), Author   , Some(Authors) ),
      ((2, 1), LastName , Some(Author) ),
      ((3, 1), FirstName, Some(Author) ),
      ((5, 2), Author   , Some(Authors) ),
      ((7, 1), Journal  , None )
    )
    val rawText = {
      """|1
         |.
         |Bishop-Clark,
         |C
         |and
         |Wheeler,
         |D;
         |S.Eng. P-Hall
         |""".stripMargin
    }
    val textGraph = TextGraphJvm.create(stableId)
    val cells = stringToTextGraphCells(stableId, rawText, PageNum(0))

    cells.foreach { row =>
      textGraph.appendRow(row)
    }

    labelSpans.foreach { case ((rowStart, len), label, parent)=>
      parent match {
        case Some(p) => textGraph.addLabel(rowStart, len, label, p)
        case None => textGraph.addLabel(rowStart, len, label)
      }
    }


    textGraph
  }

  import _root_.io.circe
  import circe._
  val JsonPrettyPrinter = circe.Printer(
    preserveOrder = true,
    dropNullValues = false,
    indent = " "*4,
    lbraceRight = "",
    rbraceLeft = "\n",
    lbracketRight = "",
    rbracketLeft = "",
    lrbracketsEmpty = "",
    arrayCommaRight = " ",
    objectCommaRight = "\n",
    colonLeft = " ",
    colonRight = " "
  )
}

class TextGraphTests extends TextGraphSpec {



  "construct basic graph" in {
    // val textGraph = makeSample()

    // assert(textGraph.toText() == sampleDoc) // stripping leading spaces?
  }

  import TextGraphShape._

  // "apply nested labels" in {
  //   val textGraph = makeSample()

  //   val l0 = textGraph.addLabel(0, 1, Label(s"L0"))
  //   println(s"Added (0): ${l0}")

  //   for {
  //     i <- 1 until 4
  //   } {
  //     val l1 = textGraph.addLabel(0, 1, Label(s"L${i}"), Label(s"L${i-1}"))
  //     println(s"Added (${i}): ${l1}")
  //   }


  //   val labelTrees = textGraph.findLabelTrees(textGraph.graphArea())
  //   labelTrees.foreach { tree =>
  //     println(s"Tree==== ")
  //     println(tree.drawBox)
  //   }

  // }



  "apply labels" in {

  }

  "convert region-based labels to sequence-based labels" in {
    val bishopClarkGraph = makeBishopClarkSample()

    val graphPaper = TextGraphJvm.textGraphToGraphPaper(bishopClarkGraph)
    infobox("bishopClarkGraph", graphPaper.asMonocolorString())

    val labelTrees = bishopClarkGraph.findLabelTrees(bishopClarkGraph.graphArea())
    labelTrees.foreach { tree =>
      infobox("==Tree==", tree.drawBox)
    }

    val matrixContent = bishopClarkGraph.getMatrixContent()
    matrixContent.map { matrixRows =>

      val rowIntervals = matrixRows.rows.map { matrixRow =>
        val rowNum = matrixRow.area.origin.y
        val rowBegin = matrixRow.area.origin.x
        val rowLen = matrixRow.area.width
        (rowBegin, rowLen)
      }

      labelTrees.map { tree =>
        tree.map { labelShape =>
          labelShape.graphBox()
        }
      }



    }

    //


  }

  // val thingCount = 10

  // val things = unlabeledThings(thingCount)

  // things.addBioLabel(Journal)
  // things.addBioLabel(Author)



  // return every labeled span of text within graph

  // it should "support alphabet soup regions" in {}
  // it should "split/join lines" in {}


  // info("behavior of label sequence labeling/unlabeling")
  // "application of labels" in {
  // info("removing labels")
  // "it should find the span of cells that have a particular label" in {
  // "it should find the span of cells that have identical labeling (including unlabeled spans)" in {


}
