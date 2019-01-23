package com.rasterfoundry.backsplash.export

import geotrellis.raster._
import geotrellis.raster.io.geotiff._
import geotrellis.raster.io.geotiff.compression._
import cats._
import cats.implicits._
import _root_.io.circe._
import _root_.io.circe.generic.semiauto._
import _root_.io.circe.syntax._
import Exportable.ops._

import java.util.UUID

case class ExportDefinition[SourceDefinition: Exportable: Decoder](
    id: UUID,
    source: SourceDefinition,
    output: OutputDefinition
) {
  def toGeoTiff(compression: Compression): GeoTiff[MultibandTile] =
    source.toGeoTiff(compression)
}

object ExportDefinition {
  implicit def encodeFoo[SourceDefinition: Exportable: Encoder]
    : Encoder[ExportDefinition[SourceDefinition]] =
    new Encoder[ExportDefinition[SourceDefinition]] {
      final def apply(exportDef: ExportDefinition[SourceDefinition]): Json =
        Json.obj(
          ("id", exportDef.id.asJson),
          ("src", exportDef.source.asJson),
          ("output", exportDef.output.asJson)
        )
    }

  implicit def decodeExportDefinition[SourceDefinition: Exportable: Decoder]
    : Decoder[ExportDefinition[SourceDefinition]] =
    new Decoder[ExportDefinition[SourceDefinition]] {
      final def apply(
          c: HCursor): Decoder.Result[ExportDefinition[SourceDefinition]] =
        for {
          id <- c.downField("id").as[UUID]
          src <- c.downField("src").as[SourceDefinition]
          out <- c.downField("output").as[OutputDefinition]
        } yield {
          ExportDefinition[SourceDefinition](id, src, out)
        }
    }
}
