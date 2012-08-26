package deputy.models

import deputy.expectedExceptions._

object ResolvedDep {
  val sep = "|"
  val escapedSep = "\\|"

  def parse(s: String): ResolvedDep = {
    val FormatExpr = ("^(.*?)" + escapedSep + "(.*?)" + escapedSep + "(.*?)" + escapedSep + "(\\d*?)" + escapedSep + "(.*?)" + escapedSep + "$").r
    s match {
      case FormatExpr(coordsStr, artifactStr, moduleTypeStr, scopesStr, resolvedFromArtifactStr) => {

        def emptyStringOption(s: String) = if (s.isEmpty) None else Some(s)
        val scopes = scopesStr.split(",").toList
        val resolvedFromArtifact = emptyStringOption(resolvedFromArtifactStr)
        val dep = if (coordsStr.isEmpty) None else Some(Dependency.parse(coordsStr))
        val artifact = emptyStringOption(artifactStr)
        val moduleType = emptyStringOption(moduleTypeStr)

        ResolvedDep(dep, artifact, moduleType, scopes, resolvedFromArtifact)
      }
      case _ => throw LineParseException(s, FormatExpr.toString)
    }
  }
}

case class ResolvedDep(dep: Option[Dependency], artifact: Option[String], moduleType: Option[String], scopes: List[String], parent: Option[String]) {
  import ResolvedDep._
  def format = {
    val depStr = dep.map(_.format).getOrElse { "" }
    val artifactStr = artifact.getOrElse { "" }
    val moduleTypeStr = moduleType.getOrElse { "" }
    val scopesStr = scopes.mkString(",")
    val resolvedFromArtifactStr = dep.getOrElse { "" }
    depStr + sep + artifactStr + sep + moduleTypeStr + sep + scopesStr + sep + resolvedFromArtifactStr + sep
  }
}
