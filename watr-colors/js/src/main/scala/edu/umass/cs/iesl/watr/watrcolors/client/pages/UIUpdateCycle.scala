package edu.umass.cs.iesl.watr
package watrcolors
package client
package pages


import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue


import scalatags.JsDom
// import JsDom.{TypedTag, tags}
import geometry._
import geometry.syntax._
// import watrmarks._
import labeling._
import LabelWidgetF._


import rx._
import utils.Color
import utils.Colors
import domtags._


import scala.scalajs.js
import scala.scalajs.js.`|`
import org.scalajs.dom
// import org.singlespaced.d3js.selection.Update
// import org.singlespaced.d3js.Selection
import org.singlespaced.d3js.d3
// import org.singlespaced.d3js.Ops._

import scala.scalajs.js
  // import scala.collection.mutable
import js.JSConverters._

trait D3BasicShapes {
  def d3SvgSelection =
    d3.select("#d3-svg-container")
      .select("svg")

  def uiShapeClass(wid: Int@@WidgetID, cs: String*) = {
    ("ui-shape" :: ("wid-"+wid.unwrap.toString()) :: cs.toList).mkString(" ").clazz
  }


  // def renderPosWidget(p: AbsPosWidget): Option[ElementTag] = {
  def renderPosWidget(p: AbsPosWidget): Option[dom.Element] = {
    val AbsPosWidget(fa, strictBounds, bleedBounds, transVec, zOrder, scaling)  = p

    fa match {
      case RegionOverlay(wid, targetRegion, overlays) =>

        Some(
          <.image(
            uiShapeClass(wid, "reg-overlay"),
            ^.xLinkHref := s"/img/region/${targetRegion.id}",
            ^.x         := strictBounds.left,
            ^.y         := strictBounds.top,
            ^.width     := strictBounds.width,
            ^.height    := strictBounds.height
          ).render
        )

      case  Reflow(wid, tr) =>
        Some(createTextWidget(tr.toString, strictBounds)(
          uiShapeClass(wid, "reflow")
        ).render)

      case TextBox(wid, tb) =>
        Some(createTextWidget(tb.toString, strictBounds)(
          uiShapeClass(wid, "textbox")
        ).render)

      case Figure(wid, fig) =>
        val g1 = createShape(fig)
        val tx =  -transVec.x.toInt
        val ty =  -transVec.y.toInt
        Some(
          <.g(
            ^.transform := s"translate($tx $ty)",
            uiShapeClass(wid, "ui-fig"),
            wid.toString.id,
            g1
          ).render
        )

      case LabelWidgetF.Labeled(wid, a, key, value) =>

        // // Define the div for the tooltip
        // val tooltipdiv = d3.select("body").append("div")
        //   .attr("class", "tooltip")
        //   .style("opacity", 0)
        // val hoverIn: js.Function1[dom.MouseEvent, Unit] =
        //   (event: dom.MouseEvent) => {
        //     tooltipdiv.transition()
        //       .duration(200)
        //       .style("opacity", .9)
        //     tooltipdiv.html(value)
        //       .style("left", (event.pageX) + "px")
        //       .style("top", (event.pageY - 28) + "px");

        //     ()
        //   }
        // val hoverOut: js.Function1[dom.MouseEvent, Unit] =
        //   (event: dom.Event) => {
        //     tooltipdiv.transition()
        //       .duration(500)
        //       .style("opacity", 0);

        //     ()
        //   }

        // val hr = hoverArea(uiShapeClass(wid, "label-hover")).render

        // hr.onmouseover = hoverIn
        // hr.onmouseout = hoverOut

        // Some(hr)

        import parts.BootstrapBits._
        val hoverArea = createShape(strictBounds)(
          uiShapeClass(wid, "ui-labeled", value)
        ).asInstanceOf[JsDom.TypedTag[dom.html.Element]]

        Some(hoverArea.tooltip(value))


      case Pad(wid, a, padding, maybeColor) =>

        val color = maybeColor.getOrElse(Color.White).toRGB
        // val fillColor = s"rgba(${color.red}, ${color.green}, ${color.blue}, ${color.alpha})"
        // val fillOpacit = s"rgba(${color.red}, ${color.green}, ${color.blue}, ${color.alpha})"

        val colorStyle =
          s"""|fill: ${color.cssHash};
              |fill-opacity: 0.2;
              |stroke: ${color.cssHash};
              |stroke-opacity: 1.0;
              |""".stripMargin

        val fringe = makeFringeParts(strictBounds, padding)
        val fs = fringe.map(createShape(_))

        Some(
          <.g(
            uiShapeClass(wid, "ui-pad"),
            ^.style:=colorStyle
          )(fs:_*).render
        )


      case Row(wid, as)           => None
      case Col(wid, as)           => None
      case ZStack(wid, as)        => None
      case Identified(_, _, _, _) => None
      case Panel(_, _, _)         => None
      case Terminal               => None
    }
  }


