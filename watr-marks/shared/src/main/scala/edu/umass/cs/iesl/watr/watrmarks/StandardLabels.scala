package edu.umass.cs.iesl.watr
package watrmarks


trait DocSegLabels {

  // FIXME <- nulllabel????
  val NullLabel = Label("","null")

  val PageLines = Label("ds", "page-lines")
  val PageTextBlocks = Label("ds", "page-textblocks")
  val Column = Label("ds", "column")

  val SectionHeadingLine = Label("ds", "section-heading-line")
  val SectionNumber = Label("ds", "section-number")
  val SectionTitle = Label("ds", "section-title")

  val Title = Label("ds", "title")
  val Author = Label("ds", "author")

  val AbstractHeading = Label("ds", "abstract-heading")
  val Abstract = Label("ds", "abstract")

  val TextBlock = Label("ds", "text-block")
  val TextSpan = Label("ds", "text-span")
  val ParaBegin = Label("ds", "para-begin")
  val Para = Label("ds", "para")
  val Image = Label("ds", "image")
  val Table = Label("ds", "table")
  val VisualLine = Label("ds", "visual-line")
  val SemanticLine = Label("ds", "semantic-line")

  val TokenizedLine = Label("ds", "tline")
  val Token = Label("ds", "token")
  val Tokenized = Label("ds", "tokenized")
  val LineBreakToken = Label("ds", "lb-token")
  val Invisible = Label("ds", "invisible")
  val Sup = Label("ds", "sup-script")
  val Sub = Label("ds", "sub-script")
  val CenterScript = Label("ds", "ctr-script")

  // val Marker = Label("ds", "marker")
  val PageAtom = Label("ds", "atom")

  val First = Label("", "first")
  val Last = Label("", "last")

  val Annotation = Label("ds", "annotation")

}

object StandardLabels extends DocSegLabels {

}