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

sealed trait ArtifactsMsgs
case class InitArtifact(line: String) extends ArtifactsMsgs
case class DependenciesFor(artifact: Artifact) extends ArtifactsMsgs
case class AllArtifacts(descr: ModuleDescriptor) extends ArtifactsMsgs

class ArtifactsActor(settings: IvySettings, executor: ActorRef, printerActor: ActorRef, coordsActor: ActorRef) extends Actor {

  def receive = {
    case InitArtifact(line) => {
      self ! DependenciesFor(Artifact.parse(line))
    }
    case DependenciesFor(artifact) => {
      try {
        executor ! LevelsOfDeps
        executor ! CoordsStarted
        printerActor ! artifact
        val location = artifact.artifact
        val urlOpt = location.flatMap { l =>
          if (l.startsWith("http"))
            Some(new URL(l))
          else {
            val f = new File(l)
            if (f.exists) {
              Some(f.toURI.toURL)
            } else {
              None
            }
          }
        }

        urlOpt.foreach { artifactUrl =>
          val pomParser = PomModuleDescriptorParser.getInstance
          val pomDescr = pomParser.parseDescriptor(settings, artifactUrl, false)
          val deps = pomDescr.getDependencies
          //self ! AllArtifacts(pomDescr)
          executor ! DependenciesFound(deps.size)
          deps.map { depDescr =>
            val dep = depDescr.getDependencyRevisionId
            coordsActor ! UsingResolvers(Coord(dep.getOrganisation, dep.getName, dep.getRevision), Some(artifact))
            executor ! DependencyResolved(Coord(dep.getOrganisation, dep.getName, dep.getRevision))
          }
        }
        //executor ! LineCompleted
        executor ! CoordsCompleted
      } catch {
        case e =>
          executor ! e
      }

    }
  }
}