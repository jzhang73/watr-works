package edu.umass.cs.iesl.watr
package textgrid

import scala.collection.mutable
import rtrees._
import geometry._
import geometry.syntax._
import geometry.PageComponentImplicits._
import textboxing.{TextBoxing => TB}, TB._
import TypeTags._
import utils.GraphPaper

import utils.{Cursor, Cursors, Window}
import utils.SlicingAndDicing._
import utils.DoOrDieHandlers._

import watrmarks.Label
import scalaz.{@@ => _, _} //, Scalaz._

import _root_.io.circe
import circe._
import circe.literal._
import circe.syntax._
import circe.generic._
import circe.generic.auto._
import circe.generic.semiauto._


// @JsonCodec
// sealed trait Attr

// object Attr {

//   @JsonCodec
//   case class Glyph(
//     glyph: TextGraph.GridCell
//   ) extends Attr

//   case object Empty extends Attr {
//     implicit val EmptyDecoder: Decoder[Empty.type] = deriveDecoder
//     implicit val EmptyEncoder: Encoder[Empty.type] = deriveEncoder
//   }

// }


@JsonCodec
sealed trait TextGraphShape extends LabeledShape[GeometricFigure, Option[TextGraph.GridCell]]

object TextGraphShape {
  import GeometryCodecs._
  import LabeledShape._
  import TextGraph.GridCell._

  type Attr = Option[TextGraph.GridCell]

  @JsonCodec
  case class GlyphShape(
    shape: LTBounds,
    id: Int@@ShapeID,
    attr: Option[TextGraph.GridCell],
    labels: Set[Label] = Set()
  ) extends TextGraphShape {
    def addLabels(l: Label*): GlyphShape = copy(
      labels = this.labels ++ l.toSet
    )
  }

  @JsonCodec
  case class LabelShape(
    shape: LTBounds,
    id: Int@@ShapeID,
    parent: Option[LabelShape],
    labels: Set[Label] = Set()
  ) extends TextGraphShape {
    def attr: Option[TextGraph.GridCell] = None

    def addLabels(l: Label*): LabelShape = copy(
      labels = this.labels ++ l.toSet
    )
  }

  implicit val ShowLabelShape: Show[LabelShape] = Show.shows[LabelShape]{ shape =>
    s"<shape#${shape.id}>"
  }

}

sealed trait MatrixArea {
  def area: GraphPaper.Box
}

object MatrixArea {

  case class Row(
    area: GraphPaper.Box,
    glyphs: Seq[TextGraphShape.GlyphShape]
  ) extends MatrixArea

  case class Rows(
    area: GraphPaper.Box,
    rows: Seq[Row]
  ) extends MatrixArea

}


trait TextGraph { self =>

  import TextGraph._
  import TextGraphShape._

  def stableId: String@@DocumentID

  def toText(): String = {
    getRows().map{ row =>
      row.map(_.char).mkString
    }.mkString("\n")
  }


  def splitOneLeafLabelPerLine(): TextGraph = {
    ???
  }

  def pageBounds(): Seq[PageRegion] = {
    ???
  }

  def split(row: Int, col: Int): Boolean = {
    ???
  }

  def slurp(row: Int): Boolean = {
    ???
  }

  def appendRow(row: Seq[GridCell]): Unit
  def getRows(): Seq[Seq[GridCell]]

  def getMatrixContent(fromRow: Int=0, len: Int=Int.MaxValue): Option[MatrixArea.Rows]
  // def getContent(): Option[MatrixArea.Rows]

  def addLabel(row: Int, len: Int, label: Label, parent: Label): Option[LabelShape]

  def addLabel(row: Int, len: Int, label: Label): Option[LabelShape]

  def findLabelTrees(area: GraphPaper.Box): Seq[Tree[LabelShape]]


}

object TextGraph {

  sealed trait GridCell {
    def char: Char
  }

