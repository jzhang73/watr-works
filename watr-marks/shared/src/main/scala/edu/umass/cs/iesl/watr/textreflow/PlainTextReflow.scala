package edu.umass.cs.iesl.watr
package textreflow

// TODO plaintext reflow started as testing util, and still has a mix of testing and production code
trait PlainTextReflow {
  import scalaz._, Scalaz._
  import utils.ScalazTreeImplicits._
  import utils.IdGenerator
  import TypeTags._
  import watrmarks.{StandardLabels => LB}
  import matryoshka._
  import java.net.URI
  def dummyUri = URI.create("/")
  import geometry._
  import GeometricFigure._
  import EnrichGeometricFigures._
  import TextReflowF._

  val regionIDs = IdGenerator[RegionID]()

  val page0 = PageID(0)
  val xscale = 10.0d
  val yscale = 10.0d

  def lines(str: String): Seq[String] = {
    str.split("\n")
      .map(_.trim)
  }

  val ffi = 0xFB03.toChar
  val charSubs = Map(
    ffi -> "ffi",
    'ﬂ' -> "fl",
    'ﬆ' -> "st",
    'æ' -> "ae",
    'Æ' -> "AE"
  )

  def targetRegionForXY(x: Int, y: Int, w: Int, h: Int) = TargetRegion(
    RegionID(0),
    page0,
    LTBounds(
      left=x*xscale, top=y*yscale,
      width=w*xscale - 0.1, height=h*yscale - 0.1 // bbox areas are a bit smaller than full 1x1 area
    )
  )



  def stringToTextReflow(multiLines: String): TextReflow = {
    val isMultiline = multiLines.contains("\n")

    var tloc = if (isMultiline) {
      val t: Tree[TextReflowF[Int]] =
        Tree.Node(Flow(List()),
          Stream(
            Tree.Node(Labeled(Set(LB.VisualLine), 0),
              Stream(Tree.Leaf(Flow(List()))))))
      t.loc.lastChild.get.lastChild.get
    } else {
      val t: Tree[TextReflowF[Int]] =
        Tree.Leaf(Flow(List()))
      t.loc
    }

    var linenum = 0
    var chnum = 0

    def insertRight(tr: TextReflowF[Int]): Unit    = { tloc = tloc.insertRight(Tree.Leaf(tr)) }
    def insertLeft(tr: TextReflowF[Int]): Unit     = { tloc = tloc.insertLeft(Tree.Leaf(tr)) }
    def insertDownLast(tr: TextReflowF[Int]): Unit = { tloc = tloc.insertDownLast(Tree.Node(tr, Stream())) }
    def pop(): Unit = { tloc = tloc.parent.get }
    //
    def debug(): Unit = { println(tloc.toTree.map(_.toString).drawBox) }

    for (ch <- lines(multiLines).mkString("\n")) {
      ch match {
        case '\n' =>
          linenum += 1
          chnum = 0
          pop(); pop()
          insertDownLast(Labeled(Set(LB.VisualLine), 0))
          insertDownLast(Flow(List()))


        case '^' => insertDownLast(Labeled(Set(LB.Sup), 0))
        case '_' => insertDownLast(Labeled(Set(LB.Sub), 0))
        case '{' => insertDownLast(Flow(List()))
        case '}' => pop(); pop()
        case ' ' =>
          insertDownLast(Insert(" "))
          pop()
          chnum += 1

        case chx if charSubs.contains(chx) =>
          insertDownLast(Rewrite(0, charSubs(chx)))
          val charAtom = CharAtom(
            targetRegionForXY(chnum, linenum, 1, 1),
            ch.toString
          )
          insertDownLast(Atom(charAtom))
          pop()
          pop()
          chnum += 1

        case _ =>
          val charAtom = CharAtom(
            targetRegionForXY(chnum, linenum, 1, 1),
            ch.toString
          )
          insertDownLast(Atom(charAtom))
          pop()
          chnum += 1
      }
    }

    // Now construct the Fix[] version of the tree:
    val ftree = tloc.toTree
    val res = ftree.scanr ((reflowNode: TextReflowF[Int], childs: Stream[Tree[TextReflow]]) => {
      reflowNode match {
        case t@ Atom(c)                    => fixf(Atom(c))
        case t@ Insert(value)              => fixf(Insert(value))
        case t@ Rewrite(from, to)          => fixf(Rewrite(childs.head.rootLabel, to))
        case t@ Bracket(pre, post, a)      => fixf(Bracket(pre, post, childs.head.rootLabel))
        case t@ Mask(mL, mR, a)            => fixf(Mask(mL, mR, childs.head.rootLabel))
        case t@ Flow(atoms)                => fixf(Flow(childs.toList.map(_.rootLabel)))
        case t@ Labeled(ls, _)             => fixf(Labeled(ls, childs.head.rootLabel))
      }}
    )

    res.rootLabel
  }





  def stringToPageAtoms(str: String): (Seq[PageAtom], PageGeometry) = {
    for {
      (line, linenum) <- lines(str).zipWithIndex
      (ch, chnum)     <- line.zipWithIndex
    } yield {
      CharAtom(
        TargetRegion(regionIDs.nextId, page0,
          LTBounds(
            left=chnum*xscale, top=linenum*yscale,
            width=xscale, height=yscale
          )
        ),
        ch.toString
      )

    }

    val atoms = lines(str).zipWithIndex
      .map({ case (line, linenum) =>
        line.zipWithIndex
          .filterNot(_._1 == ' ')
          .map({ case (ch, chnum) =>
            CharAtom(
              TargetRegion(regionIDs.nextId, page0,
                LTBounds(
                  left=chnum*xscale, top=linenum*yscale,
                  width=xscale, height=yscale
                )
              ),
              ch.toString
            )
          })
      })
      .flatten.toSeq

    val maxX = atoms.map(_.targetRegion.bbox.right).max
    val maxY = atoms.map(_.targetRegion.bbox.bottom).max


    val pageGeom = PageGeometry(
      PageID(0), LTBounds(
        left=0, top=0,
        width=maxX, height=maxY
      )
    )


    (atoms, pageGeom)
  }


}