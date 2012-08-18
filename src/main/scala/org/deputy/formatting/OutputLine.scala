package org.deputy.formatting

object FormattingDefaults {
  val ivyCoordSep = ":"
  val sep = "|"
  val escapedSep = "\\|"

  val IvyCoordsRegExp = ("(.*?)" + ivyCoordSep + "(.*?)" + ivyCoordSep + "(.*?)").r

  def parseIvyCoords(ivyCoords: String) = ivyCoords match {
    case IvyCoordsRegExp(moduleOrg, moduleName, version) => (moduleOrg, moduleName, version)
    case _ => throw new Exception("Expected a valid module coordinates in this format: <org>:<name>:<version> but found: " + ivyCoords)
  }
}
import FormattingDefaults._

case class OutputLine(moduleOrg: String, moduleName: String, revision: String, artifact: String, moduleType: String, sha1: String, md5: String, depModuleOrg: String, depModuleName: String, depRevision: String, depArtifact: String, statusCode: String) {
  def format = {
    val depIvy = if (depModuleOrg.nonEmpty || depModuleName.nonEmpty || depRevision.nonEmpty) depModuleOrg + ivyCoordSep + depModuleName + ivyCoordSep + depRevision else ""

    moduleOrg + ivyCoordSep + moduleName + ivyCoordSep + revision + sep + artifact + sep + moduleType + sep + sha1 + sep + md5 + sep + depIvy + sep + depArtifact + sep + statusCode + sep
  }
}

object OutputLine {
  def parse(s: String) = {
    val FormatExpr = ("^(.*?)" + escapedSep + "(.*?)" + escapedSep + "(.*?)" + escapedSep + "(.*?)" + escapedSep + "(.*?)" + escapedSep + "(.*?)" + escapedSep + "(.*?)" + escapedSep + "(.*?)" + escapedSep + "$").r //TODO: proper sha1 and md5
    s match {
      case FormatExpr(ivyCoords, artifact, moduleType, sha1, md5, depIvy, depArtifact, statusCode) => {
        //TODO: OutputLine(parseIvyCoords(ivyCoords)._1, parseIvyCoords(ivyCoords)._2, parseIvyCoords(ivyCoords)._3, artifact, ext, sha1, md5, parseIvyCoords(depIvy)._1, parseIvyCoords(depIvy)._2, parseIvyCoords(depIvy)._3, depIvy)
        OutputLine(parseIvyCoords(ivyCoords)._1, parseIvyCoords(ivyCoords)._2, parseIvyCoords(ivyCoords)._3, artifact, moduleType, sha1, md5, "", "", "", depIvy, statusCode)
      }
      case _ => throw new Exception("Expression: " + s + " cannot be parsed by reg exp: " + FormatExpr)
    }
  }
}