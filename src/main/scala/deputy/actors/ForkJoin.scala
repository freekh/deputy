package deputy.actors

import akka.actor.Actor
import akka.actor.Props
import akka.routing.SmallestMailboxRouter
import org.apache.ivy.core.settings.IvySettings
import akka.actor.ActorRef
import deputy.Deputy
import deputy.models.ResolvedDep
import deputy.models.Dependency

sealed trait ForkJoinMsgs
case class DependencyWithResolvers(lines: List[String]) extends ForkJoinMsgs
case class Explode(lines: List[String]) extends ForkJoinMsgs
case class DependenciesFound(i: Int) extends ForkJoinMsgs
case object CoordsCompleted extends ForkJoinMsgs
case object CoordsStarted extends ForkJoinMsgs
case object DepedencyResolved extends ForkJoinMsgs
case object Done extends ForkJoinMsgs
case class DependenciesFor(resolvedDep: ResolvedDep, excludeRules: Seq[(String, Option[String])]) extends ForkJoinMsgs
case class Exclude(parent: Dependency, id: String, excludeOrg: String, excludeNameOpt: Option[String]) extends ForkJoinMsgs

object ForkJoinHelper {
  def executeTask(executor: ActorRef)(f: => Unit) = {
    try {
      executor ! CoordsStarted
      f
      executor ! CoordsCompleted
    } catch {
      case e =>
        executor ! e
    }
  }
}

class ForkJoinActor(settings: IvySettings) extends Actor {
  var dependenciesFound = 0
  var dependenciesResolved = 0
  var levelsOfDeps = 0
  var coordsCompleted = 0
  var coordsStarted = 0
  var errors = 0

  var initiator: ActorRef = null
  var resolvedDeps = Vector.empty[Dependency]

  var excludes: Map[(Dependency, Option[String]), Seq[(String, Option[String])]] = Map.empty

  val printerActor = context.actorOf(Props(new PrinterActor(Deputy.out)))

  val dependencyActors = context.actorOf(Props(new DependencyActor(settings, self, printerActor)).withRouter(SmallestMailboxRouter(nrOfInstances = 1))) //TODO: 10 is random, perhaps we should have a IO Router or something?

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
      resolvedDeps.size,
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
    case DependencyWithResolvers(lines) => {
      initiator = sender
      lines.foreach { l =>
        dependencyActors ! ResolveDep(Dependency.parse(l), List.empty, None, false)
      }
    }
    case Explode(lines) => {
      initiator = sender
      lines.foreach { l =>
        val rd = ResolvedDep.parse(l)
        printerActor ! rd
        dependencyActors ! DependenciesFor(rd, List.empty)
      }
    }
    case rd @ ResolveDep(dep, scopes, parent, transitive) => {
      dependencyActors ! rd
    }
    case DepedencyResolved => {
      dependenciesResolved += 1
      redrawStatus
    }
    case Exclude(parent, id, excludeOrg, excludeNameOpt) => {
      val key = parent -> Some(id)
      val newExcludeRules = excludes.get(parent -> Some(id)).map { rules =>
        rules :+ (excludeOrg, excludeNameOpt)
      }.getOrElse {
        Seq(excludeOrg -> excludeNameOpt)
      }
      excludes += key -> newExcludeRules
    }
    case DependenciesFor(current, _) => { //TODO: create new case class for this 
      current.dep.foreach { dep =>
        if (!resolvedDeps.contains(dep)) {
          Deputy.debug("firstTime:" + dep)
          resolvedDeps = resolvedDeps :+ dep
          coordsStarted += 1
          val rules = excludes.get(dep -> current.resolvedFromArtifact).getOrElse {
            Seq.empty
          }
          dependencyActors ! DependenciesFor(current, rules)
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
      System.err.println("Got exception : " + e)
      errors += 1
      redrawAndcheckIfFinished
    }
    case u => {
      Deputy.fail("Got unexpected message : " + u)
    }
  }

}