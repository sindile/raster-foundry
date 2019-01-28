package com.rasterfoundry.backsplash.export

import com.azavea.maml.ast._
import geotrellis.vector.Extent
import geotrellis.proj4.WebMercator
import geotrellis.raster._

import java.net.URI
import java.util.UUID

object MockExportDefinitions {
  private val id = UUID.randomUUID()
  private val outputDefinition =
    OutputDefinition(
      Some(WebMercator),
      true,
      "file:///tmp/test.tif",
      Some("myDropboxCreds")
    )

  val analysis = {
    val source = AnalysisExportSource(
      1,
      Extent(0, 0, 1, 1).toPolygon,
      RasterVar("mockAST"),
      Map(
        "mockAST" -> List(("file:///tmp/test/source1.tif", 1),
                          ("file:///tmp/test/source2.tif", 1),
                          ("file:///tmp/test/source3.tif", 1)))
    )
    ExportDefinition(id, source, outputDefinition)
  }

  val mosaic = {
    val source = MosaicExportSource(
      1,
      Extent(0, 0, 1, 1).toPolygon,
      List(
        ("file:///tmp/test/source1.tif", List(1, 2), Some(0)),
        ("file:///tmp/test/source2.tif", List(2, 3), Some(0)),
        ("file:///tmp/test/source3.tif", List(3, 4), Some(0))
      )
    )
    ExportDefinition(id, source, outputDefinition)
  }
}
