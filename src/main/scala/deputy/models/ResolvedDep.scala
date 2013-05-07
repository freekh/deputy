package deputy.models

import deputy.expectedExceptions._

object ResolvedDep {
  val sep = "|"
  val escapedSep = "\\|"

  def parse(s: String): ResolvedDep = {
    val FormatExpr = ("^(.*?)" + escapedSep + "(.*?)" + escapedSep + "(.*?)" + escapedSep + "(.*?)" + escapedSep + "(.*?)" + escapedSep + "(.*?)" + escapedSep + "$").r
    s match {
      case FormatExpr(depStr, moduleType, resolverName, scopesStr, path, resolvedFromArtifactStr) => {

        def emptyStringOption(s: String) = if (s.isEmpty) None else Some(s)
        val scopes = scopesStr.split(",").toList
        val resolvedFromArtifact = emptyStringOption(resolvedFromArtifactStr)
        val dep = Dependency.parse(depStr)

        ResolvedDep(dep, moduleType, resolverName, scopes, path, resolvedFromArtifact)
      }
      case _ => throw LineParseException(s, FormatExpr.toString)
    }
  }
}

case class ResolvedDep(dep: Dependency, moduleType: String, resolverName: String, scopes: List[String], path: String, resolvedFromArtifact: Option[String])  {
  import ResolvedDep._
  def format = {
    val depStr = dep.format
    val scopesStr = scopes.mkString(",")
    val resolvedFromArtifactStr = resolvedFromArtifact.getOrElse { "" }
    depStr + sep + moduleType + sep + resolverName + sep + scopesStr + sep + path + sep + resolvedFromArtifactStr + sep
  }
}
