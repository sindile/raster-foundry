package com.rasterfoundry.batch.sentinel2

import com.rasterfoundry.batch.Job
import com.rasterfoundry.batch.util._
import com.rasterfoundry.batch.util.conf.Config
import com.rasterfoundry.common.RollbarNotifier
import com.rasterfoundry.common.utils.AntimeridianUtils
import com.rasterfoundry.common.{S3, S3RegionString}
import com.rasterfoundry.database._
import com.rasterfoundry.database.util.RFTransactor
import com.rasterfoundry.common.datamodel._

import cats.effect.IO
import cats.implicits._
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.util.transactor.Transactor
import io.circe._
import io.circe.syntax._
import geotrellis.proj4._
import geotrellis.vector._
import geotrellis.vector.io._

import java.net.URI
import java.security.InvalidParameterException
import java.time.{LocalDate, ZoneOffset, ZonedDateTime}
import java.util.UUID

final case class ImportSentinel2(startDate: LocalDate = LocalDate.now(
                                   ZoneOffset.UTC))(implicit xa: Transactor[IO])
    extends Config
    with RollbarNotifier {

  implicit val transactor = xa

  // To resolve an ambiguous implicit
  import ImportSentinel2._

  // Eagerly get existing scene names
  val previousDay = startDate.minusDays(1)
  val nextDay = startDate.plusDays(1)
  val sceneQuery =
    sql"""SELECT name
            FROM scenes
            WHERE
            acquisition_date <= ${nextDay.toString}::timestamp AND
            acquisition_date >= ${previousDay.toString}::timestamp AND
            datasource = ${sentinel2Config.datasourceUUID}::uuid
      """.query[String].to[List]
  val existingSceneNamesIO = sceneQuery.transact(xa)

  val name = ImportSentinel2.name

  /** Get S3 client per each call */
  def s3Client =
    S3(region = sentinel2Config.awsRegion.flatMap { region =>
      Some(S3RegionString(region))
    })

  def createImages(sceneId: UUID,
                   infoPath: Option[String],
                   resolution: Float): List[Image.Banded] = {
    val tileInfoPath = infoPath.getOrElse("")
    logger.debug(
      s"Creating images for $tileInfoPath with resolution $resolution")

    val keys = resolution match {
      case 10f =>
        List(
          s"${tileInfoPath}/B02.jp2",
          s"${tileInfoPath}/B03.jp2",
          s"${tileInfoPath}/B04.jp2",
          s"${tileInfoPath}/B08.jp2"
        )
      case 20f =>
        List(
          s"${tileInfoPath}/B05.jp2",
          s"${tileInfoPath}/B06.jp2",
          s"${tileInfoPath}/B07.jp2",
          s"${tileInfoPath}/B8A.jp2",
          s"${tileInfoPath}/B11.jp2",
          s"${tileInfoPath}/B12.jp2"
        )
      case 60f =>
        List(
          s"${tileInfoPath}/B01.jp2",
          s"${tileInfoPath}/B09.jp2",
          s"${tileInfoPath}/B10.jp2"
        )
      case _ =>
        throw new InvalidParameterException(
          s"Unable to create images for $tileInfoPath at resolution $resolution")
    }

    keys.map { obj =>
      val filename = obj.split("/").last
      val imageBand = filename.split("\\.").head
      Image.Banded(
        rawDataBytes = 0,
        visibility = Visibility.Public,
        filename = filename,
        sourceUri = s"${sentinel2Config.baseHttpPath}${obj}",
        owner = systemUser.some,
        scene = sceneId,
        imageMetadata = Json.Null,
        resolutionMeters = resolution,
        metadataFiles = List(),
        bands = Seq(sentinel2Config.bandByName(imageBand)).flatten
      )
    }
  }

  def createThumbnails(sceneId: UUID,
                       tilePath: String): List[Thumbnail.Identified] = {
    val keyPath: String = s"$tilePath/preview.jpg"
    val thumbnailUrl = s"${sentinel2Config.baseHttpPath}$keyPath"

    Thumbnail.Identified(
      id = None,
      thumbnailSize = ThumbnailSize.Square,
      widthPx = 343,
      heightPx = 343,
      sceneId = sceneId,
      url = thumbnailUrl
    ) :: Nil
  }

  def getSentinel2Products(date: LocalDate): List[URI] = {
    s3Client
      .listKeys(
        s3bucket = sentinel2Config.bucketName,
        s3prefix =
          s"products/${date.getYear}/${date.getMonthValue}/${date.getDayOfMonth}/",
        ext = "productInfo.json",
        recursive = true,
        requesterPays = true
      )
      .toList
  }

  /** Because it makes scenes -- get it? */
  def riot(scenePath: String,
           datasourceUUID: UUID,
           user: User): IO[Option[Scene.WithRelated]] = {
    logger.info(s"Attempting to import ${scenePath}")
    val sceneId = UUID.randomUUID()
    val images = List(10f, 20f, 60f)
      .map(createImages(sceneId, scenePath.some, _))
      .reduce(_ ++ _)
    val sceneName = s"S2 ${scenePath}"
    logger.info(s"Starting scene creation: ${scenePath}- ${sceneName}")
    logger.debug(s"Getting tile info for ${scenePath}")
    val tileinfo =
      s3Client
        .getObject(sentinel2Config.bucketName,
                   s"${scenePath}/tileInfo.json",
                   true)
        .getObjectContent
        .toJson
        .getOrElse(Json.Null)
    logger.debug(s"Getting scene metadata for ${scenePath}")
    val sceneMetadata: Map[String, String] = getSceneMetadata(tileinfo)
    val datasourcePrefix = "S2"
    logger.debug(s"${datasourcePrefix} - Extracting polygons for ${scenePath}")
    val tileFootprint = multiPolygonFromJson(tileinfo,
                                             "tileGeometry",
                                             sentinel2Config.targetProjCRS)
    val dataFootprint = multiPolygonFromJson(tileinfo,
                                             "tileDataGeometry",
                                             sentinel2Config.targetProjCRS)
    val intersects = dataFootprint.exists(AntimeridianUtils.crossesAntimeridian)
    val correctedDataFootprint = AntimeridianUtils.correctDataFootprint(
      intersects,
      dataFootprint,
      sentinel2Config.targetProjCRS
    )
    val awsBase = s"https://${sentinel2Config.bucketName}.s3.amazonaws.com"
    val metadataFiles = List(
      s"$awsBase/$scenePath/tileInfo.json",
      s"$awsBase/$scenePath/metadata.xml",
      s"$awsBase/$scenePath/productInfo.json"
    )
    val cloudCover = sceneMetadata.get("cloudyPixelPercentage").map(_.toFloat)
    val acquisitionDate = sceneMetadata.get("timeStamp").map { dt =>
      new java.sql.Timestamp(
        ZonedDateTime
          .parse(dt)
          .toInstant
          .getEpochSecond * 1000l
      )
    }
    logger.debug(
      s"${datasourcePrefix} - Creating scene case class ${scenePath}")
    val sceneCreate = Scene.Create(
      id = sceneId.some,
      visibility = Visibility.Public,
      tags = List("Sentinel-2", "JPEG2000"),
      datasource = datasourceUUID,
      sceneMetadata = sceneMetadata.asJson,
      name = sceneName,
      owner = systemUser.some,
      tileFootprint =
        (sentinel2Config.targetProjCRS.epsgCode, tileFootprint).mapN {
          case (code, mp) => Projected(mp, code)
        },
      dataFootprint = correctedDataFootprint,
      metadataFiles = metadataFiles,
      images = images,
      thumbnails = createThumbnails(sceneId, scenePath),
      ingestLocation = None,
      filterFields = SceneFilterFields(
        cloudCover = cloudCover,
        acquisitionDate = acquisitionDate
      ),
      statusFields = SceneStatusFields(
        thumbnailStatus = JobStatus.Success,
        boundaryStatus = JobStatus.Success,
        ingestStatus = IngestStatus.NotIngested
      ),
      sceneType = SceneType.Avro.some
    )

    val sceneInsertIO = SceneDao.insertMaybe(sceneCreate, user).transact(xa)

    sceneInsertIO
  }

  def insertSceneFromURI(
      uri: URI,
      datasourceUUID: UUID,
      user: User,
      existingSceneNames: List[String]): IO[List[Option[Scene.WithRelated]]] = {
    val paths: List[String] = getScenePaths(uri)
    logger.info(s"Found ${paths.length} tiles for ${uri}")
    paths traverse { (path: String) =>
      val sceneExists = existingSceneNames.contains(s"S2 ${path}")
      sceneExists match {
        case true => {
          logger.warn(s"Skipping scene creation S2 ${path} exists")
          IO.pure(None)
        }
        case _ => {
          logger.info(s"Inserting scene: ${path}")
          riot(path, datasourceUUID, user)
        }
      }
    }
  }

  def getScenePaths(uri: URI): List[String] = {
    val json: Option[Json] = s3Client
      .getObject(uri.getHost, uri.getPath.tail, true)
      .getObjectContent
      .toJson
    val tilesJson: Option[List[Json]] = json flatMap {
      _.hcursor.downField("tiles").as[List[Json]].toOption
    }
    val paths: List[String] = tilesJson match {
      case None => List.empty[String]
      case Some(jsons) => {
        jsons flatMap {
          _.hcursor.downField("path").as[String].toOption
        }
      }
    }
    paths
  }

  def insertIO: IO[Unit] = {
    logger.info(s"Importing Sentinel 2 scenes for ${startDate}")

    val keys = getSentinel2Products(startDate)

    logger.info(s"Found ${keys.length} product info jsons for ${startDate}")

    val user = UserDao
      .unsafeGetUserById(systemUser)
      .transact(xa)
      .unsafeRunSync
    for {
      existingSceneNames <- existingSceneNamesIO
      results <- keys traverse { (key: URI) =>
        insertSceneFromURI(key,
                           sentinel2Config.datasourceUUID,
                           user,
                           existingSceneNames)
          .handleErrorWith(
            (error: Throwable) => {
              logger.error(
                s"Something went wrong inserting $key: ${error.getMessage}")
              sendError(error)
              IO.pure(None)
            }
          )
      }
    } yield {
      logger.info("All scenes inserted or logged")
    }
  }
}

