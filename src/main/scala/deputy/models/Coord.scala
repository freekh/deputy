package deputy.models

import deputy.expectedExceptions._

object Coord {
  val ivyCoordSep = ":"
  val IvyCoordsRegExp = ("^(.*?)" + ivyCoordSep + "(.*?)" + ivyCoordSep + "(.*?)").r

  def parse(ivyCoords: String) = ivyCoords.trim match {
    case IvyCoordsRegExp(moduleOrg, moduleName, version) => Coord(moduleOrg, moduleName, version)
    case _ => throw IvyCoordsParseException(ivyCoords.trim, IvyCoordsRegExp.toString)
  }
}

case class Coord(moduleOrg: String, moduleName: String, revision: String) {
  import Coord._
  def format = moduleOrg + ivyCoordSep + moduleName + ivyCoordSep + revision
}