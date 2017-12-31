package edu.umass.cs.iesl.watr
package textgrid

import scala.collection.mutable
import watrmarks._
import geometry._
import geometry.syntax._
import geometry.PageComponentImplicits._
import textboxing.{TextBoxing => TB}, TB._
import TypeTags._

import utils.SlicingAndDicing._
import scala.scalajs.js.annotation._


import _root_.io.circe
import circe._
import circe.literal._

sealed trait FontInfo

case object NoFonts extends FontInfo

@JSExportAll
trait TextGrid {
  import TextGrid._

  def stableId: String@@DocumentID

  def rows: Seq[Row]

  def toText(): String = {
    rows.map(_.toText).mkString("\n")
  }

  def trimRights(): TextGrid = {
    TextGrid.fromRows(
      stableId,
      rows.map(_.trimRight())
    )
  }
  def padRights(): TextGrid = {
    TextGrid.fromRows(
      stableId,
      rows.map(_.padRight)
    )
  }

  def splitOneLeafLabelPerLine(): TextGrid = {
    val splitRows = rows.flatMap { row =>
      row.splitOnLeafLabels()
    }

    TextGrid.fromRows(stableId, splitRows)
  }

  def indexedCells(): Seq[(GridCell, Int, Int)] = {
    for {
      (row, rowNum) <- rows.zipWithIndex
      (cell, colNum) <- row.cells.zipWithIndex
    } yield { (cell, rowNum, colNum) }
  }

  def split(row: Int, col: Int): Option[TextGrid] = {
    if (0 <= row && row < rows.length) {
      rows(row).split(col).map {
        case (row1, row2) =>
          val (pre, post) = rows.splitAt(row)
          val end = row1 +: row2 +: (post.drop(1))
          val newRows = pre ++ end
          TextGrid.fromRows(stableId, newRows)
      }
    } else None
  }

  def slurp(row: Int): Option[TextGrid] = {
    if (0 <= row && row < rows.length-1) {
      val (pre, post) = rows.splitAt(row+1)
      val r1 = pre.last
      val r2 = post.head

      val r12 = r1.append(r2)
      val newRows = pre.dropRight(1) ++ (r12 +: post.drop(1))
      val newGrid = TextGrid.fromRows(stableId, newRows)

      // rows can only be joined if they share the same label stack
      val maybeNewGrid = r12.cells.headOption.map{ c0 =>
        val headCellPins  = c0.pins
        val pinlen = headCellPins.length
        val equalPinStackSize = r12.cells.forall(_.pins.length == pinlen)
        if (equalPinStackSize) {
          if (pinlen==0) Some(newGrid) else {
            val headTopPin = c0.topPin().get
            val validJoin = headTopPin.isBegin || headTopPin.isInside && {
              val allInsideButLast = r12.cells.tail.dropRight(1).forall{ c =>
                val ctop = c.topPin().get
                ctop.isInside
              }
              val lastpin = r12.cells.last.topPin().get
              val endsWithInsideOrLast = lastpin.isInside || lastpin.isLast

              allInsideButLast && endsWithInsideOrLast
            }

            if (validJoin) Some(newGrid) else None
          }
        } else None
      }
      maybeNewGrid.flatten
    } else None
  }

  def buildOutput() = new TextOutputBuilder(this)

  def pageBounds(): Seq[PageRegion] = {

    val allBounds = rows.flatMap{ row => row.pageBounds() }

    val regionsByPages = allBounds.groupBy(_.page.pageNum)
    regionsByPages.map { case (pageNum, pageRegions) =>
      val headRegion = pageRegions.head.page
      val pageBbox = pageRegions.map(_.bbox).reduce(_ union _)
      PageRegion(
        headRegion,
        pageBbox
      )
    }.toList
  }

  def toJson(): Json = {
    new TextOutputBuilder(this).gridToJson()
  }
}

@JSExportTopLevel("watr.textgrid.TextGrid.Companion")
@JSExportAll
object TextGrid {
  type SetType[A] = mutable.ArrayStack[A]
  type PinSet = SetType[BioPin]


  def fromJsonStr(jsStr: String): TextGrid = {
    circe.parser.parse(jsStr).fold(
      fail => sys.error(s"could not decode TextGrid: ${fail}: ${jsStr}"),
      succ => fromJson(succ)
    )

  }
  def fromJson(js: Json): TextGrid = {
    val cursor = js.hcursor

    cursor.downField("stableId").as[String].fold(fail => {
      sys.error(s" could not decode textgrid: ${fail}")
    }, stableId => {
      val codecs =  new AccumulatingTextGridCodecs(DocumentID(stableId))
      codecs.decodeGrid(js)
    })

  }

  @JSExportAll
  sealed trait LabelTarget {
    val pins: PinSet = mutable.ArrayStack[BioPin]()

    def labels: SetType[Label] = pins.map(_.label)

    def addPin(p: BioPin): Unit = pins.push(p)

    def addLabel(l: Label): Unit = addPin(l.U)

    def removeLabel(l: Label): Unit = {
      if (pins.contains(l)) {
        while(pins.top.label != l) {
          pins.pop()
        }
      }
    }

