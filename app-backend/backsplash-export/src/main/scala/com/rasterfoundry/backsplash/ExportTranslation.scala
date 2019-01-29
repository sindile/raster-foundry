package com.rasterfoundry.backsplash.export

import com.rasterfoundry.common.datamodel

object ExportTranslation {
  def translate(ed: datamodel.ExportDefinition): ExportDefinition = {
    val outputDefinition =
      OutputDefinition(ed.output.crs, ed.output.source, ed.output.dropboxCredentials)

    val sourceDefinition = ed.input match {
      case SimpleInput(layers, mask) =>

      case ASTInput(ast, ingestLocs, projectScenes) =>
    }

    ExportDefinition(ed.id, sourceDefinition, outputDefinition)
  }
}
