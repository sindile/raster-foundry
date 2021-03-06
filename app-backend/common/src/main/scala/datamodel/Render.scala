package com.rasterfoundry.common.datamodel

import io.circe.generic.JsonCodec

@JsonCodec
final case class Render(operation: String, bands: Option[Seq[Int]])
