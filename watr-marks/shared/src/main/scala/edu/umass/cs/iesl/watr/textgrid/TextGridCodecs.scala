package edu.umass.cs.iesl.watr
package textgrid

import scala.collection.mutable
import geometry._
import TypeTags._

import _root_.io.circe
import circe._
import circe.syntax._
import circe.literal._

import utils.DoOrDieHandlers._
import watrmarks._
import scala.collection.mutable


// case class TextGridSerialization(
//   lineMap: Map[Int, (Json, String)],
//   labelMap: Map[Label, Int],
//   cellLabelBuffer: List[List[String]]
// )

case class LabelSpan(
  label: Label,
  begin: Int,
  length: Int
)

case class LabelingTree(
  labelSpan: LabelSpan,
  children: Seq[LabelingTree]
)

object LabelTreeCodecs {
  import TextGridLabelWidget._
  import TextGridFunctions._

  def encodeBioLabels(cells: Seq[TextGrid.GridCell]): Json = {
    val labelTree = gridCellsToLabelTree(cells)
    val labelSpanTree = labelTreeToSpanTree(labelTree)
    spanTreeToJson(labelSpanTree)
  }


  def decodeBioLabels(jsonRep: Json): Seq[TextGrid.PinSet] = {
    val labelingTrees = jsonRep.decodeOrDie[Seq[LabelingTree]]()

    val dummyPageRegion = PageRegion(StablePage(DocumentID("docX"), PageNum(0)), LTBounds.empty)
    def emptyInlineBIO(len: Int): Seq[TextGrid.GridCell] = {
      Array.fill[TextGrid.GridCell](len){
        TextGrid.InsertCell('a', dummyPageRegion)
      }
    }

    def maxLen(lt: LabelingTree): Int = {
      val begin = lt.labelSpan.begin
      val len = lt.labelSpan.length
      val total = begin + len
      (total +: lt.children.map(maxLen(_))).max
    }

    val totalLen = labelingTrees.map(maxLen(_)).max
    val inlineBio = emptyInlineBIO(totalLen)

    def loop(lt: LabelingTree): Unit = {
      val label = lt.labelSpan.label
      val begin = lt.labelSpan.begin
      val len = lt.labelSpan.length

      for {
        gridCursor <- GridCursor.init(inlineBio)
        c3         <- gridCursor.move(begin)
      } yield {
        val window = c3.toWindow()
        val w2 = window.widen(len-1)
        w2.addLabel(label)
        w2.closeWindow()
      }
      lt.children.foreach(loop(_))
    }

    labelingTrees.foreach(loop(_))

    inlineBio.map { _.pins }
  }

  implicit def decodeLabelSpan: Decoder[LabelSpan] = Decoder.decodeTuple3[Label, Int, Int]
    .map { t => LabelSpan(t._1, t._2, t._3) }

  implicit def encodeLabelSpan: Encoder[LabelSpan] = Encoder.encodeTuple3[Label, Int, Int]
    .contramap{ ls => (ls.label, ls.begin, ls.length) }

  implicit def decodeLabelingTree: Decoder[LabelingTree] = Decoder.decodeTuple2[LabelSpan, Seq[Json]]
    .map{ t =>
      LabelingTree(t._1, t._2.map(_.decodeOrDie[LabelingTree]()))
    }

  implicit def encodeLabelingTree: Encoder[LabelingTree] = Encoder.encodeTuple2[LabelSpan, Seq[Json]]
    .contramap{ lTree =>
      val childs = lTree.children.map{_.asJson}
      (lTree.labelSpan, childs)
    }

}

class TextOutputBuilder(textGrid: TextGrid) {

  def getSerialization(): AccumulatingTextGridCodecs = {
    val codecs = new AccumulatingTextGridCodecs(textGrid.stableId)

    textGrid.rows.zipWithIndex
      .foreach{ case (row, rowi) =>
        row.serialize(codecs)
      }
    codecs
  }

  def gridToJson(): Json = {
    val codec = getSerialization()
    val lineNums = codec.lineMap.keys.toList.sorted


    var totalOffset = 0
    val textAndLoci = lineNums.map { lineNum =>
      val text = codec.lineMap(lineNum)._2
      val loci = codec.lineMap(lineNum)._1

      val currOffset = totalOffset
      totalOffset += text.length()

      Json.obj(
        "offset" := currOffset,
        "text" := text,
        "loci" := loci,
      )
    }

    val bioLabelJson = LabelTreeCodecs.encodeBioLabels(
      textGrid.indexedCells().map(_._1)
    )

    // val cellLabels = codec.cellLabelBuffer
    // assume(cellLabels.length == totalOffset, "cell BIO labeling length != cell count")

    val labelDefs = Json.obj(
      // "labelMap" := codec.labelMap.map{case (k, v) => (v, k.fqn)},
      "cellLabels" := bioLabelJson
    )
    Json.obj(
      "stableId" := textGrid.stableId.unwrap,
      "rows" := textAndLoci,
      "labels" := labelDefs
    )
  }

}

protected class AccumulatingTextGridCodecs(stableId: String@@DocumentID) {

  val pageIdMap = mutable.Map[Int@@PageID, (String@@DocumentID, Int@@PageNum)]()
  val lineMap = mutable.Map[Int, (Json, String)]()
  // val labelMap = mutable.Map[Label, Int]()
  // val labelIdMap = mutable.Map[Int, Label]()
  // val cellLabelBuffer = mutable.ArrayBuffer[List[String]]()

