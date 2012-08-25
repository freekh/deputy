package deputy.models

import deputy.expectedExceptions._

object Artifact {
  val sep = "|"
  val escapedSep = "\\|"

  def parse(s: String): Artifact = {
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
        val coords = if (coordsStr.isEmpty) None else Some(Coord.parse(coordsStr))
        val artifact = emptyStringOption(artifactStr)
        val moduleType = emptyStringOption(moduleTypeStr)

        Artifact(coords, artifact, moduleType, statusCode, resolvedFromArtifact)
      }
      case _ => throw LineParseException(s, FormatExpr.toString)
    }
  }
}

//TODO: This cannot be called artifact, and it should not have a field called "artifact"!!!!
case class Artifact(coords: Option[Coord], artifact: Option[String], moduleType: Option[String], statusCode: Option[Int], resolvedFromArtifact: Option[String]) {
  import Artifact._
  def format = {
    val coordsStr = coords.map(_.format).getOrElse { "" }
    val artifactStr = artifact.getOrElse { "" }
    val moduleTypeStr = moduleType.getOrElse { "" }
    val statusCodeStr = statusCode.map(_.toString).getOrElse { "" }
    val resolvedFromArtifactStr = resolvedFromArtifact.getOrElse { "" }
    coordsStr + sep + artifactStr + sep + moduleTypeStr + sep + statusCodeStr + sep + resolvedFromArtifactStr + sep
  }
}
