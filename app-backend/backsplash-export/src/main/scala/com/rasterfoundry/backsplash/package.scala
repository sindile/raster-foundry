package com.rasterfoundry.backsplash

import geotrellis.raster._
import geotrellis.raster.io.geotiff._
import geotrellis.raster.io.geotiff.compression._
import geotrellis.spark.SpatialKey
import geotrellis.spark.tiling._
import geotrellis.proj4._
import geotrellis.vector.{Extent, Polygon, Point}
import geotrellis.vector.io._
import geotrellis.contrib.vlm._
import geotrellis.contrib.vlm.geotiff._
import geotrellis.contrib.vlm.gdal._
import cats._
import cats.implicits._
import _root_.io.circe._
import _root_.io.circe.parser._
import _root_.io.circe.syntax._

import scala.util.Try
import java.net.{URI, URLDecoder}
import java.nio.charset.StandardCharsets

package object export {

  implicit val polygonEncoder: Encoder[Polygon] =
    new Encoder[Polygon] {
      def apply(mp: Polygon): Json = {
        parse(mp.toGeoJson) match {
          case Right(js: Json) => js
          case Left(e)         => throw e
        }
      }
    }

  implicit val polygonDecoder: Decoder[Polygon] = Decoder[Json] map {
    _.spaces4.parseGeoJson[Polygon]
  }

  implicit val extentEncoder: Encoder[Extent] =
    new Encoder[Extent] {
      def apply(extent: Extent): Json =
        List(extent.xmin, extent.ymin, extent.xmax, extent.ymax).asJson
    }
  implicit val extentDecoder: Decoder[Extent] =
    Decoder[Json] emap { js =>
      js.as[List[Double]]
        .map {
          case List(xmin, ymin, xmax, ymax) =>
            Extent(xmin, ymin, xmax, ymax)
        }
        .leftMap(_ => "Extent")
    }

  implicit val crsEncoder: Encoder[CRS] =
    Encoder.encodeString.contramap[CRS] { crs =>
      crs.epsgCode
        .map { c =>
          s"epsg:$c"
        }
        .getOrElse(crs.toProj4String)
    }
  implicit val crsDecoder: Decoder[CRS] =
    Decoder.decodeString.emap { str =>
      Either
        .catchNonFatal(Try(CRS.fromName(str)) getOrElse CRS.fromString(str))
        .leftMap(_ => "CRS")
    }

  implicit val uriEncoder: Encoder[URI] =
    Encoder.encodeString.contramap[URI] { _.toString }
  implicit val uriDecoder: Decoder[URI] =
    Decoder.decodeString.emap { str =>
      Either.catchNonFatal(URI.create(str)).leftMap(_ => "URI")
    }

  implicit lazy val celltypeDecoder: Decoder[CellType] =
    Decoder[String].map({ CellType.fromName(_) })
  implicit lazy val celltypeEncoder: Encoder[CellType] =
    Encoder.encodeString.contramap[CellType]({ CellType.toName(_) })

  def getTileXY(lat: Double,
                lng: Double,
                zoom: Int,
                displayProjection: CRS = WebMercator): (Int, Int) = {
    val p = Point(lng, lat).reproject(LatLng, displayProjection)
    val SpatialKey(x, y) = ZoomedLayoutScheme(displayProjection)
      .levelForZoom(zoom)
      .layout
      .mapTransform(p)
    (x, y)

  }

  def exportSegmentLayout(extent: Extent, zoom: Int): GeoTiffSegmentLayout = {
    val (minTileCol, minTileRow) =
      getTileXY(extent.ymin, extent.xmin, zoom)
    val (maxTileCol, maxTileRow) =
      getTileXY(extent.ymax, extent.xmax, zoom)

    val tileCols = maxTileCol - minTileCol
    val tileRows = maxTileRow - minTileRow
    val tileLayout = TileLayout(tileCols, tileRows, 256, 256)
    GeoTiffSegmentLayout(
      tileCols,
      tileRows,
      tileLayout,
      Tiled,
      PixelInterleave
    )
  }

  def getRasterSource(uri: String): RasterSource = {
    val enableGDAL = false // for now
    if (enableGDAL) {
      GDALRasterSource(
        URLDecoder.decode(uri, StandardCharsets.UTF_8.toString()))
    } else {
      new GeoTiffRasterSource(
        URLDecoder.decode(uri, StandardCharsets.UTF_8.toString()))
    }
  }

}
