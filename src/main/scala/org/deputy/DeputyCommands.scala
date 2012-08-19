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
import scala.annotation.tailrec

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

  def withResolvers(moduleOrg: String, moduleName: String, revision: String, resolvers: List[Resolver]): Seq[Line] = {
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

    artifactsOnlys.map { case (artifact, moduleType) => Line(Some(Coords(moduleOrg, moduleName, revision)), Some(artifact), Some(moduleType), None, None) }
  }

  def withResolvers(line: String, resolvers: List[Resolver]): Int = {
    val Coords(moduleOrg: String, moduleName: String, revision: String) = Coords.parse(line)
    withResolvers(moduleOrg, moduleName, revision, resolvers).foreach(l => System.out.println(l.format))
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

  def disableOutput[A](f: => A): A = {
    val out = System.out
    System.setOut(new PrintStream(new OutputStream() {
      override def write(b: Int) = {}
    }))
    try {
      f
    } finally {
      System.setOut(out);
    }
  }

  def explode(parsedLines: Seq[Line], settings: IvySettings, resolvers: List[Resolver], currentLevel: Int, levels: Int = -1): Set[Line] = {
    var currentLevel = 0 //TODO: yeah, this shows up as red in my editor too, it should be tailrec instead of the while, but I am too tired right now
    var lines = parsedLines.toSet
    var lastLines = Set[Line]()
    while ((levels < 0 || currentLevel < levels) && lines != lastLines) { //TODO: it should not be Set[Line] but Seq
      currentLevel += 1
      lines = lastLines ++ (for {
        parsedLine <- lines if parsedLine.moduleType.isDefined && parsedLine.moduleType.get == DependencyTypes.pom
        artifact <- parsedLine.artifact.toList
        descriptor = disableOutput {
          val pomParser = PomModuleDescriptorParser.getInstance
          pomParser.parseDescriptor(settings, new URL(artifact), false) //TODO: depends on resolver && make this async
        }
        descriptor <- descriptor.getDependencies.toList
      } yield {
        //TODO: fix this loop so that it is a Seq again and then reenableSystem.out.println(parsedLine.format)
        val dep = descriptor.getDependencyRevisionId
        val newOutputLine = Line(Some(Coords(dep.getOrganisation, dep.getName, dep.getRevision)), Some(artifact), None, None, resolvedFromArtifact = parsedLine.artifact)
        System.out.println(newOutputLine.format)
        explode(withResolvers(dep.getOrganisation, dep.getName, dep.getRevision, resolvers), settings, resolvers, currentLevel, levels)
      }).flatten
      lastLines = lines
    }
    lines
  }

  def explodeLines(lines: List[String], settings: IvySettings, resolvers: List[Resolver], levels: Int = -1): Int = {
    val parsedLines = lines.map(Line.parse)
    parsedLines.foreach { l => println(l.format) }
    explode(parsedLines, settings, resolvers, 0, levels).foreach { l =>
      println(l.format)
    }
    0
  }

}