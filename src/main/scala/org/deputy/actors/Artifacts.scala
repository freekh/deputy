package org.deputy.actors

import akka.actor.Actor
import org.apache.ivy.core.settings.IvySettings
import java.net.URL
import org.apache.ivy.plugins.parser.m2.PomModuleDescriptorParser
import java.io.IOException
import org.apache.ivy.core.module.id.ModuleRevisionId
import akka.actor.ActorRef
import java.io.File
import org.deputy.models.Artifact
import org.deputy.models.Coord
import org.apache.ivy.core.module.descriptor.ModuleDescriptor
import org.deputy.Deputy

sealed trait ArtifactsMsgs
case class InitArtifact(line: String) extends ArtifactsMsgs
case class DependenciesFor(artifact: Artifact) extends ArtifactsMsgs
case class AllArtifacts(descr: ModuleDescriptor) extends ArtifactsMsgs

class ArtifactsActor(settings: IvySettings, executor: ActorRef, printerActor: ActorRef, coordsActor: ActorRef) extends Actor {

  def receive = {
    case InitArtifact(line) => {
      executor ! CoordsStarted
      self ! DependenciesFor(Artifact.parse(line))
    }

    case AllArtifacts(pomDescr) => {
      //System.err.println(pomDescr.getAllArtifacts.map(_.toString).toList)
    }
    case DependenciesFor(artifact) => {
      try {
        Deputy.debug("depsFor:" + artifact)

        printerActor ! artifact

        val location = artifact.artifact
        val urlOpt = location.flatMap { l =>
          if (l.startsWith("file")) {
            val f = new File(l)
            if (f.exists) {
              Some(f.toURI.toURL)
            } else {
              None
            }
          } else {
            Some(new URL(l))
          }
        }

        urlOpt.foreach { artifactUrl =>
          //executor ! LevelsOfDeps
          val pomParser = PomModuleDescriptorParser.getInstance
          val pomDescr = pomParser.parseDescriptor(settings, artifactUrl, false)

          val deps = pomDescr.getDependencies
          self ! AllArtifacts(pomDescr)
          executor ! DependenciesFound(deps.size)
          deps.map { depDescr =>
            val dep = depDescr.getDependencyRevisionId
            val c = Coord(dep.getOrganisation, dep.getName, dep.getRevision)
            Deputy.debug("resolving:" + c)
            //executor ! ResolversFor(c)
            executor ! CoordsStarted
            coordsActor ! UsingResolvers(c, Some(artifact), true)
          }
        }

        executor ! CoordsCompleted
      } catch {
        case e =>
          executor ! e
      }

    }
  }
}