package com.rasterfoundry.backsplash.export

import com.rasterfoundry.backsplash.export.shapes._
import com.rasterfoundry.backsplash.export.ExportableInstances._
import com.rasterfoundry.common.datamodel.export._

import cats._
import cats.effect._
import cats.implicits._
import com.monovore.decline._
import geotrellis.raster.io.geotiff._
import geotrellis.raster.io.geotiff.compression._
import geotrellis.raster.io.geotiff.writer.GeoTiffWriter
import shapeless._
import _root_.io.circe._
import _root_.io.circe.syntax._
import _root_.io.circe.parser._
import _root_.io.circe.shapes._
import com.typesafe.scalalogging._
import Exportable.ops._

import java.net.URI
import java.util.UUID
import scala.concurrent.ExecutionContext

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
            implicit val cs: ContextShift[IO] =
              IO.contextShift(ExecutionContext.global)
            val logger = Logger[BacksplashExport.type]

            if (mockMosaicDef) {
              println(ExportDefinition.mockMosaic.asJson)
            } else if (mockAnalysisDef) {
              println(ExportDefinition.mockAnalysis.asJson)
            } else {
              val compression = DeflateCompression(compressionLevel)
              val exportDefString = UriHandler.handle(exportDefUri)

              // The Coproduct of available Exportables (the appropriate coproduct
              //  instances of Decoder and Exportable are necessary)
              type Exports =
                ExportDefinition[AnalysisExportSource] :+:
                  ExportDefinition[MosaicExportSource] :+:
                  CNil
              val decodedExportDef = decode[Exports](exportDefString)

              decodedExportDef match {
                case Right(exportDef) =>
                  logger.info(
                    s"Beginning tiff export to ${exportDef.exportDestination}.")
                  val t0 = System.nanoTime()

                  val geotiff = exportDef.toGeoTiff(compression)
                  GeoTiffWriter.write(geotiff, exportDef.exportDestination)

                  val t1 = System.nanoTime()
                  val secondsElapsed = (t1 - t0).toDouble / 1000000000
                  val secondsString = "%.2f".format(secondsElapsed)
                  logger.info(s"Export completed in $secondsString seconds")
                case Left(err) =>
                  throw err
              }
            }
        }
      }
    )
