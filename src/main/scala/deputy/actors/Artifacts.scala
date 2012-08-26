package deputy.actors

import akka.actor.Actor
import org.apache.ivy.core.settings.IvySettings
import java.net.URL
import org.apache.ivy.plugins.parser.m2.PomModuleDescriptorParser
import java.io.IOException
import org.apache.ivy.core.module.id.ModuleRevisionId
import akka.actor.ActorRef
import java.io.File
import deputy.models.Artifact
import deputy.models.Coord
import org.apache.ivy.core.module.descriptor.ModuleDescriptor
import deputy.Deputy
import deputy.logic._

class ArtifactsActor(settings: IvySettings, executor: ActorRef, printerActor: ActorRef, coordsActor: ActorRef) extends Actor {

  trait ActorArtifactsHandler extends ArtifactsHandler {
    override def dependenciesFound(nbOfDeps: Int): Unit = {
      executor ! DependenciesFound(nbOfDeps)
    }

    override def createArtifacts(coord: Coord, scopes: List[String], dependentArt: Option[Artifact], transitive: Boolean): Unit = {
      coordsActor ! CreateArtifacts(coord, scopes, dependentArt, transitive)
    }

    override def addExcludeRule(parent: Coord, id: String, excludeOrg: String, excludeNameOpt: Option[String]): Unit = {
      executor ! Exclude(parent, id, excludeOrg, excludeNameOpt)
    }

    val location = { artifact: Artifact => artifact.artifact }
  }

  val artifactsLogic = new Artifacts(settings) with ActorArtifactsHandler

  def receive = {
    case DependenciesFor(artifact, excludeRules) => {
      printerActor ! artifact
      Deputy.debug("depsFor:" + artifact)
      Executor.executeTask(executor) {
        artifactsLogic.depdenciesFor(artifact, excludeRules)
      }
    }
    case msg => {
      Deputy.fail("Artifacts actor got a unexpected messsage: " + msg)
    }
  }
}