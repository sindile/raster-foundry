package com.rasterfoundry.backsplash.export

import TileReification._
import geotrellis.server._
import geotrellis.raster._
import geotrellis.raster.rasterize._
import geotrellis.vector.Polygon
import geotrellis.contrib.vlm.RasterSource
import geotrellis.proj4._
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
    layers: List[(String, List[Int], Option[Double])]
) {
  def rsLayers: List[(RasterSource, List[Int], Option[Double])] =
    layers.map {
      case (uri, bands, ndOverride) => (getRasterSource(uri), bands, ndOverride)
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
      val tileList = TilesForExtent.latLng(extent, zoom)
      val minTileX = tileList.map(_._1).min
      val minTileY = tileList.map(_._2).min

      new Iterator[((Int, Int), MultibandTile)] {
        var allTiles = tileList
        val eval = LayerTms.identity(self.rsLayers)

        def next() = {
          val (x, y) = allTiles.head
          allTiles = allTiles.drop(1)

          logger.debug(s"Requesting Tile @$zoom/$x/${y}")
          val tile = eval(zoom, x, y).unsafeRunSync match {
            case Valid(mbtile) =>
              logger.debug(
                s"Constructed Multiband tile @${zoom}/$x/$y with bands ${mbtile.bandCount}")
              val tileExtent =
                TileReification.tmsLevels(zoom).mapTransform.keyToExtent(x, y)
              mbtile.mask(tileExtent,
                          List(self.area.reproject(LatLng, WebMercator)),
                          Rasterizer.Options.DEFAULT)
            case _ =>
              val bands = self.rsLayers.head._2
              logger.debug(
                s"Generating empty tile @${zoom}/${x}/${y} with bands ${bands.length}")
              MultibandTile(bands.map(_ => TileReification.invisiTile).toArray)
          }
          val xLoc = x - minTileX
          val yLoc = y - minTileY
          logger.debug(s"Tiff segment Location: (${xLoc}, ${yLoc})")
          ((xLoc, yLoc), tile)
        }
        def hasNext = {
          logger.debug(s"${allTiles.length} tiles left")
          allTiles.length > 0
        }
      }
    }

    def exportZoom(self: MosaicExportSource): Int =
      self.zoom

    def exportCellType(self: MosaicExportSource) =
      DoubleConstantNoDataCellType

    def exportExtent(self: MosaicExportSource) =
      self.area.envelope

    def segmentLayout(self: MosaicExportSource) =
      exportSegmentLayout(self.area.envelope, self.zoom)
  }
}
