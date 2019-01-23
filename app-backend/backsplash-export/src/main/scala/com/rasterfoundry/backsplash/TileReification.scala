package com.rasterfoundry.backsplash.export

import geotrellis.raster._
import geotrellis.raster.resample._
import geotrellis.proj4._
import geotrellis.spark.SpatialKey
import geotrellis.spark.tiling._
import geotrellis.contrib.vlm._
import geotrellis.contrib.vlm.geotiff._
import geotrellis.contrib.vlm.gdal._
import geotrellis.server._
import com.azavea.maml.ast._
import cats._
import cats.effect._
import cats.implicits._
import com.typesafe.scalalogging.LazyLogging

import java.net.{URI, URLDecoder}
import java.nio.charset.StandardCharsets

object TileReification {
  def DEFAULT = new TileReification(WebMercator)
}

class TileReification(crs: CRS) extends LazyLogging {
  private val tmsLevels: Array[LayoutDefinition] = {
    val scheme = ZoomedLayoutScheme(crs, 256)
    for (zoom <- 0 to 64) yield scheme.levelForZoom(zoom).layout
  }.toArray

  private val invisiCellType = IntUserDefinedNoDataCellType(0)
  private val invisiTile = IntUserDefinedNoDataArrayTile(Array.fill(65536)(0),
                                                         256,
                                                         256,
                                                         invisiCellType)

  private def getRasterSource(uri: String): RasterSource = {
    val enableGDAL = false // for now
    if (enableGDAL) {
      logger.debug(s"Using GDAL Raster Source: ${uri}")
      GDALRasterSource(
        URLDecoder.decode(uri, StandardCharsets.UTF_8.toString()))
    } else {
      logger.debug(s"Using GeoTiffRasterSource: ${uri}")
      new GeoTiffRasterSource(
        URLDecoder.decode(uri, StandardCharsets.UTF_8.toString()))
    }
  }

  type ExportMosaic = List[(URI, List[Int])]
  val mosaicExportTmsReification = new TmsReification[ExportMosaic] {
    def kind(self: ExportMosaic): MamlKind = MamlKind.Image

    def tmsReification(self: ExportMosaic, buffer: Int)(
        implicit contextShift: ContextShift[IO]
    ): (Int, Int, Int) => IO[Literal] = (z: Int, x: Int, y: Int) => {
      val ld = tmsLevels(z)
      val extent = ld.mapTransform.keyToExtent(x, y)
      val subTilesIO = self.traverse {
        case (uri, bands) =>
          IO {
            getRasterSource(uri.toString)
              .reproject(WebMercator, NearestNeighbor)
              .tileToLayout(ld, NearestNeighbor)
              .read(SpatialKey(x, y), bands)
          } flatMap {
            case Some(t) => IO.pure(Raster(t, extent))
            case _ =>
              IO.raiseError(
                new Exception(
                  s"No Tile availble for the following SpatialKey: ${x}, ${y}"))
          }
      }
      val exportTile = subTilesIO.map { subtiles =>
        subtiles.reduceOption(_ merge _) match {
          case Some(tile) => Raster(tile, extent)
          case _          => Raster(MultibandTile(invisiTile), extent)
        }
      }
      exportTile.map(RasterLit(_))
    }
  }

  type ExportAnalysis = List[(URI, Int)]
  val analysisExportTmsReification = new TmsReification[ExportAnalysis] {
    def kind(self: ExportAnalysis): MamlKind = MamlKind.Image

    def tmsReification(self: ExportAnalysis, buffer: Int)(
        implicit contextShift: ContextShift[IO]
    ): (Int, Int, Int) => IO[Literal] = (z: Int, x: Int, y: Int) => {
      val ld = tmsLevels(z)
      val extent = ld.mapTransform.keyToExtent(x, y)
      val subTilesIO = self.traverse {
        case (uri, band) =>
          IO {
            getRasterSource(uri.toString)
              .reproject(WebMercator, NearestNeighbor)
              .tileToLayout(ld, NearestNeighbor)
              .read(SpatialKey(x, y), Seq(band))
          } flatMap {
            case Some(t) => IO.pure(Raster(t, extent))
            case _ =>
              IO.raiseError(
                new Exception(
                  s"No Tile availble for the following SpatialKey: ${x}, ${y}"))
          }
      }
      val exportTile = subTilesIO.map { subtiles =>
        subtiles.reduceOption(_ merge _) match {
          case Some(tile) => Raster(tile, extent)
          case _          => Raster(MultibandTile(invisiTile), extent)
        }
      }
      exportTile.map(RasterLit(_))
    }
  }
}