    def hasLabel(l: Label): Boolean = {
      pins.exists(_.label == l)
    }

    def hasPin(p: BioPin): Boolean = {
      pins.contains(p)
    }

    def topLabel(): Option[Label] = {
      if (pins.nonEmpty) {
        Some(pins.top.label)
      } else None
    }

    def topPin(): Option[BioPin] = {
      if (pins.nonEmpty) {
        Some(pins.top)
      } else None
    }

    def showPinsVert(): Box = {
      vjoins(left, pins.toList.reverse.map(_.pinChar.toString.box))
    }
  }

  @JSExportAll
  sealed trait GridCell extends LabelTarget {
    def pageRegion: PageRegion

    def char: Char

    def fonts: FontInfo = NoFonts

    def createInsert(ch: Char): InsertCell = InsertCell(ch, this.pageRegion)

    def showCell(): Box = {
      vjoin(left, char.toString(), showPinsVert())
    }
  }

  def mbrRegionFunc(h: PageItem, tail: Seq[PageItem]): PageRegion = {
    (h +: tail).map(_.pageRegion).reduce { _ union _ }
  }

  case class PageItemCell(
    headItem: PageItem,
    tailItems: Seq[PageItem] = Seq(),
    override val char: Char,
    regionFunc: (PageItem, Seq[PageItem]) => PageRegion = mbrRegionFunc(_, _)
  ) extends GridCell {
    override val pageRegion: PageRegion = regionFunc(headItem, tailItems)
  }


  case class InsertCell(
    char: Char,
    insertAt: PageRegion
  ) extends GridCell {
    override val pageRegion: PageRegion = insertAt
  }


  @JSExportAll
  trait Row extends LabelTarget {

    def cells: Seq[GridCell]

    private def isSpace(gc: GridCell) = gc.char == ' '
    private def trim(cs: Seq[GridCell]) = trimRight(cs.dropWhile(isSpace(_)))
    private def trimRight(cs: Seq[GridCell]) = cs.reverse.dropWhile(isSpace(_)).reverse

    def trimRight(): Row = {
      Row.fromCells(trimRight(cells))
    }

    def padRight(): Row = {
      cells.lastOption.map{ c =>
        Row.fromCells( cells :+ c.createInsert(' '))
      } getOrElse { this }
    }

    def split(col: Int): Option[(Row, Row)] = {
      if (0 < col && col < cells.length) {
        val (c1, c2) = cells.splitAt(col)

        Some((
          Row.fromCells(c1), Row.fromCells(c2)
        ))
      } else None
    }

    def splitOnLeafLabels(): Seq[Row] = {
      val groups = cells.groupByPairs((a, b) => {
        (a.topPin(), b.topPin()) match {
          case (Some(pin1), Some(pin2)) =>
            val isBIL = pin1.isBegin && (pin2.isInside || pin2.isLast)
            val isIIL = pin1.isInside && (pin2.isInside || pin2.isLast)
            val sameLabel = pin1.label == pin2.label
            sameLabel && (isBIL || isIIL)

          case (None, None) => true
          case _ => false
        }})


      groups.map{ group =>
        val r = Row.fromCells(group)
        r.cells.head.topPin().foreach { pin =>
          r.addLabel(pin.label)
        }
        r
      }
    }

    def append(row: Row): Row = {
      Row.fromCells(cells ++ row.cells)
    }

    def pageBounds(): Seq[PageRegion] = {
      val regionsByPages = cells.groupBy(_.pageRegion.page.pageNum)

      regionsByPages.map { case (pageNum, pageRegions) =>
        val headRegion = pageRegions.head.pageRegion
        val pageBbox = pageRegions.map(_.pageRegion.bbox).reduce(_ union _)
        PageRegion(
          headRegion.page,
          pageBbox
        )
      }.toList
    }

    def toCursor(): Option[GridCursor] = {
      GridCursor.init(cells.toList.toZipper)
    }

    def foreach(f: GridCell => Unit): Unit  = {
      cells.foreach(f(_))
    }


    def toText(): String = {
      cells.map(_.char).mkString("")
    }

    def showRow(): Box = {
      hcat(top, cells.map(_.showCell()))
    }

    protected[textgrid] def serialize(codecs: AccumulatingTextGridCodecs): Unit = {
      codecs.encodeRow(this)
    }
  }

  abstract class MutableRow extends Row {
    override val cells: mutable.ArrayBuffer[GridCell] = mutable.ArrayBuffer()
  }


  object Row {
    def fromCells(init: Seq[GridCell]): Row = new MutableRow {
      cells.appendAll(init)
    }
  }

  abstract class MutableTextGrid extends TextGrid {
    override val rows: mutable.ArrayBuffer[Row] = mutable.ArrayBuffer()
  }

  def fromRows(id: String@@DocumentID, init: Seq[Row]): TextGrid = new MutableTextGrid {
    override val stableId = id
    rows.appendAll(init)
  }

  def fromCells(stableId: String@@DocumentID, init: Seq[GridCell]): TextGrid =
    fromRows(stableId, Seq(Row.fromCells(init)))

}
