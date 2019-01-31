package com.rasterfoundry.backsplash.server

import com.rasterfoundry.backsplash.Parameters._
import com.rasterfoundry.database.{MapTokenDao, ProjectDao, UserDao}
import com.rasterfoundry.database.Implicits._
import com.rasterfoundry.datamodel.{MapToken, Project, User, Visibility}
import com.rasterfoundry.{http4s => RFHttp4s}
import cats.data._
import cats.effect.IO
import cats.implicits._
import com.typesafe.config.ConfigFactory
import doobie.ConnectionIO
import doobie.implicits._
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.server._
import org.http4s.util.CaseInsensitiveString
import java.net.URL
import java.util.UUID

import com.rasterfoundry.backsplash.MetricsRegistrator
import com.typesafe.scalalogging.LazyLogging
import doobie.util.transactor.Transactor
import scalacache.memoization._
import scalacache.CatsEffect.modes._
import scalacache.Flags

import scala.concurrent.duration._

class Authenticators(val xa: Transactor[IO], mtr: MetricsRegistrator)
    extends LazyLogging
    with RFHttp4s.Authenticators {

  val verifyJWTTimer = mtr.newTimer(classOf[Authenticators], "verify-jwt")
  val tokenAuthTimer = mtr.newTimer(classOf[Authenticators], "token-auth")

  val tokensAuthenticator = Kleisli[OptionT[IO, ?], Request[IO], User](
    {
      case req @ _ -> UUIDWrapper(analysisId) /: _
            :? TokenQueryParamMatcher(tokenQP)
            :? MapTokenQueryParamMatcher(mapTokenQP)
          if req.scriptName == "/tools" =>
        val authHeader: OptionT[IO, Header] =
          OptionT.fromOption(
            req.headers.get(CaseInsensitiveString("Authorization")))
        checkTokenAndHeader(tokenQP, authHeader) :+
          (
            OptionT.fromOption[IO](mapTokenQP) flatMap { (mapToken: UUID) =>
              userFromMapToken(MapTokenDao.checkAnalysis(analysisId), mapToken)
            }
          ) reduce { _ orElse _ }

      case req @ _ -> UUIDWrapper(projectId) /: _
            :? TokenQueryParamMatcher(tokenQP)
            :? MapTokenQueryParamMatcher(mapTokenQP) => {
        val authHeaderO: Option[Header] =
          req.headers.get(CaseInsensitiveString("Authorization"))
        (authHeaderO, tokenQP, mapTokenQP) match {
          case (None, None, None) =>
            userFromPublicProject(projectId)
          case _ =>
            val authHeader: OptionT[IO, Header] =
              OptionT.fromOption(authHeaderO)
            checkTokenAndHeader(tokenQP, authHeader) :+
              (
                OptionT.fromOption[IO](mapTokenQP) flatMap { (mapToken: UUID) =>
                  userFromMapToken(MapTokenDao.checkProject(projectId),
                                   mapToken)
                }
              ) reduce { _ orElse _ }
        }
      }

      case _ =>
        OptionT.none[IO, User]
    }
  )

  private def checkTokenAndHeader(
      tokenQP: Option[String],
      authHeader: OptionT[IO, Header]): List[OptionT[IO, User]] = {
    List(
      authHeader flatMap { (header: Header) =>
        userFromToken(header.value.replace("Bearer ", ""))
      },
      OptionT.fromOption[IO](tokenQP) flatMap { (token: String) =>
        userFromToken(token)
      }
    )
  }

  private def userFromMapToken(func: UUID => ConnectionIO[Option[MapToken]],
                               mapTokenId: UUID): OptionT[IO, User] =
    OptionT(func(mapTokenId).transact(xa)) flatMap { mapToken =>
      OptionT(getUserFromJWTwithCache(mapToken.owner))
    }

  private def userFromPublicProject(id: UUID): OptionT[IO, User] =
    for {
      project <- OptionT[IO, Project](
        ProjectDao.query
          .filter(id)
          .filter(fr"tile_visibility=${Visibility.Public.toString}::visibility")
          .selectOption
          .transact(xa))
      user <- OptionT(getUserFromJWTwithCache(project.owner))
    } yield user

  val tokensAuthMiddleware = AuthMiddleware(tokensAuthenticator)
}
