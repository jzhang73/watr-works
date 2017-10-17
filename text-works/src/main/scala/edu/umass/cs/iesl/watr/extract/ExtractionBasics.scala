package edu.umass.cs.iesl.watr
package extract


import geometry._
import geometry.syntax._
import utils.{RelativeDirection => Dir}
import scala.collection.mutable
import org.apache.pdfbox.pdmodel.font._
import utils.ExactFloats._

sealed trait ExtractedItem {
  val charProps: CharProps = new CharProps {}

  def id: Int@@CharID
  def bbox: LTBounds

  lazy val location: Point = bbox.toPoint(Dir.BottomLeft)

  def strRepr(): String

}

// Font properties:
//   name/type
//   unique id within document (e.g, 104 0 R)
//   # of glyphs represented (per page, per document)
//   is natural language?? (# of "natural" common bi/trigrams: 'ie', 'st', 'th', etc.)
//   is symbolic??


object FontDefs {
  def getQualifiedFontName(pdFont: PDFont): String = {
    s"""${pdFont.getName}::${pdFont.getType}"""
  }

  def getFontName(pdFont: PDFont): String = {
    pdFont.getName
  }

}

case class FontBounds(
  ascent: Float,
  descent: Float,
  capline: Float
) {
}

case class FontMetrics(
  ascent: Float,
  descent: Float,
  capline: Float
) {


}
case class FontProperties(
  name: String,
  fontType: String,
  declaredBounds: FontBounds,
  pageCount: Int
) {



  val alphaEvidence = Array.ofDim[Int](LetterFrequencies.Letters.length)
  val bigramEvidence = Array.ofDim[Int](LetterFrequencies.Bigrams.length)
  val trigramEvidence = Array.ofDim[Int](LetterFrequencies.Trigrams.length)

  val pagewiseEvidence = Array.ofDim[Int](pageCount)

  val asciiHeights = Array.ofDim[Double](128)

  val dets = mutable.ListBuffer[Double]()

  def addEvidence(c: Char, height: Double, glyphProps: GlyphProps): Unit = {
    // val ctmDet = glyphProps
    if (c.toInt < 128) {
      // dets.append(ctmDet)
      asciiHeights(c.toInt) = height
    }
  }

  def isNatLangFont(): Boolean = {
    val nonZeros = bigramEvidence.count(_ > 0)
    nonZeros > 3
  }

  def inferredMetrics(): FontBounds = {
    // midline ~= height of eaoru
    if (isNatLangFont()) {

      val midlineEv = "eaoru".map(c => asciiHeights(c.toInt)) //avg
      val ascentEv = "ldkh".map(c => asciiHeights(c.toInt)) // max
      val capEv = (65 to 90).toList.map(asciiHeights(_)) //max
      val descentEv = "yqpg".map(c => asciiHeights(c.toInt)) // - midline

      println(s"midline ev: ${midlineEv}")
      println(s"ascentEv ev: ${ascentEv}")
      println(s"descentEv ev: ${descentEv}")
      println(s"cap ev: ${capEv}")

      declaredBounds
    } else {

      declaredBounds
    }
  }
}

class FontDefs(pageCount: Int) {

  import FontDefs._

  val fontProperties = mutable.ArrayBuffer[FontProperties]()

  def addFont(pdFont: PDFont, glyphProps: GlyphProps): Unit = {

    val fname = getFontName(pdFont)

    if (!fontProperties.exists(_.name == fname)) {
      println(s"adding font ${fname}")
      val fontDesc = pdFont.getFontDescriptor
      val props = FontProperties(
        fname,
        pdFont.getType,
        FontBounds(
          fontDesc.getAscent,
          fontDesc.getDescent,
          fontDesc.getCapHeight
        ),
        pageCount
      )
      fontProperties.append(props)
    }
  }

  def addMetricEvidence(pdFont: PDFont, char: Char, bbox: LTBounds, glyphProps: GlyphProps): Unit = {
    fontProperties.find(_.name == pdFont.getName)
      .foreach { fontProps =>
        fontProps.addEvidence(char, bbox.height.asDouble, glyphProps)
      }
  }



  def addEvidence(fontName: String, pageNum: Int@@PageNum, chars: Char*): Unit = {
    val maybeProps = fontProperties.find(_.name == fontName)
    if (maybeProps.isEmpty) {
      println(s"Missing font: ${fontName} for chars: ${chars.mkString}")
    }

    maybeProps.foreach {  props =>
      val ngram = chars.mkString

      if (chars.length == 3) {
        val i = LetterFrequencies.Trigrams.indexOf(ngram)
        if (i >= 0) {
          props.trigramEvidence(i) += 1
        }
      } else if (chars.length == 2) {
        val i = LetterFrequencies.Bigrams.indexOf(ngram)
        if (i >= 0) {
          props.bigramEvidence(i) += 1
        }
      } else if (chars.length == 1) {
        val i = LetterFrequencies.Letters.indexOf(ngram)
        if (i >= 0) {
          props.alphaEvidence(i) += 1
        }

        props.pagewiseEvidence(pageNum.unwrap) += 1
      }
    }

  }



}

object ExtractedItem {
  // implicit class RicherExtractedItem(val self: CharItem) extends AnyVal {}

  case class CharItem(
    id: Int@@CharID,
    bbox: LTBounds,
    fontBbox: LTBounds,
    char: String,
    fontName: String
  ) extends ExtractedItem {
    def strRepr(): String = char
  }


  case class ImgItem(
    id: Int@@CharID,
    bbox: LTBounds
  ) extends ExtractedItem {
    def strRepr(): String = s"[image ${bbox.prettyPrint}]"
  }

  case class PathItem(
    id: Int@@CharID,
    bbox: LTBounds,
    waypoints: Seq[Point]
  ) extends ExtractedItem {

    lazy val pp = waypoints.map(_.prettyPrint).take(4).mkString(", ")
    def strRepr(): String = s"[path ${bbox.prettyPrint}=[$pp]]"
  }


}



class CharProps()  {
  var charRunId: Int = -1
  var isRunBegin: Boolean = false
}
