package com.rasterfoundry.backsplash.export

import geotrellis.vector.Extent
import geotrellis.proj4._

object TilesForExtent {
  // generate a list of tiles that fall under some non-latlng extent
  def apply(extent: Extent, zoom: Int, extentCRS: CRS): List[(Int, Int)] =
    latLng(extent.reproject(extentCRS, LatLng), zoom)

  def latLng(extent: Extent, zoom: Int): List[(Int, Int)] = {
    val blLat = extent.ymax
    val blLng = extent.xmin

    val trLat = extent.ymin
    val trLng = extent.xmax

    val (minX, minY) = getTileXY(blLat, blLng, zoom)
    val (maxX, maxY) = getTileXY(trLat, trLng, zoom)
    println(s"xs: ${minX} to ${maxX}")
    println(s"ys: ${minY} to ${maxY}")
    (for {
      xs <- minX to maxX
      ys <- minY to maxY
    } yield (xs, ys)).toList
  }
}
