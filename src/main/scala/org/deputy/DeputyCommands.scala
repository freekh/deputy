package org.deputy

import java.io.File
import org.apache.ivy.core.settings.IvySettings
import org.apache.ivy.core.settings.XmlSettingsParser
import org.deputy.resolvers.Resolver
import org.deputy.formatting.OutputLine
import dispatch._
import org.deputy.formatting.FormattingDefaults

object DeputyCommands {

  def settingsFrom(xml: File) = {
    val settings = new IvySettings()
    (new XmlSettingsParser(settings)).parse(xml.toURI.toURL)
    settings
  }

  object Exts {
    private val defaults = List("pom", "ivy", "jar", "war")
    def current = defaults.toSet //TODO: + specified
    val List(pom, ivy, jar, war) = defaults
  }

  object Classifiers {
    private val defaults = List("sources", "javadoc")
    def current = defaults.toSet //TODO: + specified
    val List(sources, javadoc) = defaults
  }

  def withResolvers(l: String, resolvers: List[Resolver]) = { //TODO: line
    val (moduleOrg: String, moduleName: String, revision: String) = FormattingDefaults.parseIvyCoords(l)
    import Exts._

    def commonPattern(artifactPattern: String, ext: String) = {
      val replacedPattern = artifactPattern
        .replace("[module]", moduleName)
        .replace("[revision]", revision)
        .replace("[ext]", ext)
        .replace("[artifact]", moduleName)
      val commonPatterns: Set[String] = if (ext == jar) {
        Set(replacedPattern) ++ Classifiers.current.map(classifier => replacedPattern.replace("[classifier]", classifier))
      } else {
        Set(replacedPattern)
      }

      //TODO: add optional support now we are just removing everything
      commonPatterns.map(_.replaceAll("(\\(.*?\\))", ""))
    }

    val artifactsOnlys = resolvers.flatMap(resolver =>
      for {
        ext <- Exts.current if ext != ivy
        artifactPattern <- resolver.artifactPatterns
      } yield {
        if (resolver.isM2Compatible)
          commonPattern(artifactPattern, ext).map(a => a.replace("[organisation]", moduleOrg.replace(".", "/")) -> ext)
        else
          commonPattern(artifactPattern, ext).map(a => a.replace("[organisation]", moduleOrg) -> ext)
      }).flatten

    artifactsOnlys.map { case (artifact, ext) => System.out.println(OutputLine(moduleOrg, moduleName, revision, artifact, ext, "", "", "", "", "", "", "").format) }
    0
  }

  def prune(l: String) = {
    val outputLine = OutputLine.parse(l)
    val svc = url(outputLine.artifact)
    val response = Http(svc.HEAD)()
    System.out.println(outputLine.copy(statusCode = response.getStatusCode.toString).format)
    0
  }

}