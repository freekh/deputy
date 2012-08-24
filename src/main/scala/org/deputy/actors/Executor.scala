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
case object DepedencyResolved extends ExecutorMsgs
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

  val coordsActor = context.actorOf(Props(new CoordsActor(settings, self, printerActor)).withRouter(RoundRobinRouter(nrOfInstances = 1)))

  val artifactsActor = context.actorOf(Props(new ArtifactsActor(settings, self, printerActor, coordsActor)).withRouter(RoundRobinRouter(nrOfInstances = 1)))

  val ignoreVersion = false

  var lastLine = ""
  def redrawStatus = {
    Deputy.progress("\b" * lastLine.size)
    lastLine = "%4d/%4d|deps: %4d/%4d| levels: %4d|new: %4d|errors: %4d".format(
      coordsCompleted,
      coordsStarted,
      dependenciesResolved,
      dependenciesFound,
      levelsOfDeps,
      coordsDeps.size,
      errors)
    Deputy.progress(lastLine)
    Deputy.progress("\r")
  }

  def redrawAndcheckIfFinished = {
    redrawStatus
    if ((coordsCompleted + errors) >= (coordsStarted)) {
      initiator ! Done
      Deputy.progress("\n")
    }
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
    case DepedencyResolved => {
      dependenciesResolved += 1
      redrawStatus
    }
    case DependenciesFor(art) => {
      //if (dependenciesResolved > dependenciesFound)
      //System.err.println("too many deps when processing: " + art)
      art.coords.foreach { coord =>
        val coordRefined = if (ignoreVersion) {
          Coord(coord.moduleOrg, coord.moduleName, "")
        } else {
          coord
        }
        if (!coordsDeps.contains(coordRefined)) {
          Deputy.debug("firstTime:" + coordRefined)
          coordsDeps = coordsDeps :+ coordRefined
          coordsStarted += 1
          artifactsActor ! DependenciesFor(art)
        }
      }
      redrawStatus
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
      redrawAndcheckIfFinished
    }

    case e: Exception => {
      Deputy.debug("Got exception : " + e)
      errors += 1
      redrawAndcheckIfFinished
    }
    case u => {
      Deputy.debug("Got unexpected message : " + u)
    }
  }

}