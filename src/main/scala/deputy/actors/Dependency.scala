package deputy.actors

import akka.actor.Actor
import org.apache.ivy.core.settings.IvySettings
import java.net.URL
import org.apache.ivy.plugins.parser.m2.PomModuleDescriptorParser
import java.io.IOException
import org.apache.ivy.core.module.id.ModuleRevisionId
import akka.actor.ActorRef
import java.io.File
import deputy.models.ResolvedDep
import deputy.models.Dependency
import org.apache.ivy.core.module.descriptor.ModuleDescriptor
import deputy.Deputy
import deputy.logic._

case class ResolveDep(dep: Dependency, scopes: List[String], parent: Option[ResolvedDep], transitive: Boolean)

class DependencyActor(settings: IvySettings, executor: ActorRef, printerActor: ActorRef) extends Actor {

  trait ActorDepdencyExctractorHandler extends DependencyExtractorHandler {
    override def dependenciesFound(nbOfDeps: Int): Unit = {
      executor ! DependenciesFound(nbOfDeps)
    }

    override def resolveDependency(dep: Dependency, scopes: List[String], parent: Option[ResolvedDep], transitive: Boolean): Unit = {
      executor ! ResolveDep(dep, scopes, parent, transitive)
    }

    override def addExcludeRule(parent: Dependency, id: String, excludeOrg: String, excludeNameOpt: Option[String]): Unit = {
      executor ! Exclude(parent, id, excludeOrg, excludeNameOpt)
    }
  }

  trait ActorDependencyResolverHandler extends DependencyResolverHandler {
    def dependencyResolved: Unit = {
      executor ! DepedencyResolved
    }

    def printThenContinue(resolvedDep: ResolvedDep, transitive: Boolean): Unit = {
      printerActor ! resolvedDep
      //Deputy.debug("expanding:" + url)
      if (transitive) {
        //Deputy.debug("executorDeps:" + url)
        executor ! DependenciesFor(resolvedDep, Seq.empty)
      }
    }
  }

  val depExtractor = new DependencyExtractor(settings) with ActorDepdencyExctractorHandler

  val depResolver = new DependencyResolver(settings: IvySettings) with ActorDependencyResolverHandler

  def receive = {
    case DependenciesFor(rd, excludeRules) => {
      Deputy.debug("depsFor:" + rd)
      ForkJoinHelper.executeTask(executor) {
        depExtractor.dependenciesFor(rd, excludeRules)
      }
    }
    case ResolveDep(dep @ Dependency(moduleOrg, moduleName, revision), scopes, parent, transitive) => {
      ForkJoinHelper.executeTask(executor) {
        depResolver.resolveDependency(dep, scopes, parent, transitive)
      }
    }
    case msg => {
      Deputy.fail("Dependency actor got a unexpected messsage: " + msg + ". This might mean that the current build of deputy is broken - please file a bug with this error msg")
    }
  }
}