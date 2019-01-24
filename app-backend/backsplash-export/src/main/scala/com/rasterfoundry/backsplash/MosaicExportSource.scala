package com.rasterfoundry.backsplash.export

import TileReification._
import geotrellis.server._
import geotrellis.raster._
import geotrellis.vector.Polygon
import geotrellis.contrib.vlm.RasterSource
import _root_.io.circe._
import _root_.io.circe.generic.semiauto._
import cats.effect._
import cats.data.Validated._
import java.util.UUID
import java.net.URI

import com.typesafe.scalalogging.LazyLogging

// layers includes tuples of the URL/URI of a cog + the list of bands to use
case class MosaicExportSource(
    zoom: Int,
    area: Polygon,
    layers: List[(String, List[Int])]
) {
  lazy val rsLayers =
    layers.map {
      case (uri, bands) => (getRasterSource(uri), bands)
    }
}

object MosaicExportSource extends LazyLogging {
  implicit val encoder = deriveEncoder[MosaicExportSource]
  implicit val decoder = deriveDecoder[MosaicExportSource]

  implicit val exportable = new Exportable[MosaicExportSource] {
    def keyedTileSegments(
        self: MosaicExportSource,
        zoom: Int
    )(implicit cs: ContextShift[IO]): Iterator[((Int, Int), MultibandTile)] = {
      val extent = exportExtent(self)
      println(s"Exporting Extent: ${extent}")
      val (minTileX, minTileY) = getTileXY(extent.ymin, extent.xmin, zoom)
      val (maxTileX, maxTileY) = getTileXY(extent.ymax, extent.xmax, zoom)

      println(s"ys: ${maxTileY} to ${minTileY}")
      println(s"xs: ${minTileX} to ${maxTileX}")

      val tileList = for {
        xs <- minTileX to maxTileX
        ys <- maxTileY to minTileY
      } yield (xs, ys)

      println(s"${tileList.length} tiles to export from source")

      new Iterator[((Int, Int), MultibandTile)] {
        var allTiles = tileList

        def next = {
          val (x, y) = allTiles.head
          allTiles = allTiles.drop(1)
          val eval = LayerTms.identity(self.rsLayers)

          println(s"Requesting Tile: (${x}, ${y})")
          val tile = eval(zoom, x, y).unsafeRunSync match {
            case Valid(mbtile) => {
              println(s"Multiband Tile Bands: ${mbtile.bandCount}")
              mbtile
            }
            case _ =>
              val bands = self.rsLayers.head._2
              println(s"Invisitile Bands: ${bands}")
              MultibandTile(bands.map(_ => TileReification.invisiTile).toArray)
          }
          val xLoc = x - minTileX
          val yLoc = y - maxTileY
          println(s"Tile Location: (${xLoc}, ${yLoc})")
          ((xLoc, yLoc), tile)
        }
        def hasNext = {
          println(s"${allTiles.length} tiles left")
          allTiles.length > 0
        }
      }
    }

    def exportZoom(self: MosaicExportSource): Int =
      self.zoom

    def exportCellType(self: MosaicExportSource) = DoubleConstantNoDataCellType

    def exportExtent(self: MosaicExportSource) = self.area.envelope

    def segmentLayout(self: MosaicExportSource) =
      exportSegmentLayout(self.area.envelope, self.zoom)
  }
}
