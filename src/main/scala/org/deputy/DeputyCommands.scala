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
import akka.dispatch.Future
import akka.actor.ActorSystem
import akka.dispatch.Await
import akka.util.Duration
import org.apache.ivy.core.module.descriptor.ModuleDescriptor
import akka.actor.Props

object DeputyCommands {
  implicit val akkaSystem = ActorSystem("deputy")

  def println(s: String) = Deputy.out.println(s)
  val printerActor = akkaSystem.actorOf(Props(new PrinterActor(Deputy.out)))

  object ResolutionStrategies {
    private val all = List("firstCompletedOf", "sequence")
    val List(firstCompletedOf, sequence) = all

  }
  val resolutionStrategy = ResolutionStrategies.firstCompletedOf

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

  def withResolvers(moduleOrg: String, moduleName: String, revision: String, ivySettings: IvySettings): Future[Set[Artifact]] = {

    def acceptRevision(a: String, b: String) = synchronized {
      val vrm = new VersionRangeMatcher("range", ivySettings.getDefaultLatestStrategy)
      vrm.accept(ModuleRevisionId.newInstance(moduleOrg, moduleName, a), ModuleRevisionId.newInstance(moduleOrg, moduleName, b))
    }

    def isDynamicVersion(a: String) = synchronized {
      val vrm = new VersionRangeMatcher("range", ivySettings.getDefaultLatestStrategy)
      vrm.isDynamic(ModuleRevisionId.newInstance(moduleOrg, moduleName, a))
    }
    import scala.collection.JavaConversions.{ synchronized => _, _ }

    val pubDate = new Date() //???
    val listOfFutures = for {
      resolver <- getRepositoryResolvers(ivySettings.getResolvers.toList).distinct
      pattern <- resolver.getIvyPatterns.map(_.toString)
      isIvy <- List(true, false)
    } yield {
      val (module, artifact) = if (isIvy) {
        val ivyModule = ModuleRevisionId.newInstance(moduleOrg, moduleName, revision)
        ivyModule -> DefaultArtifact.newIvyArtifact(ivyModule, pubDate)
      } else {
        val pomModule = ModuleRevisionId.newInstance(moduleOrg.replace(".", "/"), moduleName, revision)
        pomModule -> DefaultArtifact.newPomArtifact(pomModule, pubDate)
      }
      val partiallyResolvedPattern = IvyPatternHelper.substitute(pattern, ModuleRevisionId
        .newInstance(module, IvyPatternHelper.getTokenString(IvyPatternHelper.REVISION_KEY)),
        artifact)
      val moduleType = if (isIvy) DependencyTypes.ivy else DependencyTypes.pom
      val promiseOfRevs = if (isDynamicVersion(revision)) Future(ResolverHelper.listTokenValues(resolver.getRepository, partiallyResolvedPattern,
        IvyPatternHelper.REVISION_KEY))
      else Future(Array(revision))

      promiseOfRevs.map { revs =>
        for {
          revs <- Option(revs).toList
          currentRev <- revs.toList if !isDynamicVersion(revision) || acceptRevision(revision, currentRev)
        } yield {
          val url = IvyPatternHelper.substituteToken(partiallyResolvedPattern,
            IvyPatternHelper.REVISION_KEY, currentRev)
          val finalArt = Artifact(Some(Coords(moduleOrg, moduleName, currentRev)), Some(url), Some(moduleType), None, None)
          printerActor ! finalArt
          finalArt
        }
      }
    }
    val pomsAndIvys = if (resolutionStrategy == ResolutionStrategies.firstCompletedOf)
      Future.firstCompletedOf(listOfFutures).map(_.toSet)
    else
      Future.sequence(listOfFutures).map(_.toSet.flatten)
    pomsAndIvys
  }

  def withResolvers(line: String, ivySettings: IvySettings): Int = {
    val Coords(moduleOrg: String, moduleName: String, revision: String) = Coords.parse(line)
    Await.result(withResolvers(moduleOrg, moduleName, revision, ivySettings), Duration.parse("5 seconds")).foreach(l => System.out.println(l.format))
    0
  }

  def withResolvers(lines: List[String], ivySettings: IvySettings): Int = {
    executeAllLines(lines, { line: String => withResolvers(line, ivySettings) })
  }

  def executeAllLines(lines: List[String], f: String => Int): Int = {
    lines.foldLeft(0)((res, line) => res + f(line))
  }

  def resolve(lines: List[String]): Int = {
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

  def explode(artifacts: Seq[Artifact], settings: IvySettings): Seq[Future[Seq[Artifact]]] = {

    def parseInFutureIOCatch(artifact: URL, pomArtifact: String) = Future {
      { //disableOutput {
        val pomParser = PomModuleDescriptorParser.getInstance
        try {
          val pomDescr = pomParser.parseDescriptor(settings, artifact, false)
          pomDescr.getDependencies.map { depDescr =>
            val dep = depDescr.getDependencyRevisionId
            //println("found new dep: " + dep + " from " + artifact)
            dep -> pomArtifact
          }.toList
        } catch {
          case e: IOException => List.empty
        }
      }
    }.flatMap { depArtifacts =>
      val listOfFutures = depArtifacts.map { //I think it is ok to sequence here, because withResolvers will output stuff
        case (dep, pomArtifact) =>
          //println(dep.getOrganisation + ":" + dep.getName + ":" + dep.getRevision) //REMOVE
          withResolvers(dep.getOrganisation, dep.getName, dep.getRevision, settings).map { l =>
            l.map(_.copy(resolvedFromArtifact = Some(pomArtifact)))
          }
      }
      val futureOfDeps = if (resolutionStrategy == ResolutionStrategies.firstCompletedOf) {
        Future.firstCompletedOf(listOfFutures).map(_.toSeq)
      } else {
        Future.sequence(listOfFutures).map(_.flatten)
      }
      futureOfDeps
    }

    def getDependencies(artifact: Artifact): Seq[Future[Seq[Artifact]]] =
      artifact.moduleType.toSeq.flatMap { moduleType =>
        if (moduleType == DependencyTypes.pom) {
          val pomFutureOpt = artifact.artifact.map { pomArtifact =>
            val artifactFile = new File(pomArtifact)
            val artifacts = if (pomArtifact.startsWith("http")) {
              parseInFutureIOCatch(new URL(pomArtifact), pomArtifact)
            } else if (artifactFile.isFile) {
              parseInFutureIOCatch(artifactFile.toURI.toURL, pomArtifact)
            } else {
              Future(Seq.empty)
            }
            artifacts
          }
          pomFutureOpt
        } else {
          Seq.empty
        }
      }
    artifacts.flatMap { artifact =>
      printerActor ! artifact
      val depsFutures = getDependencies(artifact)
      depsFutures.map { depsFuture =>
        depsFuture.flatMap { deps => //I think this is ok, because we are async printing out
          val newDeps = deps.diff(artifacts)
          if (newDeps.nonEmpty)
            Future.sequence(explode(newDeps, settings)).map(deps => artifact +: deps.flatten) //TODO: yeah, it should be @tailrec 
          else
            Future(Seq.empty)
        }
      }
    }
  }

  def explodeLines(lines: List[String], settings: IvySettings): Int = {
    val artifacts = lines.map(Artifact.parse)
    Await.result(Future.sequence(explode(artifacts, settings)), Duration.parse("10 minutes"))
    0
  }

  //Seq[Future[Artificat]] =>
}