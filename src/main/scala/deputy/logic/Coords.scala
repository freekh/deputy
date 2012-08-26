package deputy.logic

import org.apache.ivy.plugins.version.VersionRangeMatcher
import org.apache.ivy.core.settings.IvySettings
import deputy.models.Artifact
import org.apache.ivy.plugins.resolver.RepositoryResolver
import org.apache.ivy.plugins.resolver.ChainResolver
import deputy.models.Coord
import java.util.Date
import org.apache.ivy.core.module.id.ModuleRevisionId
import org.apache.ivy.core.IvyPatternHelper
import org.apache.ivy.core.module.descriptor.DefaultArtifact
import org.apache.ivy.plugins.resolver.util.ResolverHelper
import deputy.Deputy

trait CoordsHandler {
  def dependencyResolved: Unit
  def wrapUpWithArtifact(artifact: Artifact, transitive: Boolean): Unit
}

object Coords {
  def acceptRevision(moduleOrg: String, moduleName: String, settings: IvySettings, a: String, b: String) = synchronized { //VersionRangeMatcher is not thread safe! I think we can live with synchronized since we are IO bound
    val vrm = new VersionRangeMatcher("range", settings.getDefaultLatestStrategy)
    vrm.accept(ModuleRevisionId.newInstance(moduleOrg, moduleName, a), ModuleRevisionId.newInstance(moduleOrg, moduleName, b))
  }

  def isDynamicVersion(moduleOrg: String, moduleName: String, settings: IvySettings, a: String) = synchronized {
    val vrm = new VersionRangeMatcher("range", settings.getDefaultLatestStrategy)
    vrm.isDynamic(ModuleRevisionId.newInstance(moduleOrg, moduleName, a))
  }
}

class Coords(settings: IvySettings) { handler: CoordsHandler => //TODO: rename Coords and all names like it to dependencies because that makes more sense to most people

  private def getRepositoryResolvers(resolvers: List[_]): List[RepositoryResolver] = {
    import scala.collection.JavaConversions._
    resolvers flatMap { //TODO: @tailrec

      case r: RepositoryResolver => List(r)
      case r: ChainResolver => getRepositoryResolvers(r.getResolvers.toList)
      //TODO: error msg handling case _ => throw UnsupportedResolver(ivy)  
    }
  }

  def createArtifacts(coord: Coord, scopes: List[String], dependentArtifactOpt: Option[Artifact], transitive: Boolean) = {
    val Coord(moduleOrg, moduleName, revision) = coord
    import scala.collection.JavaConversions._
    val pubDate = new Date() //???
    if (transitive) handler.dependencyResolved
    val printedArts = for {
      resolver <- getRepositoryResolvers(settings.getResolvers.toList).distinct if resolver.getName == "typesafe"
      pattern <- resolver.getIvyPatterns.map(_.toString)
      isIvy = !resolver.isM2compatible
    } yield {
      //sender ! PatternFound
      val moduleType = if (isIvy) "ivy" else "pom" //TODO: use DynamicTypes.ivy, ...

      val (module, artifact) = if (isIvy) {
        val ivyModule = ModuleRevisionId.newInstance(moduleOrg, moduleName, revision)
        ivyModule -> DefaultArtifact.newIvyArtifact(ivyModule, pubDate)
      } else {
        val pomModule = ModuleRevisionId.newInstance(moduleOrg.replace(".", "/"), moduleName, revision)
        pomModule -> DefaultArtifact.newPomArtifact(pomModule, pubDate)
      }
      val partiallyResolvedPattern = IvyPatternHelper.substitute(pattern, ModuleRevisionId
        .newInstance(module, IvyPatternHelper.getTokenString(IvyPatternHelper.REVISION_KEY)),
        artifact)

      val possibleRevs = if (Coords.isDynamicVersion(moduleOrg, moduleName, settings, revision)) {
        ResolverHelper.listTokenValues(resolver.getRepository, partiallyResolvedPattern,
          IvyPatternHelper.REVISION_KEY)
      } else Array(revision)

      val revs = if (Deputy.latestVersion) {
        possibleRevs.sorted.lastOption.toList
      } else {
        possibleRevs.toList
      }

      val arts = for {
        currentRev <- revs.toList if !Coords.isDynamicVersion(moduleOrg, moduleName, settings, revision) || Coords.acceptRevision(moduleOrg, moduleName, settings, revision, currentRev)
      } yield {
        val url = IvyPatternHelper.substituteToken(partiallyResolvedPattern,
          IvyPatternHelper.REVISION_KEY, currentRev)
        val c = Coord(moduleOrg, moduleName, currentRev)
        val finalArt = Artifact(Some(c), Some(url), Some(moduleType), scopes, dependentArtifactOpt.flatMap(_.artifact))
        handler.wrapUpWithArtifact(finalArt, transitive)
      }
    }
  }
}