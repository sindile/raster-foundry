package com.rasterfoundry.backsplash.export

import com.azavea.maml.ast.Expression
import com.azavea.maml.ast.codec.tree._
import geotrellis.server._
import geotrellis.raster._
import geotrellis.vector.Polygon
import geotrellis.raster.io.geotiff._
import geotrellis.contrib.vlm.RasterSource
import geotrellis.proj4._
import cats._
import cats.implicits._
import _root_.io.circe._
import _root_.io.circe.generic.semiauto._

import java.net.URI

case class AnalysisExportSource(
    zoom: Int,
    area: Polygon,
    ast: Expression,
    params: Map[String, List[(URI, Int)]]
) {
  lazy val rsParams =
    params.mapValues { lst =>
      lst.map {
        case (uri, band) => (getRasterSource(uri.toString), band)
      }
    }
}

object AnalysisExportSource {
  implicit val encoder = deriveEncoder[AnalysisExportSource]
  implicit val decoder = deriveDecoder[AnalysisExportSource]

  implicit def exportable =
    new Exportable[AnalysisExportSource] {
      def keyedTileSegments(
          self: AnalysisExportSource
      ): Iterator[((Int, Int), MultibandTile)] = {
        ???
      }

      def exportCellType(self: AnalysisExportSource): CellType =
        DoubleConstantNoDataCellType

      def exportExtent(self: AnalysisExportSource) = self.area.envelope

      def segmentLayout(self: AnalysisExportSource) =
        exportSegmentLayout(self.area.envelope, self.zoom)
    }
}
