package deputy

import akka.actor.ActorSystem
import akka.actor.Props
import deputy.actors.PrinterActor
import deputy.logic.DependencyExtractor
import deputy.logic.DependencyResolver
import deputy.models.Dependency
import deputy.models.ResolvedDep
import org.apache.ivy.core.settings.IvySettings

class ForkJoiner(settings: IvySettings) {
  val actorSystem = ActorSystem("deputy")

  val printer = actorSystem.actorOf(Props(new PrinterActor(Deputy.out)))

  val resolver = new DependencyResolver(settings)

  val parLevel = 100
  collection.parallel.ForkJoinTasks.defaultForkJoinPool.setParallelism(parLevel)

  val extractor = new DependencyExtractor(settings)

  def resolveDependencies(lines: Seq[String], resolverName: Option[String]) = {
    lines.par.foreach { l => //NOTICE the par 
      resolver.resolveDependency(Dependency.parse(l), List.empty, None, resolverName).foreach { rd =>
        printer ! rd
      }
    }
  }

  def findDependencies(lines: Seq[String], resolverName: Option[String]) = {
    val rds = lines.map(ResolvedDep.parse)
    findAllDeps(rds, resolverName, Seq.empty, Seq.empty)
    //rds.foreach { rd => extractor.findDependencies(rd, Seq.empty).foreach { r => printer ! r._1 } }
  }

  private def findAllDeps(initRds: Seq[ResolvedDep], resolverName: Option[String], excludeRules: Seq[(String, Option[String])], alreadyResolved: Seq[String]): Seq[(ResolvedDep, Seq[(String, Option[String])])] = {
    val foundRdsAndExcludes = initRds.par.flatMap { rd =>
      extractor.findDependencies(rd, resolverName, excludeRules)
    }
    val foundRds = foundRdsAndExcludes.map(_._1).distinct

    val newRds = foundRds.filter(rd => !alreadyResolved.contains(rd.path))
    newRds.foreach { rd => printer ! rd }
    //System.err.println(foundRds.size + " VS " + alreadyResolved.size)
    if (newRds.nonEmpty) {
      findAllDeps(foundRds.toList, resolverName, foundRdsAndExcludes.map(_._2).flatten.toList, alreadyResolved ++ initRds.map(_.path) ++ newRds.map(_.path))
    } else {
      Seq.empty
    }
  }

}

