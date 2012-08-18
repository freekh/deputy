package org.deputy.formatting

import org.deputy.expectedExceptions._

object Line {
  val sep = "|"
  val escapedSep = "\\|"

  def parse(s: String): Line = {
    val FormatExpr = ("^(.*?)" + escapedSep + "(.*?)" + escapedSep + "(.*?)" + escapedSep + "(\\d*?)" + escapedSep + "(.*?)" + escapedSep + "$").r
    s match {
      case FormatExpr(coordsStr, artifactStr, moduleTypeStr, statusCodeStr, resolvedFromArtifactStr) => {

        def emptyStringOption(s: String) = if (s.isEmpty) None else Some(s)
        val statusCode = emptyStringOption(statusCodeStr).flatMap { statusCodeStr =>
          try {
            Some(statusCodeStr.toInt)
          } catch {
            case _: java.lang.NumberFormatException => throw IntParseException(s, statusCodeStr)
          }
        }
        val resolvedFromArtifact = emptyStringOption(resolvedFromArtifactStr)
        val coords = if (coordsStr.isEmpty) None else Some(Coords.parse(coordsStr))
        val artifact = emptyStringOption(artifactStr)
        val moduleType = emptyStringOption(moduleTypeStr)

        Line(coords, artifact, moduleType, statusCode, resolvedFromArtifact)
      }
      case _ => throw LineParseException(s, FormatExpr.toString)
    }
  }
}

case class Line(coords: Option[Coords], artifact: Option[String], moduleType: Option[String], statusCode: Option[Int], resolvedFromArtifact: Option[String]) {
  import Line._
  def format = {
    val coordsStr = coords.map(_.format).getOrElse { "" }
    val artifactStr = artifact.getOrElse { "" }
    val moduleTypeStr = moduleType.getOrElse { "" }
    val statusCodeStr = statusCode.map(_.toString).getOrElse { "" }
    val resolvedFromArtifactStr = resolvedFromArtifact.getOrElse { "" }
    coordsStr + sep + artifactStr + sep + moduleTypeStr + sep + statusCodeStr + sep + resolvedFromArtifactStr + sep
  }
}
