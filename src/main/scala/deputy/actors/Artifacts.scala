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
    def dependenciesFound(nbOfDeps: Int): Unit = {
      executor ! DependenciesFound(nbOfDeps)
    }

    def createArtifacts(coord: Coord, dependentArt: Option[Artifact], transitive: Boolean): Unit = {
      coordsActor ! CreateArtifacts(coord, dependentArt, transitive)
    }
  }

  val artifactsLogic = new Artifacts(settings) with ActorArtifactsHandler

  def receive = {
    case artifact: Artifact => {
      printerActor ! artifact
      Deputy.debug("depsFor:" + artifact)
      Executor.executeTask(executor) {
        artifactsLogic.depdenciesFor(artifact)
      }

    }
  }
}