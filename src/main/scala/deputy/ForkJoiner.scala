package deputy

import akka.actor.ActorSystem
import akka.actor.Props
import deputy.actors.PrinterActor
import deputy.logic.DependencyExtractor
import deputy.logic.DependencyResolver
import deputy.models.Dependency
import deputy.models.ResolvedDep
import org.apache.ivy.core.settings.IvySettings
import scala.annotation.tailrec

class ForkJoiner(settings: IvySettings, quick: Boolean) {
  val actorSystem = ActorSystem("deputy")

  val printer = actorSystem.actorOf(Props(new PrinterActor(Deputy.out)))

  val parLevel = 100
  collection.parallel.ForkJoinTasks.defaultForkJoinPool.setParallelism(parLevel)

  val extractor = new DependencyExtractor(settings, quick)

  def resolveDependencies(lines: Seq[String], resolverName: Option[String]) = {
    val resolver = new DependencyResolver(settings, quick)
    lines.par.foreach { l => //NOTICE the par 
      resolver.resolveDependency(Dependency.parse(l), List.empty, None, resolverName).foreach { rd =>
        printer ! rd
      }
    }
  }

  def findDependencies(lines: Seq[String], resolverName: Option[String]) = {
    val rds = lines.map(ResolvedDep.parse)
    rds.foreach { rd => printer ! rd }
    findAllDeps(rds, resolverName, Seq.empty, Set.empty)
  }

  @tailrec private def findAllDeps(initRds: Seq[ResolvedDep], resolverName: Option[String], excludeRules: Seq[(String, Option[String])], alreadyResolved: Set[String]): Seq[(ResolvedDep, Seq[(String, Option[String])])] = {
    val foundRdsAndExcludes = initRds.par.flatMap { rd =>
      val describedDeps = extractor.parseDescriptor(rd)
      describedDeps.flatMap {
        case (deps, artifacts) =>
          artifacts.foreach { artifact => printer ! artifact }

          extractor.convertToResolvedDep(deps, excludeRules, resolverName, rd)
      }
    }
    val foundRds = foundRdsAndExcludes.map(_._1).distinct //TODO: i am not sure we need this?

    val newRds = foundRds.filter(rd => !alreadyResolved.contains(rd.path))
    newRds.foreach { rd => printer ! rd }
    if (newRds.nonEmpty) {
      findAllDeps(foundRds.toList, resolverName, foundRdsAndExcludes.map(_._2).flatten.toList, alreadyResolved ++ initRds.map(_.path) ++ newRds.map(_.path))
    } else {
      Seq.empty
    }
  }

}

