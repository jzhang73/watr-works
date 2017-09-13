package edu.umass.cs.iesl.watr
package rindex

import com.github.davidmoten.rtree.{geometry => RG}
import edu.umass.cs.iesl.watr.{geometry => G}

object RGeometryConversions {
  import G._


  def toRGLine(tb: Line): RG.Line = {
    val Line(Point.Doubles(x1, y1), Point.Doubles(x2, y2)) = tb
    RG.Geometries.line(x1, y1, x2, y2)
  }

  def toRGPoint(tb: Point): RG.Point = {
    val Point.Doubles(x1, y1) = tb
    RG.Geometries.point(x1, y1)
  }

  def toRGRectangle(tb: LTBounds): RG.Rectangle = {
    val LTBounds.Floats(l, t, w, h) = tb
    RGeometry.rectangle(l, t, w, h)
  }

  def rectangle(
    x: Double, y: Double, width: Double, height: Double
  ): RG.Rectangle = rectangle(
    x.toFloat, y.toFloat, width.toFloat, height.toFloat
  )

  def rectangle(
    x: Float, y: Float, width: Float, height: Float
  ): RG.Rectangle = {
    RG.Geometries.rectangle(
      x, y, x+width, y+height
    )
  }


  def toLTBounds(r: RG.Rectangle): G.LTBounds = {
    G.LTBounds.Floats(
      left = r.x1,
      top =  r.y1,
      width = (r.x2 - r.x1),
      height = (r.y2 - r.y1)
    )
  }
}
