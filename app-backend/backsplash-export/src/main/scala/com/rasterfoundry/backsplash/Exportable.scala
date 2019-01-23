package com.rasterfoundry.backsplash.export

import geotrellis.proj4.CRS
import geotrellis.vector.Extent
import geotrellis.proj4.WebMercator
import geotrellis.raster.io.geotiff._
import geotrellis.raster.io.geotiff.compression._
import geotrellis.raster._
import simulacrum._

import java.util.UUID

@typeclass trait Exportable[A] {
  @op("keyedTileSegments") def keyedTileSegments(
      self: A): Iterator[((Int, Int), MultibandTile)]

  @op("exportCellType") def exportCellType(self: A): CellType

  @op("exportExtent") def exportExtent(self: A): Extent

  // I imagine we'll just be using webmercator for now
  @op("exportCRS") def exportCRS(self: A): CRS = WebMercator

  @op("segmentLayout") def segmentLayout(self: A): GeoTiffSegmentLayout

  @op("toGeoTiff") def toGeoTiff(self: A,
                                 compression: Compression): MultibandGeoTiff = {
    val tifftile = GeoTiffBuilder[MultibandTile]
      .makeTile(
        keyedTileSegments(self),
        segmentLayout = segmentLayout(self),
        cellType = exportCellType(self),
        compression = compression
      )
      .asInstanceOf[GeoTiffMultibandTile] // This hurts :(

    MultibandGeoTiff(tifftile, exportExtent(self), exportCRS(self))
  }
}
