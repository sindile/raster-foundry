package com.rasterfoundry.common.datamodel.export

import com.rasterfoundry.common._
import com.azavea.maml.ast.Expression
import com.azavea.maml.ast.codec.tree._
import geotrellis.vector.MultiPolygon
import geotrellis.proj4._
import cats.syntax.functor._
import _root_.io.circe._
import _root_.io.circe.syntax._
import _root_.io.circe.generic.semiauto._

trait ExportSource

object ExportSource {
  implicit def encodeExportSource[ExportSource]: Encoder[ExportSource] =
    new Encoder[ExportSource] {
      final def apply(src: ExportSource): Json = src match {
        case aes @ AnalysisExportSource(_, _, _, _) => aes.asJson
        case mes @ MosaicExportSource(_, _, _)      => mes.asJson
      }
    }
  implicit val decodeExportDefinition: Decoder[ExportSource] =
    List[Decoder[ExportSource]](
      Decoder[AnalysisExportSource].widen,
      Decoder[MosaicExportSource].widen
    ).reduceLeft(_ or _)
}

case class AnalysisExportSource(
    zoom: Int,
    area: MultiPolygon,
    ast: Expression,
    params: Map[String, List[(String, Int, Option[Double])]]
) extends ExportSource

object AnalysisExportSource {
  implicit val encoder = deriveEncoder[AnalysisExportSource]
  implicit val decoder = deriveDecoder[AnalysisExportSource]
}

case class MosaicExportSource(
    zoom: Int,
    area: MultiPolygon,
    layers: List[(String, List[Int], Option[Double])]
) extends ExportSource

object MosaicExportSource {
  implicit val encoder = deriveEncoder[MosaicExportSource]
  implicit val decoder = deriveDecoder[MosaicExportSource]
}
