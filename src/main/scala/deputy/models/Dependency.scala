package deputy.models

import deputy.expectedExceptions._

object Dependency {
  val ivyCoordSep = ":"
  val IvyCoordsRegExp = ("^(.*?)" + ivyCoordSep + "(.*?)" + ivyCoordSep + "(.*?)").r

  def parse(ivyCoords: String) = ivyCoords.trim match {
    case IvyCoordsRegExp(moduleOrg, moduleName, version) => Dependency(moduleOrg, moduleName, version)
    case _ => throw IvyCoordsParseException(ivyCoords.trim, IvyCoordsRegExp.toString)
  }
}

//TODO: This should be called dependency
case class Dependency(moduleOrg: String, moduleName: String, revision: String) {
  import Dependency._
  def format = moduleOrg + ivyCoordSep + moduleName + ivyCoordSep + revision
}