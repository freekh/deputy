package org.deputy.models

import org.deputy.expectedExceptions._

object Coords {
  val ivyCoordSep = ":"
  val IvyCoordsRegExp = ("^(.*?)" + ivyCoordSep + "(.*?)" + ivyCoordSep + "(.*?)").r

  def parse(ivyCoords: String) = ivyCoords.trim match {
    case IvyCoordsRegExp(moduleOrg, moduleName, version) => Coords(moduleOrg, moduleName, version)
    case _ => throw IvyCoordsParseException(ivyCoords.trim, IvyCoordsRegExp.toString)
  }
}

case class Coords(moduleOrg: String, moduleName: String, revision: String) {
  import Coords._
  def format = moduleOrg + ivyCoordSep + moduleName + ivyCoordSep + revision
}