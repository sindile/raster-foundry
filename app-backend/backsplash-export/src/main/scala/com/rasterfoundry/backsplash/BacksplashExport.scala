package com.rasterfoundry.backsplash.export

import cats._
import cats.effect._
import cats.implicits._
import com.monovore.decline._
import geotrellis.raster.io.geotiff._
import geotrellis.raster.io.geotiff.compression._
import geotrellis.raster.io.geotiff.writer.GeoTiffWriter
import _root_.io.circe._
import _root_.io.circe.syntax._
import _root_.io.circe.parser._
import Exportable.ops._

import java.net.URI
import java.util.UUID

object BacksplashExport
    extends CommandApp(
      name = "rf-export",
      header = "Export mosaic and analysis TIFs",
      main = {

        val exportDefOpt =
          Opts.option[URI]("definition",
                           short = "d",
                           help = "URI of ExportDefinition JSON")

        val compressionLevelOpt = Opts
          .option[Int]("compression",
                       short = "c",
                       help =
                         "The (deflate) compression level to apply on export")
          .withDefault(9)

        // Can be removed if not useful
        val mockAnalysisDefOpt = Opts
          .flag("exampleAnalysis",
                help = "Print out a syntactically valid analysis definition")
          .orFalse

        val mockMosaicDefOpt = Opts
          .flag("exampleMosaic",
                help = "Print out a syntactically valid analysis definition")
          .orFalse

        (exportDefOpt,
         compressionLevelOpt,
         mockAnalysisDefOpt,
         mockMosaicDefOpt).mapN {
          (exportDefUri, compressionLevel, mockAnalysisDef, mockMosaicDef) =>
            if (mockMosaicDef) {
              println(MockExportDefinitions.mosaic.asJson)
            } else if (mockAnalysisDef) {
              println(MockExportDefinitions.analysis.asJson)
            } else {
              val compression = DeflateCompression(compressionLevel)
              val exportDefString = UriHandler.handle(exportDefUri)
              val exportDefinition = List(
                decode[ExportDefinition[AnalysisExportSource]](exportDefString),
                decode[ExportDefinition[MosaicExportSource]](exportDefString)
              ).reduce(_ orElse _)

              exportDefinition match {
                case Right(exportDefinition) =>
                  val geotiff = exportDefinition.toGeoTiff(compression)
                  GeoTiffWriter.write(geotiff, "outputlocation")
                case Left(err) =>
                  throw err
              }
            }
        }
      }
    )
