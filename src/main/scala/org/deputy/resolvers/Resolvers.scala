package org.deputy.resolvers

import java.io.File
import org.apache.ivy.core.settings.IvySettings
import org.apache.ivy.core.settings.XmlSettingsParser

abstract class Resolver {
  val artifactPatterns: List[String]
  val ivyPatterns: Option[List[String]]
  val isM2Compatible: Boolean

  def settingsFrom(xml: File) = {
    val settings = new IvySettings()
    (new XmlSettingsParser(settings)).parse(xml.toURI.toURL)
    settings
  }

}
case class UrlResolver(artifactPatterns: List[String], ivyPatterns: Option[List[String]] = None, isM2Compatible: Boolean = false) extends Resolver {

}