  def decodeGrid(js: Json): TextGrid = {
    val cursor = js.hcursor
    // val serLabelMap = cursor.downField("labels").downField("labelMap").as[Map[Int, String]]
    // serLabelMap.fold(fail => {
    //   labelIdMap
    // }, succ => {
    //   labelIdMap ++= succ.toList.map{ case (k, v) => (k, Label(v)) }
    // })

    // val cellLabels = cursor.downField("labels").downField("cellLabels").as[List[List[String]]]
    // val decoded = LabelTreeCodecs.decode(cellLabels)
    // cellLabels.fold(fail => {}, succ => {
    //   cellLabelBuffer.appendAll(succ)
    // })

    val rowsM = cursor
      .downField("rows").values.map { jsVals =>
        jsVals.toVector.map { js =>
          val lociM = js.hcursor.downField("loci").as[Seq[TextGrid.GridCell]]
          lociM.fold(fail => {
            sys.error(s"could not decode textgrid loci:${fail}: js=${js}")
          }, succ => {
            TextGrid.Row.fromCells(succ)
          })
        }
      }

    val rows = rowsM.orDie(s"could not decode textgrid rows")

    val textGrid = TextGrid.fromRows(stableId,  rows)
    for {
      l <- cursor.downField("labels").success
      m <- l.downField("cellLabels").success
      focus <- m.focus
    } yield {
      val labels = LabelTreeCodecs.decodeBioLabels(focus)
      textGrid.indexedCells().map(_._1).zip(labels)
        .foreach { case (cell, labels) =>
          cell.pins ++= labels.reverse
        }
    }
    textGrid
  }

  private def nextLineNum: Int = if (lineMap.keySet.isEmpty) 0 else lineMap.keySet.max + 1

  private def decodeGlyphCells: Decoder[Seq[(String, Int, (Int, Int, Int, Int))]] = Decoder.instance { c =>
    c.as[(Seq[(String, Int, (Int, Int, Int, Int))])]
  }

  private def decodeGlyphCell: Decoder[(String, Int, (Int, Int, Int, Int))] = Decoder.instance { c =>
    c.as[(String, Int, (Int, Int, Int, Int))]
  }

  protected[textgrid] def encodeRow(row: TextGrid.Row): Unit = {
    val rowAsJson = row.cells.map(c => c.asJson).asJson
    val lineNum = nextLineNum
    lineMap.put(lineNum, (rowAsJson, row.toText))
  }


  // private def addBioPins(cell: TextGrid.GridCell): TextGrid.GridCell = {
  //   if (cellLabelBuffer.nonEmpty) {
  //     val cellPinRepr = cellLabelBuffer.remove(0)
  //     cellPinRepr.foreach { pinRep =>
  //       val Array(pinChar, labelId) = pinRep.split(":")
  //       val label = labelIdMap.get(labelId.toInt)
  //         .orDie(s"could not decode label ${labelId}")

  //       val pin = pinChar match {
  //         case "B" => label.B
  //         case "I" => label.I
  //         case "O" => label.O
  //         case "L" => label.L
  //         case "U" => label.U
  //         case _ =>
  //           sys.error(s"could not decode label pinChar ${pinChar}")
  //       }
  //       cell.addPin(pin)
  //     }
  //   }
  //   cell
  // }


  implicit def decodeGridCell: Decoder[TextGrid.GridCell] = Decoder.instance { c =>

    c.keys.map(_.toVector) match {
      case Some(Vector("g")) =>
        val res = c.downField("g").focus.map{ json =>
          val dec = decodeGlyphCells.decodeJson(json).map { cells =>
            val atoms = cells.map{ case(char, page, (l, t, w, h)) =>
              val bbox = LTBounds.IntReps(l, t, w, h)
              CharAtom(
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
            val cell = TextGrid.PageItemCell(atoms.head, atoms.tail, atoms.head.char.head)
            // addBioPins(cell)
            cell
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

              val cell = TextGrid.InsertCell(char.head, insertAt: PageRegion)
              // addBioPins(cell)
              cell
            }
        }

        res.getOrElse { Left(DecodingFailure("insert grid cell decoding error", List.empty)) }

      case x => Left(DecodingFailure(s"unknown grid cell type ${x}", List.empty))
    }

  }

  val labelIdGen = utils.IdGenerator[LabelID]()

  implicit def GridCellEncoder: Encoder[TextGrid.GridCell] = Encoder.instance[TextGrid.GridCell]{ _ match {
    case cell@ TextGrid.PageItemCell(headItem, tailItems, char, _) =>

      // val cellLabels = cell.pins.reverse.toList.map{ pin =>
      //   val labelId = labelMap.getOrElseUpdate(pin.label, labelIdGen.nextId.unwrap)
      //   s"${pin.pinChar}:${labelId}"
      // }
      // cellLabelBuffer.append(cellLabels)

      val items = (headItem +: tailItems).map{ pageItem =>
        val page = pageItem.pageRegion.page
        val pageNum = page.pageNum


        if (!pageIdMap.contains(page.pageId)) {
          pageIdMap.put(page.pageId, (page.stableId, page.pageNum))
        }

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

    case cell@ TextGrid.InsertCell(char, insertAt)     =>
      // val cellLabels = cell.pins.reverse.toList.map{ pin =>
      //   val labelId = labelMap.getOrElseUpdate(pin.label, labelIdGen.nextId.unwrap)
      //   s"${pin.pinChar}:${labelId}"
      // }
      // cellLabelBuffer.append(cellLabels)


      val pageNum = insertAt.page.pageNum
      val LTBounds.IntReps(l, t, w, h) = insertAt.bbox

      val jsonRec = Json.arr(
        Json.fromString(char.toString()),
        Json.fromInt(pageNum.unwrap),
        Json.arr(Json.fromInt(l), Json.fromInt(t), Json.fromInt(w), Json.fromInt(h))
      )
      Json.obj(
        "i" := jsonRec
      )
  }}

}
