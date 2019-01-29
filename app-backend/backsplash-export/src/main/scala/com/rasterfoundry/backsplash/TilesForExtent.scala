package com.rasterfoundry.backsplash.export

import geotrellis.vector.Extent
import geotrellis.proj4._

object TilesForExtent {
  def latLng(extent: Extent, zoom: Int): List[(Int, Int)] = {
    val blLat = extent.ymax
    val blLng = extent.xmin

    val trLat = extent.ymin
    val trLng = extent.xmax

    val (minX, minY) = getTileXY(blLat, blLng, zoom)
    val (maxX, maxY) = getTileXY(trLat, trLng, zoom)

    for {
      xs <- minX to maxX
      ys <- minY to maxY
    } yield (xs, ys)
  }.toList
}
