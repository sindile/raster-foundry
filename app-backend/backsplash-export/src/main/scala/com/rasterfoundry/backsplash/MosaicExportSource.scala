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

object MosaicExportSource {
  implicit val encoder = deriveEncoder[MosaicExportSource]
  implicit val decoder = deriveDecoder[MosaicExportSource]

  implicit val exportable = new Exportable[MosaicExportSource] {
    def keyedTileSegments(
        self: MosaicExportSource,
        zoom: Int
    )(implicit cs: ContextShift[IO]): Iterator[((Int, Int), MultibandTile)] = {
      val extent = exportExtent(self)
      val (minLat, minLng) = (extent.ymin, extent.xmin)
      val (maxLat, maxLng) = (extent.ymax, extent.xmax)
      val (minTileX, minTileY) = getTileXY(minLat, minLng, zoom)
      val (maxTileX, maxTileY) = getTileXY(maxLat, maxLng, zoom)

      val tileList = for {
        xs <- minTileX to maxTileX
        ys <- minTileY to maxTileY
      } yield (xs, ys)

      new Iterator[((Int, Int), MultibandTile)] {
        var allTiles = tileList

        def next = {
          val (x, y) = allTiles.head
          allTiles = allTiles.drop(1)
          val eval = LayerTms.identity(self.rsLayers)
          val tile = eval(zoom, x, y).unsafeRunSync match {
            case Valid(mbtile) =>
              mbtile
            case _ =>
              val bands = self.rsLayers.head._2
              MultibandTile(bands.map(_ => TileReification.invisiTile).toArray)
          }
          ((x - minTileX, y - minTileY), tile)
        }
        def hasNext = allTiles.length > 0
      }
    }

    def exportZoom(self: MosaicExportSource): Int =
      self.zoom

    def exportCellType(self: MosaicExportSource) =
      self.rsLayers
        .map {
          case (rs, _) => rs.cellType
        }
        .foldLeft[CellType](BitCellType) {
          case (acc, next) => ???
        }

    def exportExtent(self: MosaicExportSource) = self.area.envelope

    def segmentLayout(self: MosaicExportSource) =
      exportSegmentLayout(self.area.envelope, self.zoom)
  }
}
