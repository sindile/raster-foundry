package com.azavea.rf.database

import com.azavea.rf.database.Implicits._
import com.azavea.rf.datamodel._

import doobie._, doobie.implicits._
import doobie.postgres._, doobie.postgres.implicits._
import doobie.Fragments
import cats._, cats.data._, cats.effect.IO, cats.implicits._

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

import java.util.UUID


object BandDao extends Dao[Band] {

  val tableName = "bands"

  val selectF =
    sql"""
      SELECT
        id, image_id, name, number, wavelength
      FROM
    """ ++ tableF

  def create(band: Band): ConnectionIO[Band] = {
    val id = UUID.randomUUID
    (fr"INSERT INTO" ++ tableF ++ fr"""
        (id, image_id, name, number, wavelength)
      VALUES
        (${band.id}, ${band.image}, ${band.name}, ${band.number}, ${band.wavelength})
    """).update.withUniqueGeneratedKeys[Band](
      "id", "image_id", "name", "number", "wavelength"
    )
  }

  def createMany(bands: List[Band]): ConnectionIO[Int] = {
    val bandRowsFragment: Fragment = (bands map {
      band: Band => fr"(${band.id}, ${band.image}, ${band.name}, ${band.number}, ${band.wavelength})"
    }).intercalate(fr",")
    val insertFragment:Fragment =
      fr"INSERT INTO" ++ tableF ++ fr"(id, image_id, name, number, wavelength) VALUES" ++ bandRowsFragment
    insertFragment.update.run
  }
}

