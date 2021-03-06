package com.rasterfoundry.backsplash.server

import com.rasterfoundry.backsplash._
import com.rasterfoundry.backsplash.ProjectStore.ToProjectStoreOps
import com.rasterfoundry.backsplash.error._
import com.rasterfoundry.database.{
  DatasourceDao,
  SceneDao,
  SceneToLayerDao,
  SceneToProjectDao
}
import com.rasterfoundry.database.Implicits._
import com.rasterfoundry.common.datamodel.{BandOverride, MosaicDefinition}
import com.rasterfoundry.common.datamodel.color.{
  BandGamma => RFBandGamma,
  PerBandClipping => RFPerBandClipping,
  MultiBandClipping => RFMultiBandClipping,
  SigmoidalContrast => RFSigmoidalContrast,
  Saturation => RFSaturation
}
import com.rasterfoundry.backsplash.{ProjectStore, BacksplashImage}
import com.rasterfoundry.backsplash.color.{
  ColorCorrect => BSColorCorrect,
  SingleBandOptions => BSSingleBandOptions,
  _
}

import cats.data.{NonEmptyList => NEL}
import cats.effect.IO
import cats.implicits._
import com.typesafe.scalalogging.LazyLogging
import doobie._
import doobie.implicits._
import geotrellis.vector.{Polygon, Projected}

import java.util.UUID

class ProjectStoreImplicits(xa: Transactor[IO])
    extends ToProjectStoreOps
    with LazyLogging {

  @SuppressWarnings(Array("OptionGet"))
  private def mosaicDefinitionToImage(mosaicDefinition: MosaicDefinition,
                                      bandOverride: Option[BandOverride],
                                      projId: UUID): BacksplashImage = {
    val singleBandOptions =
      mosaicDefinition.singleBandOptions flatMap {
        _.as[BSSingleBandOptions.Params].toOption
      }
    val sceneId = mosaicDefinition.sceneId
    val ingestLocation = mosaicDefinition.ingestLocation getOrElse {
      throw UningestedScenesException(
        s"Scene ${sceneId} does not have an ingest location"
      )
    }
    val footprint = mosaicDefinition.footprint getOrElse {
      throw MetadataException(s"Scene ${sceneId} does not have a footprint")
    }

    val subsetBands = if (mosaicDefinition.isSingleBand) {
      singleBandOptions map { sbo =>
        List(sbo.band)
      } getOrElse {
        throw SingleBandOptionsException(
          "Single band options must be specified for single band projects"
        )
      }
    } else {
      bandOverride map { ovr =>
        List(ovr.redBand, ovr.greenBand, ovr.blueBand)
      } getOrElse {
        List(
          mosaicDefinition.colorCorrections.redBand,
          mosaicDefinition.colorCorrections.greenBand,
          mosaicDefinition.colorCorrections.blueBand
        )
      }
    }

    val colorCorrectParameters = BSColorCorrect.Params(
      0, // red
      1, // green
      2, // blue
      (BandGamma.apply _)
        .tupled(
          RFBandGamma.unapply(mosaicDefinition.colorCorrections.gamma).get),
      (PerBandClipping.apply _).tupled(
        RFPerBandClipping
          .unapply(mosaicDefinition.colorCorrections.bandClipping)
          .get
      ),
      (MultiBandClipping.apply _).tupled(
        RFMultiBandClipping
          .unapply(mosaicDefinition.colorCorrections.tileClipping)
          .get
      ),
      (SigmoidalContrast.apply _)
        .tupled(
          RFSigmoidalContrast
            .unapply(mosaicDefinition.colorCorrections.sigmoidalContrast)
            .get
        ),
      (Saturation.apply _).tupled(
        RFSaturation
          .unapply(mosaicDefinition.colorCorrections.saturation)
          .get
      )
    )

    BacksplashImage(
      sceneId,
      projId,
      ingestLocation,
      footprint,
      subsetBands,
      colorCorrectParameters,
      singleBandOptions
    )
  }

  implicit val sceneStore: ProjectStore[SceneDao] = new ProjectStore[SceneDao] {
    def read(
        self: SceneDao,
        projId: UUID, // actually a scene id, but argument names have to match
        window: Option[Projected[Polygon]],
        bandOverride: Option[BandOverride],
        imageSubset: Option[NEL[UUID]]): fs2.Stream[IO, BacksplashImage] = {
      for {
        scene <- SceneDao.streamSceneById(projId, window).transact(xa)
        compositeO <- fs2.Stream.eval {
          DatasourceDao.unsafeGetDatasourceById(scene.datasource).transact(xa)
        } map {
          _.defaultColorComposite
        }
      } yield {
        // We don't actually have a project, so just make something up
        val randomProjectId = UUID.randomUUID
        val ingestLocation = scene.ingestLocation getOrElse {
          throw UningestedScenesException(
            s"Scene ${scene.id} does not have an ingest location")
        }
        val footprint = scene.dataFootprint getOrElse {
          throw MetadataException(
            s"Scene ${scene.id} does not have a footprint"
          )
        }
        val imageBandOverride = bandOverride map { ovr =>
          List(ovr.redBand, ovr.greenBand, ovr.blueBand)
        } getOrElse { List(0, 1, 2) }
        val colorCorrectParams = BSColorCorrect.paramsFromBandSpecOnly(0, 1, 2)
        logger.debug(s"Chosen color correction: ${colorCorrectParams}")
        BacksplashImage(
          scene.id,
          randomProjectId,
          ingestLocation,
          footprint,
          imageBandOverride,
          colorCorrectParams,
          None // no single band options ever
        )
      }
    }
  }

  implicit val projectStore: ProjectStore[SceneToProjectDao] =
    new ProjectStore[SceneToProjectDao] {
      def read(
          self: SceneToProjectDao,
          projId: UUID,
          window: Option[Projected[Polygon]],
          bandOverride: Option[BandOverride],
          imageSubset: Option[NEL[UUID]]): fs2.Stream[IO, BacksplashImage] = {
        SceneToProjectDao.getMosaicDefinition(
          projId,
          window,
          bandOverride map { _.redBand },
          bandOverride map { _.greenBand },
          bandOverride map { _.blueBand },
          imageSubset map { _.toList } getOrElse List.empty) map {
          mosaicDefinitionToImage(_, bandOverride, projId)
        } transact (xa)
      }
    }

  implicit val layerStore: ProjectStore[SceneToLayerDao] =
    new ProjectStore[SceneToLayerDao] {
      // projId here actually refers to a layer -- but the argument names have to
      // match the typeclass we're providing evidence for
      def read(
          self: SceneToLayerDao,
          projId: UUID,
          window: Option[Projected[Polygon]],
          bandOverride: Option[BandOverride],
          imageSubset: Option[NEL[UUID]]): fs2.Stream[IO, BacksplashImage] = {
        SceneToLayerDao.getMosaicDefinition(
          projId,
          window,
          bandOverride map { _.redBand },
          bandOverride map { _.greenBand },
          bandOverride map { _.blueBand },
          imageSubset map { _.toList } getOrElse List.empty) map {
          mosaicDefinitionToImage(_, bandOverride, projId)
        } transact (xa)
      }
    }
}
