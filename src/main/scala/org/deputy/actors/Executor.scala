package org.deputy.actors

import akka.actor.Actor
import akka.actor.Props
import akka.routing.RoundRobinRouter
import org.apache.ivy.core.settings.IvySettings
import akka.actor.ActorRef
import org.deputy.Deputy
import org.deputy.models.Artifact
import org.deputy.models.Coord

sealed trait ExecutorMsgs
case class CoordsWithResolvers(lines: List[String]) extends ExecutorMsgs
case class Explode(lines: List[String]) extends ExecutorMsgs
case class ExpandArtifact(depArt: Artifact)
case class DependencyResolved(coord: Coord)
case class DependenciesFound(i: Int) extends ExecutorMsgs
case object CoordsCompleted extends ExecutorMsgs
case object CoordsStarted extends ExecutorMsgs
case object Done extends ExecutorMsgs
case object LevelsOfDeps extends ExecutorMsgs

class Executor(settings: IvySettings) extends Actor {
  var dependenciesFound = 0
  var dependenciesResolved = 0
  var levelsOfDeps = 0
  var coordsCompleted = 0
  var coordsStarted = 0
  var errors = 0

  var initiator: ActorRef = null
  var expandedArtifacts = Vector.empty[Option[Coord]]

  val printerActor = context.actorOf(Props(new OrderedPrinterActor(Deputy.out)))

  val coordsActor = context.actorOf(Props(new CoordsActor(settings, self, printerActor)).withRouter(
    RoundRobinRouter(nrOfInstances = 100)))

  val artifactsActor = context.actorOf(Props(new ArtifactsActor(settings, self, printerActor, coordsActor)).withRouter(
    RoundRobinRouter(nrOfInstances = 100)))

  var lastLine = ""
  def redrawStatus = {
    System.err.print("\b" * lastLine.size)
    lastLine = "%4d/%4d|deps: %4d/%4d| levels: %4d|new: %4d|errors: %4d".format(
      coordsCompleted,
      coordsStarted,
      dependenciesResolved,
      dependenciesFound,
      levelsOfDeps,
      expandedArtifacts.size,
      errors)
    System.err.print(lastLine)
  }

  def receive = {
    case CoordsWithResolvers(lines) => {
      initiator = sender
      lines.foreach { l =>
        coordsActor ! InitCoord(l)
      }
    }
    case Explode(lines) => {
      initiator = sender
      lines.foreach { l =>
        artifactsActor ! InitArtifact(l)
      }
    }
    case CoordsStarted => {
      coordsStarted += 1
      redrawStatus
    }
    case DependenciesFound(i) => {
      dependenciesFound += i
      redrawStatus
    }
    case DependencyResolved(c) => {
      dependenciesResolved += 1
      expandedArtifacts = expandedArtifacts ++ Vector(Some(c))
      redrawStatus
    }
    case CoordsCompleted => {
      coordsCompleted += 1
      redrawStatus
      if ((coordsCompleted + errors) >= coordsStarted) {
        initiator ! Done
        System.err.println
      }
    }
    case ExpandArtifact(a) => {
      if (!expandedArtifacts.contains(a.coords)) {
        artifactsActor ! DependenciesFor(a)
      }
      redrawStatus

    }
    case LevelsOfDeps => {
      levelsOfDeps += 1
      redrawStatus
    }
    case e: Exception => {
      errors += 1
      redrawStatus
      if ((coordsCompleted + errors) >= coordsStarted) initiator ! Done
    }

  }

}