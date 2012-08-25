package deputy.actors

import akka.actor.Actor
import org.apache.ivy.plugins.version.VersionRangeMatcher
import org.apache.ivy.core.settings.IvySettings
import org.apache.ivy.core.module.id.ModuleRevisionId
import java.util.Date
import org.apache.ivy.plugins.resolver.RepositoryResolver
import org.apache.ivy.plugins.resolver.ChainResolver
import org.apache.ivy.core.IvyPatternHelper
import deputy.models.Coord
import org.apache.ivy.core.module.descriptor.DefaultArtifact
import org.apache.ivy.plugins.resolver.util.ResolverHelper
import deputy.models.Artifact
import akka.actor.ActorRef
import deputy.Deputy
import deputy.logic._

case class CreateArtifacts(coords: Coord, dependentArtifactOpt: Option[Artifact], transitive: Boolean)

class CoordsActor(settings: IvySettings, executor: ActorRef, printerActor: ActorRef) extends Actor {

  trait ActorCoordsHandler extends CoordsHandler {
    def dependencyResolved: Unit = {
      executor ! DepedencyResolved
    }

    def wrapUpWithArtifact(artifact: Artifact, transitive: Boolean): Unit = {
      printerActor ! artifact
      //Deputy.debug("expanding:" + url)
      if (transitive) {
        //Deputy.debug("executorDeps:" + url)
        executor ! DependenciesFor(artifact, Seq.empty)
      }
    }
  }

  val coordsLogic = new Coords(settings: IvySettings) with ActorCoordsHandler

  def receive = {
    case CreateArtifacts(coord @ Coord(moduleOrg, moduleName, revision), dependentArtifactOpt, transitive) => {
      Executor.executeTask(executor) {
        coordsLogic.createArtifacts(coord, dependentArtifactOpt, transitive)
      }

    }
    case msg => {
      Deputy.fail("Coords actor got a unexpected messsage: " + msg)
    }
  }

}