object ImportSentinel2 extends Job {
  val name = "import_sentinel2"

  @SuppressWarnings(Array("CatchThrowable"))
  def multiPolygonFromJson(
      tileinfo: Json,
      key: String,
      targetCrs: CRS = CRS.fromName("EPSG:3857")): Option[MultiPolygon] = {
    val geom = tileinfo.hcursor.downField(key)
    val polygon = try {
      geom.focus.map(_.noSpaces.parseGeoJson[Polygon])
    } catch {
      case e: Throwable => {
        sendError(e)
        logger.warn(s"Error parsing geometry for Sentinel Scene: ${key}")
        None
      }
    }
    val srcProj =
      geom
        .downField("crs")
        .downField("properties")
        .downField("name")
        .focus
        .flatMap(_.as[String].toOption)
        .map { crs =>
          CRS.fromName(s"EPSG:${crs.split(":").last}")
        }

    (srcProj, polygon).mapN {
      case (sourceProjCRS, poly) =>
        MultiPolygon(poly.reproject(sourceProjCRS, targetCrs))
    }
  }

  def getSceneMetadata(tileinfo: Json): Map[String, String] = {

    /** Required for sceneMetadata collection, to unbox Options */
    implicit def optionToString(opt: Option[String]): String = opt.getOrElse("")

    implicit def optionTToString[T](opt: Option[T]): String =
      opt.map(_.toString)

    Map(
      ("path", tileinfo.hcursor.downField("path").as[String].toOption),
      ("timeStamp",
       tileinfo.hcursor.downField("timestamp").as[String].toOption),
      ("utmZone", tileinfo.hcursor.downField("utmZone").as[Int].toOption),
      ("latitudeBand",
       tileinfo.hcursor.downField("latitudeBand").as[String].toOption),
      ("gridSquare",
       tileinfo.hcursor.downField("gridSquare").as[String].toOption),
      ("dataCoveragePercentage",
       tileinfo.hcursor
         .downField("dataCoveragePercentage")
         .as[Double]
         .toOption),
      ("cloudyPixelPercentage",
       tileinfo.hcursor.downField("cloudyPixelPercentage").as[Double].toOption),
      ("productName",
       tileinfo.hcursor.downField("productName").as[String].toOption),
      ("productPath",
       tileinfo.hcursor.downField("productPath").as[String].toOption)
    )
  }

  def runJob(args: List[String]): IO[Unit] = {
    RFTransactor.xaResource
      .use(xa => {
        implicit val transactor = xa
        val job = args.toList match {
          case List(date) => ImportSentinel2(LocalDate.parse(date))
          case _          => ImportSentinel2()
        }
        logger.debug(s"Preparing to run job")
        job.insertIO
      })
  }
}
