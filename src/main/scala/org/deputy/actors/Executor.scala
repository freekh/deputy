package org.deputy.actors

import akka.actor.Actor
import akka.actor.Props
import akka.routing.RoundRobinRouter
import org.apache.ivy.core.settings.IvySettings
import akka.actor.ActorRef
import org.deputy.Deputy
import org.deputy.models.Artifact
import org.deputy.models.Coord
import org.deputy.models.Coord

sealed trait ExecutorMsgs
case class CoordsWithResolvers(lines: List[String]) extends ExecutorMsgs
case class Explode(lines: List[String]) extends ExecutorMsgs
case class DependenciesFound(i: Int) extends ExecutorMsgs
case object CoordsCompleted extends ExecutorMsgs
case object CoordsStarted extends ExecutorMsgs
case object Done extends ExecutorMsgs

class Executor(settings: IvySettings) extends Actor {
  var dependenciesFound = 0
  var dependenciesResolved = 0
  var levelsOfDeps = 0
  var coordsCompleted = 0
  var coordsStarted = 0
  var errors = 0

  var initiator: ActorRef = null
  var coordsDeps = Vector.empty[Coord]

  val printerActor = context.actorOf(Props(new OrderedPrinterActor(Deputy.out)))

  val coordsActor = context.actorOf(Props(new CoordsActor(settings, self, printerActor)).withRouter(
    RoundRobinRouter(nrOfInstances = 100)))

  val artifactsActor = context.actorOf(Props(new ArtifactsActor(settings, self, printerActor, coordsActor)).withRouter(
    RoundRobinRouter(nrOfInstances = 100)))

  var lastLine = ""
  def redrawStatus = {
    //System.err.print("\b" * lastLine.size)
    lastLine = "%4d/%4d|deps: %4d/%4d| levels: %4d|new: %4d|errors: %4d".format(
      coordsCompleted,
      coordsStarted,
      dependenciesResolved,
      dependenciesFound,
      levelsOfDeps,
      coordsDeps.size,
      errors)
    //System.err.print(lastLine)
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
    case DependenciesFor(art) => {
      art.coords.foreach { coord =>
        if (!coordsDeps.contains(coord)) {
          artifactsActor ! DependenciesFor(art)
        } else {
          coordsDeps = coordsDeps :+ coord
        }
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
    case CoordsCompleted => {
      coordsCompleted += 1
      redrawStatus
      if ((coordsCompleted + errors) >= coordsStarted) {
        initiator ! Done
        System.err.println
      }
    }

    case e: Exception => {
      errors += 1
      redrawStatus
      if ((coordsCompleted + errors) >= coordsStarted) initiator ! Done
    }
    case u => {
      System.err.println("OMG: " + u)
    }
  }

}