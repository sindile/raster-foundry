package com.rasterfoundry.backsplash.export

import geotrellis.raster.io.geotiff._
import geotrellis.raster.io.geotiff.compression._
import geotrellis.raster._
import simulacrum._

import java.util.UUID

@typeclass trait Exportable[A] {
  @op("keyedTileSegments") def keyedTileSegments(
      self: A): Iterator[((Int, Int), MultibandTile)]

  @op("cellType") def cellType(self: A): CellType

  @op("segmentLayout") def segmentLayout(self: A): GeoTiffSegmentLayout

  @op("toGeoTiff") def toGeoTiff(
      self: A,
      compression: Compression): GeoTiffMultibandTile =
    GeoTiffBuilder[MultibandTile]
      .makeTile(
        keyedTileSegments(self),
        segmentLayout = segmentLayout(self),
        cellType = cellType(self),
        compression = compression
      )
      .asInstanceOf[GeoTiffMultibandTile] // This hurts :(
}
