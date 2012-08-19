package org.deputy

import java.io.File
import org.apache.ivy.core.settings.IvySettings
import org.apache.ivy.core.settings.XmlSettingsParser
import org.deputy.resolvers.Resolver
import org.deputy.models._
import dispatch._
import org.apache.ivy.core.IvyPatternHelper
import java.util.HashMap
import org.apache.ivy.plugins.parser.m2.PomModuleDescriptorParser
import java.net.URL
import java.io.PrintStream
import java.io.OutputStream
import scala.xml.Elem
import org.deputy.descriptors.Pom

object DeputyCommands {

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

  object Tokens {
    val classifier = "classifier"
    val defaults = Map(
      classifier -> "")
  }
  object DependencyTypes {
    private val all = List("pom", "ivy")
    val List(pom, ivy) = all
  }

  def withResolvers(line: String, resolvers: List[Resolver]): Int = {
    val Coords(moduleOrg: String, moduleName: String, revision: String) = Coords.parse(line)
    import Exts._

    def commonPattern(artifactPattern: String, moduleType: String) = {
      import scala.collection.JavaConverters._

      val artifact = moduleName //TODO: is this right?
      val commonPatterns: Set[String] = if (moduleType == jar) {
        Set(artifactPattern) ++ Classifiers.current.map(classifier => {
          val classifierTokens = Tokens.defaults ++ Map(Tokens.classifier -> classifier)
          IvyPatternHelper.substituteTokens(artifactPattern, classifierTokens.asJava)
        })
      } else {
        Set(IvyPatternHelper.substituteTokens(artifactPattern, Tokens.defaults.asJava))
      }
      commonPatterns.map(p => IvyPatternHelper.substitute(p, moduleOrg, moduleName, revision, artifact, moduleType, moduleType))

    }

    val artifactsOnlys = resolvers.flatMap(resolver =>
      for {
        ext <- Exts.current if ext != ivy
        artifactPattern <- resolver.artifactPatterns
      } yield {
        val moduleType = ext //TODO: is this right? ext is perhaps xml, ... while type is ivy, pom? 
        val explodedPatterns = if (resolver.isM2Compatible) commonPattern(artifactPattern.replace("[organisation]", moduleOrg.replace(".", "/")), moduleType)
        else commonPattern(artifactPattern, moduleType)
        explodedPatterns.map(p => p -> moduleType)
      }).flatten

    artifactsOnlys.map { case (artifact, moduleType) => System.out.println(Line(Some(Coords(moduleOrg, moduleName, revision)), Some(artifact), Some(moduleType), None, None).format) }
    0

  }

  def withResolvers(lines: List[String], resolvers: List[Resolver]): Int = {
    executeAllLines(lines, { line: String => withResolvers(line, resolvers) })
  }

  def executeAllLines(lines: List[String], f: String => Int): Int = {
    lines.foldLeft(0)((res, line) => res + f(line))
  }

  def check(lines: List[String]): Int = {
    val parsedLinesWithArtifacts = lines.map(Line.parse).foldLeft(List[(Line, String)]())((a, parsedLine) => parsedLine.artifact.map(a => parsedLine -> a).toList ++ a)
    val promise = Promise.all(parsedLinesWithArtifacts.map {
      case (parsedLine, artifact) =>
        val svc = url(artifact)
        Http(svc.HEAD).map(r => r -> parsedLine)
    })
    val result = promise.map(responseLines => {
      responseLines.foldLeft(0) { (result, responseLine) =>
        responseLine match {
          case (response, line) =>
            System.out.println(line.copy(statusCode = Some(response.getStatusCode)).format)
            0
        }
      }
    })
    result()
  }

  def explode(lines: List[String], settings: IvySettings): Int = {
    val parsedLines = lines.map(Line.parse)
    val poms = parsedLines.filter(l => l.moduleType.isDefined && l.moduleType.get == DependencyTypes.pom)
    val pomProms = poms.flatMap { pomLine => //pomProms: hehe :)
      pomLine.artifact.map { pomUrl =>
        Http(url(pomUrl).OK(as.xml.Elem)).map(pomXml => pomLine -> Pom.parse(pomXml, pomUrl))
      }
    }
    Promise.all(pomProms).foreach { urlAndPoms =>
      urlAndPoms.foreach {
        case (pomLine, pom) =>
          System.out.println(pomLine.format)
          pom.dependencies.foreach { dep =>
            System.out.println(Line(dep.toCoords, None, None, None, pomLine.artifact).format)
          }
      }
    }

    0
  }

}