  def mbrRegionFunc(h: PageItem, tail: Seq[PageItem]): PageRegion = {
    (h +: tail).map(_.pageRegion).reduce { _ union _ }
  }

  case class GlyphCell(
    char: Char,
    headItem: PageItem,
    tailItems: Seq[PageItem] = Seq(),
  ) extends GridCell {
    val pageRegion: PageRegion = mbrRegionFunc(headItem, tailItems)
  }


  case class InsertCell(
    char: Char
  ) extends GridCell

  // implicit val InsertCellEncoder: Encoder[InsertCell] = Encoder[Char].contramap(_.char)
  // implicit val InsertCellDecoder: Decoder[InsertCell] = Decoder[Char].map(InsertCell(_))


  // case object SpaceCell extends GridCell {
  //   def char: Char = ' '

  //   implicit val SpaceCellDecoder: Decoder[SpaceCell.type] = deriveDecoder
  //   implicit val SpaceCellEncoder: Encoder[SpaceCell.type] = deriveEncoder

  // }

  // Needed for circe Codec annotations to work properly
  object GridCell {
    implicit val GraphCellEncoder: Encoder[GridCell] = Encoder.instance[GridCell]{ _ match {
      case cell@ TextGraph.GlyphCell(char, headItem, tailItems) =>

        val items = (headItem +: tailItems).map{ pageItem =>
          val page = pageItem.pageRegion.page
          val pageNum = page.pageNum

          val LTBounds.IntReps(l, t, w, h) = pageItem.bbox
          Json.arr(
            Json.fromString(char.toString()),
            Json.fromInt(pageNum.unwrap),
            Json.arr(Json.fromInt(l), Json.fromInt(t), Json.fromInt(w), Json.fromInt(h))
          )
        }

        Json.obj(
          "g" := items
        )

      case cell@ TextGraph.InsertCell(char)     =>
        Json.obj(
          "i" := List(char.toString())
        )

    }}

    private def decodeGlyphCells: Decoder[Seq[(String, Int, (Int, Int, Int, Int))]] = Decoder.instance { c =>
      c.as[(Seq[(String, Int, (Int, Int, Int, Int))])]
    }

    private def decodeGlyphCell: Decoder[(String, Int, (Int, Int, Int, Int))] = Decoder.instance { c =>
      c.as[(String, Int, (Int, Int, Int, Int))]
    }
    implicit def decodeGraphCell: Decoder[TextGraph.GridCell] = Decoder.instance { c =>

      c.keys.map(_.toVector) match {
        case Some(Vector("g")) =>

          val res = c.downField("g").focus.map{ json =>
            val dec = decodeGlyphCells.decodeJson(json).map { cells =>
              val atoms = cells.map{ case(char, page, (l, t, w, h)) =>
                val bbox = LTBounds.IntReps(l, t, w, h)
                PageItem.CharAtom(
                  CharID(-1),
                  PageRegion(
                    StablePage(
                      stableId,
                      PageNum(page)
                    ),
                    bbox
                  ),
                  char.toString()
                )
              }
              TextGraph.GlyphCell(atoms.head.char.head, atoms.head, atoms.tail)
            }

            dec.fold(decFail => {
              Left(decFail)
            }, succ => {
              Right(succ)
            })
          }

          res.getOrElse { Left(DecodingFailure("page item grid cell decoding error", List.empty)) }


        case Some(Vector("i")) =>
          val res = c.downField("i").focus.map{ json =>
            decodeGlyphCell.decodeJson(json)
              .map { case(char, page, (l, t, w, h)) =>
                val bbox = LTBounds.IntReps(l, t, w, h)

                val insertAt = PageRegion(
                  StablePage(
                    stableId,
                    PageNum(page)
                  ),
                  bbox
                )

                TextGraph.InsertCell(char.head)
              }
          }

          res.getOrElse { Left(DecodingFailure("insert grid cell decoding error", List.empty)) }

        case x => Left(DecodingFailure(s"unknown grid cell type ${x}", List.empty))
      }

    }

  }

}
