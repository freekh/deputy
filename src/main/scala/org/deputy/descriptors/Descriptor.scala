package org.deputy.descriptors

import org.deputy.models._

case class DescriptorCoords(moduleOrg: Option[String], moduleName: Option[String], revision: Option[String]) {
  def toCoords = for {
    moduleOrg <- moduleOrg
    moduleName <- moduleName
    revision <- revision
  } yield {
    Coords(moduleOrg, moduleName, revision)
  }
}

abstract class Descriptor {
  val dependencies: Seq[DescriptorCoords]
}