package com.rasterfoundry.backsplash.export

import TileReification._
import com.azavea.maml.ast.Expression
import com.azavea.maml.ast.codec.tree._
import com.azavea.maml.eval._
import geotrellis.server._
import geotrellis.raster._
import geotrellis.raster.rasterize._
import geotrellis.vector.Polygon
import geotrellis.raster.io.geotiff._
import geotrellis.contrib.vlm.RasterSource
import geotrellis.proj4._
import cats._
import cats.data.Validated._
import cats.effect._
import cats.implicits._
import _root_.io.circe._
import _root_.io.circe.generic.semiauto._
import com.typesafe.scalalogging.LazyLogging

import java.net.URI

case class AnalysisExportSource(
    zoom: Int,
    area: Polygon,
    ast: Expression,
    params: Map[String, List[(String, Int, Option[Double])]]
) {
  lazy val rsParams =
    params.mapValues { lst =>
      lst.map {
        case (uri, band, ndOverride) => (getRasterSource(uri), band, ndOverride)
      }
    }
}

object AnalysisExportSource extends LazyLogging {
  implicit val encoder = deriveEncoder[AnalysisExportSource]
  implicit val decoder = deriveDecoder[AnalysisExportSource]

  implicit def exportable =
    new Exportable[AnalysisExportSource] {
      def keyedTileSegments(
          self: AnalysisExportSource,
          zoom: Int
      )(
          implicit cs: ContextShift[IO]
      ): Iterator[((Int, Int), MultibandTile)] = {
        val extent = exportExtent(self)
        val tileList = TilesForExtent.latLng(extent, zoom)
        val minTileX = tileList.map(_._1).min
        val minTileY = tileList.map(_._2).min

        new Iterator[((Int, Int), MultibandTile)] {
          var allTiles = tileList
          val eval =
            LayerTms.apply(IO.pure(self.ast),
                           IO.pure(self.rsParams),
                           BufferingInterpreter.DEFAULT)

          def next() = {
            val (x, y) = allTiles.head
            allTiles = allTiles.drop(1)

            logger.debug(s"Requesting Tile @$zoom/$x/${y}")
            val tile = eval(zoom, x, y).unsafeRunSync match {
              case Valid(mbtile) =>
                logger.debug(s"Constructed Multiband tile @${zoom}/$x/$y")
                val tileExtent =
                  TileReification.tmsLevels(zoom).mapTransform.keyToExtent(x, y)
                mbtile.mask(tileExtent,
                            List(self.area.reproject(LatLng, WebMercator)),
                            Rasterizer.Options.DEFAULT)
              case _ =>
                logger.debug(s"Generating empty tile @${zoom}/${x}/${y}")
                MultibandTile(TileReification.invisiTile)
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

      def exportCellType(self: AnalysisExportSource): CellType =
        DoubleConstantNoDataCellType

      def exportZoom(self: AnalysisExportSource): Int =
        self.zoom

      def exportExtent(self: AnalysisExportSource) = self.area.envelope

      def segmentLayout(self: AnalysisExportSource) =
        exportSegmentLayout(self.area.envelope, self.zoom)
    }
}
