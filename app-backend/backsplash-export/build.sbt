initialCommands in console := """
import com.rasterfoundry.backsplash.export._
import com.rasterfoundry.backsplash.export.TileReification._

import cats._
import cats.effect._
import cats.implicits._
import geotrellis.server._

import scala.concurrent.ExecutionContext

implicit val cs = IO.contextShift(ExecutionContext.global)

val rsSE = getRasterSource("file:///Users/nzimmerman/git_repos/raster-foundry/data/cog-pa_m_4208064_se_17_1_20150727.tif")
val rsSW = getRasterSource("file:///Users/nzimmerman/git_repos/raster-foundry/data/cog-pa_m_4208064_sw_17_1_20150727.tif")
val rsNE = getRasterSource("file:///Users/nzimmerman/git_repos/raster-foundry/data/cog-pa_m_4208064_ne_17_1_20150727.tif")
val rsNW = getRasterSource("file:///Users/nzimmerman/git_repos/raster-foundry/data/cog-pa_m_4208064_nw_17_1_20150727.tif")
val src = List((rsNW, List(0, 1, 2)), (rsNE, List(0, 1, 2)), (rsSE, List(0, 1, 2)), (rsSW, List(0, 1, 2)))

val eval = LayerTms.identity(src)
val evalSE = LayerTms.identity(List((rsSE, List(0, 1, 2))))
val evalSW = LayerTms.identity(List((rsSW, List(0, 1, 2))))
val evalNE = LayerTms.identity(List((rsNE, List(0, 1, 2))))
val evalNW = LayerTms.identity(List((rsNW, List(0, 1, 2))))
"""

