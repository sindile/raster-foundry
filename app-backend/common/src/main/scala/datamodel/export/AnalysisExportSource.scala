package com.rasterfoundry.common.datamodel.export

import com.azavea.maml.ast.Expression
import com.azavea.maml.ast.codec.tree._
import geotrellis.vector.Polygon
import geotrellis.proj4._
import _root_.io.circe._
import _root_.io.circe.generic.semiauto._

case class AnalysisExportSource(
    zoom: Int,
    area: Polygon,
    ast: Expression,
    params: Map[String, List[(String, Int, Option[Double])]]
)

object AnalysisExportSource {
  implicit val encoder = deriveEncoder[AnalysisExportSource]
  implicit val decoder = deriveDecoder[AnalysisExportSource]
}
