package com.rasterfoundry.backsplash.export

import geotrellis.server._
import geotrellis.raster._
import geotrellis.vector.Polygon
import geotrellis.contrib.vlm.RasterSource
import _root_.io.circe._
import _root_.io.circe.generic.semiauto._

import java.util.UUID
import java.net.URI

case class MosaicExportSource(
    zoom: Int,
    area: Polygon,
    layers: List[(URI, List[Int])]
) {
  lazy val rsLayers =
    layers.map {
      case (uri, bands) => (getRasterSource(uri.toString), bands)
    }
}

object MosaicExportSource {
  implicit val encoder = deriveEncoder[MosaicExportSource]
  implicit val decoder = deriveDecoder[MosaicExportSource]

  implicit val exportable = new Exportable[MosaicExportSource] {
    def keyedTileSegments(
        self: MosaicExportSource
    ): Iterator[((Int, Int), MultibandTile)] = {
      ???
    }

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
