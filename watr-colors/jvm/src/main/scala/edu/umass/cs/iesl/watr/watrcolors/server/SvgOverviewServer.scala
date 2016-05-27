package edu.umass.cs.iesl.watr
package watrcolors
package server

import net.sf.jsi.Rectangle

// import scala.collection.mutable
import extract.CermineExtractor
import watrmarks._
import ammonite.ops._


class SvgOverviewServer(
  rootDirectory: Path
) extends SvgOverviewApi  {
  lazy val corpus = Corpus(rootDirectory)


  def createView(corpusEntryId: String): List[HtmlUpdate] = {
    corpus
      .entry(corpusEntryId)
      .getSvgArtifact
      .asPath
      .map({ f =>
        val corpusPath = corpus.corpusRoot.relativeTo(f)
        println(s"SvgOverviewServer: createView(${corpusEntryId}) path=(${corpusPath})")
        List(
          HtmlReplaceInner("#main", new html.SvgOverviewView().init(corpusPath.toString).toString)
        )
      }).getOrElse ({
        List(
          HtmlReplaceInner("#main", s"Error creating view for ${corpusEntryId}")
        )
      })
  }


  def jsiRectangleToBBox(r: Rectangle): BBox = {
    val x = r.minX
    val y = r.minY
    val w = r.maxX - r.minX
    val h = r.maxY - r.minY
    BBox(x.toDouble, y.toDouble, w.toDouble, h.toDouble, "")
  }

  def pointInside(x: Double, y: Double, b: BBox): Boolean = {
    (b.x <= x && x <= b.x+b.width) && (b.y <= y && y <= b.y+b.height)
  }
  def overlaps(b1: BBox, b2: BBox): Boolean = {
    (pointInside(b1.x, b1.y, b2)
      || pointInside(b1.x+b1.width, b1.y, b2)
      || pointInside(b1.x, b1.y+b1.height, b2)
      || pointInside(b1.x+b1.width, b1.y+b1.height, b2))
  }


  def onSelectBBox(artifactId: String, bbox: BBox): List[HtmlUpdate] = {
    println(s"onSelectBBox:begin(${artifactId}, bbox=${bbox})")
    val bboxes = getCharLevelOverlay(artifactId, bbox)

    val overlapBboxes: List[BBox] = for (b <-bboxes if overlaps(b, bbox)) yield b

    val bbStr = overlapBboxes.map{bb =>
      s"${bb.info}"
    } mkString("<pre>", "\n", "</pre>")

    println(s"onSelectBBox:done, bbox count was ${bboxes.length}, overlap count is ${overlapBboxes.length} ")
    List(
      HtmlReplaceInner("#selection-info", bbStr)
    )
  }

  def getCermineOverlay(corpusEntryId: String): List[BBox] = {
    println(s"getCermineOverlay:begin(${corpusEntryId})")

    val maybeOverlays = corpus
      .entry(corpusEntryId)
      .getArtifact("cermine-zones.json")
      .asJson
      .map({ jsvalue =>
        CermineExtractor.loadSpatialIndices(jsvalue)
      })

    val overlays = maybeOverlays.recover({ case err =>
      sys.error(s"getCermineOverlay: error loading spatialindex ${err}")
    }).get

    // TODO take out hardcoded kludge
    // val maxBBox = BBox(
    val maxBBox = LTBounds(
      0, 0, 2000, 2000
    )
    // val combinedOverlay = concatVertical(overlays)
    val zones = overlays.query(PageID(0), maxBBox)

    val bboxes = for {
      zone <- zones
      region <- zone.bboxes
    } yield {
      // region.bbox
      val zlabels = overlays.getZoneLabels(zone.id)
      val lstr = zlabels.mkString("[", "; ", "]")
      BBox(
        x = region.bbox.left,
        y =  region.bbox.top,
        width = region.bbox.width,
        height = region.bbox.height,
        lstr
      )
    }


    bboxes.toList

  }


  def concatVertical(pages: Seq[ZoneIndexer]): ZoneIndexer = {
    pages.headOption.getOrElse(sys.error("concat vertical"))
  }

  // Create overly w/mozilla pdf.js info displayed, to examine/debug layout info as generated by pdf->svg extraction
  def getCharLevelOverlay(corpusEntryId: String, query: BBox): List[BBox] = {
    // import watrmarks.dom

    // println(s"getCharLevelOverlay(${corpusEntryId})")

    // val overlays = mutable.ArrayBuffer[BBox]()

    // val maybeOverlays = corpus
    //   .entry(corpusEntryId)
    //   .getSvgArtifact
    //   .asReader
    //   .map({ r =>

    //     println("mapping reader")

    //     val tspans = dom.IO.readTSpans(r)
    //     println(s"tspan count = ${tspans.length}")

    //     tspans.foreach { tspan =>

    //       val mFinal = tspan.transforms.foldLeft(Matrix.idMatrix)({
    //         case (acc, e) =>
    //           acc.multiply(e.toMatrix)
    //       })

    //       val offsets = tspan.textXYOffsets

    //       val y = offsets.ys(0)
    //       val ff = tspan.fontFamily

    //       // TODO compute exact font width
    //       val widths = offsets.xs.map(_ => 5d)

    //       (offsets.xs zip widths zip tspan.text).foreach { case ((x, w), ch) =>

    //         val tvec = mFinal.transform(watrmarks.Vector(x, y))
    //         val tvec2 = mFinal.transform(watrmarks.Vector(x+w, y))

    //         val height = 5.0
    //         val bbox = BBox(
    //           x = tvec.x,
    //           y = tvec.y,
    //           width = tvec2.x - tvec.x,
    //           height = height,
    //           info = ch.toString // (CharInfo(ch.toString))
    //         )

    //         overlays.append(bbox)
    //       }
    //     }
    //   })



    // overlays.toList
    ???
  }

}


// (function() {
//   var canvas = this.__canvas = new fabric.Canvas('c');
//   fabric.Object.prototype.originX = fabric.Object.prototype.originY = 'center';
//   fabric.Object.prototype.transparentCorners = false;

//   fabric.loadSVGFromURL('../lib/tiger2.svg', function(objects, options) {
//     var obj = fabric.util.groupSVGElements(objects, options);
//     obj.scale(0.5);

//     // load shapes
//     for (var i = 1; i < 4; i++) {
//       for (var j = 1; j < 4; j++) {
//         obj.clone(function(i, j) {
//           return function(clone) {
//             clone.set({
//               left: i * 200 - 100,
//               top: j * 200 - 100
//             });
//             canvas.add(clone);
//             animate(clone);
//           };
//         }(i, j));
//       }
//     }
//   });

//   function animate(obj) {
//     obj.setAngle(0).animate({ angle: 360 }, {
//       duration: 3000,
//       onComplete: function(){ animate(obj) },
//       easing: function(t, b, c, d) { return c*t/d + b }
//     });
//   }

//   function cache() {
//     canvas.forEachObject(function(obj, i) {
//       if (obj.type === 'image') return;

//       var scaleX = obj.scaleX;
//       var scaleY = obj.scaleY;

//       canvas.remove(obj);
//       obj.scale(1).cloneAsImage(function(clone) {
//         clone.set({
//           left: obj.left,
//           top: obj.top,
//           scaleX: scaleX,
//           scaleY: scaleY
//         });
//         canvas.insertAt(clone, i);
//         animate(clone);
//       });
//     });
//   }

//   (function render(){
//     canvas.renderAll();
//     fabric.util.requestAnimFrame(render);
//   })();

//   document.getElementById('cache').onclick = cache;
// })();