  def createTextWidget(text: String, bbox: LTBounds): ElementTag = {

    val spans = text.split("\n").toList
      .zipWithIndex
      .map{ case (line, i) =>
        val leadingSpaces = line.takeWhile(_==' ').length()
        val padl = leadingSpaces*4
        <.tspan(
          ^.x   := (bbox.left+padl),
          ^.y   := (bbox.top+(i*20)),
          line
        )
      }
    val style =
      s"""|style="font-family: Times New Roman;
          |font-size: 20px;
          |stroke: #020202;
          |fill: #020202;"
          |""".stripMargin

    <.text(
      ^.style     := style,
      ^.x         := bbox.left,
      ^.y         := bbox.top,
      ^.width     := bbox.width,
      ^.height    := bbox.height
    )(spans)


    // val ftext = fabric.Text(text)
    // ftext.setFontSize(14)
    // ftext.top     = bbox.top
    // ftext.left    = bbox.left
    // val scaleX = bbox.width  / ftext.width.doubleValue
    // val scaleY = bbox.height / ftext.height.doubleValue
    // ftext.setScaleX(scaleX)
    // ftext.setScaleY(scaleY)
    // noControls(ftext)

    // ftext
  }

  def createShape(shape: GeometricFigure): ElementTag = {

    def go(shape0: GeometricFigure, fgColor: String, bgColor: String, fgOpacity: Float, bgOpacity: Float): ElementTag = {
      shape0 match {
        case p: Point =>
          <.circle(
            ^.x := p.x, ^.y := p.y
          )

        case l@ Line(p1: Point, p2: Point) =>
          <.line(
            ^.x1:=p1.x, ^.y1:=p1.y,
            ^.x2:=p2.x, ^.y2:=p2.y,
            ^.stroke        := fgColor,
            ^.fill          := bgColor,
            ^.strokeOpacity := fgOpacity,
            ^.fillOpacity   := bgOpacity
          )

        case bbox:LTBounds =>
          <.rect(
            ^.x             := bbox.left,
            ^.y             := bbox.top,
            ^.width         := bbox.width,
            ^.height        := bbox.height,
            ^.stroke        := fgColor,
            ^.fill          := bgColor,
            ^.strokeOpacity := fgOpacity,
            ^.fillOpacity   := bgOpacity
          )

        case b:LBBounds =>
          go(b.toLTBounds, fgColor, bgColor, fgOpacity, bgOpacity)

        case g @ GeometricGroup(bounds, figs) =>
          val shapes = figs.map(go(_, Colors.Black.cssHash(), bgColor, 0.2f, 0.2f))
            <.g(shapes:_*)

        case g @ Colorized(fig: GeometricFigure, fg: Color, bg: Color, fgOpacity: Float, bgOpacity: Float) =>
          go(fig, fg.cssHash(), bg.cssHash(), fgOpacity, bgOpacity)
      }
    }

    go(shape, "", "", 0f, 0f)
  }
}


trait UIUpdateCycle extends D3BasicShapes {

  def doUIUpdateCycle(r: UIRequest): Future[UIResponse]
  def updateUIState(state: UIState): Unit

  def uiRequestCycle(req: UIRequest)(implicit ctx: Ctx.Owner): Future[Unit] = for {
    uiResponse  <- doUIUpdateCycle(req)
  } yield {
    println("complete:uiRequest ")
    joinResponseData(uiResponse.changes)
    updateUIState(uiResponse.uiState)
  }

  def setD3SvgDimensions(changes: Seq[WidgetMod]): Unit = {
    changes.headOption.foreach { _ match {
      case WidgetMod.Added(wid, widget) =>
        val bbox = widget.get.strictBounds
        d3SvgSelection
          .attr("width", bbox.width)
          .attr("height", bbox.height)
      case _ =>
    }}
  }

  type KeyFunction = js.ThisFunction2[dom.Node|js.Array[WidgetMod],js.UndefOr[WidgetMod], Int, String]
  type MyDatumFunction = js.Function3[WidgetMod, Int, js.UndefOr[Int], dom.EventTarget]

  def joinResponseData(changes: Seq[WidgetMod]): Unit = {
    changes.foreach { mod =>
      mod match {
        case WidgetMod.Added(wid, widget) =>
          println(s"${mod}")
        case WidgetMod.Removed(wid) =>
          println(s"${mod}")
        case _ =>
      }
    }

    setD3SvgDimensions(changes)

    val incomingData: js.Array[WidgetMod] =
      changes.collect({
        case w: WidgetMod.Unmodified  => w
        case w: WidgetMod.Added       => w
      }).map(_.asInstanceOf[WidgetMod])
        .toJSArray


    val keyFunc: KeyFunction = (t: dom.Node|js.Array[WidgetMod], d:js.UndefOr[WidgetMod], i:Int) => {
      d.map(_.id.toString).getOrElse(sys.error(s"keyFunc error on t:${t} d:${d}, i:${i}"))
    }

    val emptyTarget = <.g(".empty".clazz).render

    val dataFunc: js.Function3[WidgetMod, Double, Double, dom.EventTarget] =
      (mod:WidgetMod, a: Double, b: Double) => {
        mod match {
          case WidgetMod.Added(wid, widget) =>
            val maybeElem = for {
              w <- widget
              e <- renderPosWidget(w)
            } yield e

            maybeElem.getOrElse(emptyTarget)

          case _ =>
            emptyTarget
        }
      }


    val select0 = d3SvgSelection
      .selectAll[dom.EventTarget](".ui-shape")
      .data[WidgetMod](incomingData, keyFunc)

    select0
      .enter()
      .append(dataFunc)

    select0.exit()
      .remove()

  }

}