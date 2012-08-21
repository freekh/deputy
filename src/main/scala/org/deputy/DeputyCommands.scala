package org.deputy

import java.io.File
import org.apache.ivy.core.settings.IvySettings
import org.apache.ivy.core.settings.XmlSettingsParser
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
import org.apache.ivy.plugins.resolver.RepositoryResolver
import org.apache.ivy.plugins.resolver.ChainResolver
import org.apache.ivy.plugins.resolver.util.ResolverHelper
import org.apache.ivy.core.module.id.ModuleRevisionId
import org.apache.ivy.core.module.descriptor.DefaultArtifact
import java.util.Date
import org.apache.ivy.plugins.version.VersionRangeMatcher
import java.io.IOException

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

  def getRepositoryResolvers(resolvers: List[_]): List[RepositoryResolver] = {
    import scala.collection.JavaConversions._
    resolvers flatMap { //TODO: @tailrec

      case r: RepositoryResolver => List(r)
      case r: ChainResolver => getRepositoryResolvers(r.getResolvers.toList)
      //TODO: error msg handling case _ => throw UnsupportedResolver(ivy)  
    }
  }

  def withResolvers(moduleOrg: String, moduleName: String, revision: String, ivySettings: IvySettings): Promise[Set[Artifact]] = {
    import scala.collection.JavaConversions._

    val vrm = new VersionRangeMatcher("range", ivySettings.getDefaultLatestStrategy)
    def acceptRevision(a: String, b: String) = {
      vrm.accept(ModuleRevisionId.newInstance(moduleOrg, moduleName, a), ModuleRevisionId.newInstance(moduleOrg, moduleName, b))
    }

    def isDynamicVersion(a: String) = {
      vrm.isDynamic(ModuleRevisionId.newInstance(moduleOrg, moduleName, a))
    }

    val module = ModuleRevisionId.newInstance(moduleOrg, moduleName, revision)
    val pubDate = new Date() //???
    val pomsAndIvys = Promise.all(for {
      resolver <- getRepositoryResolvers(ivySettings.getResolvers.toList)
      pattern <- resolver.getIvyPatterns.map(_.toString)
      isIvy <- List(true, false)
    } yield {
      val artifact = if (isIvy) DefaultArtifact.newIvyArtifact(module, pubDate) else DefaultArtifact.newPomArtifact(module, pubDate)
      val partiallyResolvedPattern = IvyPatternHelper.substitute(pattern, ModuleRevisionId
        .newInstance(module, IvyPatternHelper.getTokenString(IvyPatternHelper.REVISION_KEY)),
        artifact)
      val moduleType = if (isIvy) DependencyTypes.ivy else DependencyTypes.pom
      val promiseOfRevs = if (isDynamicVersion(revision)) Promise(ResolverHelper.listTokenValues(resolver.getRepository, partiallyResolvedPattern,
        IvyPatternHelper.REVISION_KEY))
      else Promise(Array(revision))
      promiseOfRevs.map { revs =>
        for {
          revs <- Option(revs).toList
          currentRev <- revs.toList if !isDynamicVersion(revision) || acceptRevision(revision, currentRev)
        } yield {
          val url = IvyPatternHelper.substituteToken(partiallyResolvedPattern,
            IvyPatternHelper.REVISION_KEY, currentRev)
          Artifact(Some(Coords(moduleOrg, moduleName, currentRev)), Some(url), Some(moduleType), None, None)
        }
      }
    }).map(_.flatten.toSet)

    pomsAndIvys
  }

  def withResolvers(line: String, ivySettings: IvySettings): Int = {
    val Coords(moduleOrg: String, moduleName: String, revision: String) = Coords.parse(line)
    withResolvers(moduleOrg, moduleName, revision, ivySettings)().foreach(l => System.out.println(l.format))
    0
  }

  def withResolvers(lines: List[String], ivySettings: IvySettings): Int = {
    executeAllLines(lines, { line: String => withResolvers(line, ivySettings) })
  }

  def executeAllLines(lines: List[String], f: String => Int): Int = {
    lines.foldLeft(0)((res, line) => res + f(line))
  }

  def check(lines: List[String]): Int = {
    val parsedLinesWithArtifacts = lines.map(Artifact.parse).foldLeft(List[(Artifact, String)]())((a, parsedLine) => parsedLine.artifact.map(a => parsedLine -> a).toList ++ a)
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

  def explode(parsedLines: Set[Artifact], settings: IvySettings, currentLevel: Int, levels: Int = -1): Promise[Set[Artifact]] = {
    def getDependencies(line: Artifact): Promise[Set[Artifact]] = {
      if (line.moduleType == Some(DependencyTypes.pom)) {
        //println("checking dep for pom in: " + line)
        //THIS CODE IS IN WRITE-ONLY MODE, MUST FIX
        line.artifact.flatMap { pomArtifact =>
          val promPomOpt = disableOutput {
            val pomParser = PomModuleDescriptorParser.getInstance
            val artifactFile = new File(pomArtifact)

            if (pomArtifact.startsWith("http")) {
              try {
                Some(Promise(pomParser.parseDescriptor(settings, new URL(pomArtifact), false)))
              } catch {
                case e: IOException =>
                  None
              }
            } else if (artifactFile.isFile) {
              Some(Promise(pomParser.parseDescriptor(settings, artifactFile.toURI.toURL, false)))
            } else {
              None
            }
          }

          promPomOpt.map { promPom =>
            promPom.flatMap { pom =>
              pom.getDependencies.toList.map { depDescriptor =>
                val dep = depDescriptor.getDependencyRevisionId
                println(Artifact(Some(Coords(dep.getOrganisation, dep.getName, dep.getRevision)), None, Some(DependencyTypes.pom), None, Some(pomArtifact)).format)
                val ls = withResolvers(dep.getOrganisation, dep.getName, dep.getRevision, settings).map { l =>
                  l.map(_.copy(resolvedFromArtifact = Some(pomArtifact)))
                }
                ls
              }
            }.map(_.flatten.toSet)
          }
        }.getOrElse {
          Promise(Set[Artifact]())
        }
      } else {
        Promise(Set[Artifact]())
      }

    }
    val depsPromise = Promise.all(
      parsedLines.map { line =>
        getDependencies(line)
      }).map(_.flatten.toSet).flatMap { lines =>
        val newLines = lines.diff(parsedLines)
        if (newLines.nonEmpty)
          explode(newLines, settings, currentLevel + 1, levels).map { //TODO:make explode @tailrec
            newLines
            lines ++
          }
        else
          Promise(lines)
      }
    depsPromise
  }

  def explodeLines(lines: List[String], settings: IvySettings, levels: Int = -1): Int = {
    val parsedLines = lines.map(Artifact.parse)
    parsedLines.foreach { l => println(l.format) }
    explode(parsedLines.toSet, settings, 0, levels).foreach { p =>
      p.foreach { l =>
        println(l.format)
      }
    }
    0
  }

}