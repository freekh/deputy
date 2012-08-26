package deputy.logic

import org.apache.ivy.plugins.version.VersionRangeMatcher
import org.apache.ivy.core.settings.IvySettings
import org.apache.ivy.plugins.resolver.RepositoryResolver
import org.apache.ivy.plugins.resolver.ChainResolver
import java.util.Date
import org.apache.ivy.core.module.id.ModuleRevisionId
import org.apache.ivy.core.IvyPatternHelper
import org.apache.ivy.core.module.descriptor.DefaultArtifact
import org.apache.ivy.plugins.resolver.util.ResolverHelper
import deputy.Deputy
import deputy.models.ResolvedDep
import deputy.models.Dependency

trait DependencyResolverHandler {
  def dependencyResolved: Unit
  def printThenContinue(resolvedDep: ResolvedDep, transitive: Boolean): Unit
}

object DependencyResolver {
  def acceptRevision(moduleOrg: String, moduleName: String, settings: IvySettings, a: String, b: String) = synchronized { //VersionRangeMatcher is not thread safe! I think we can live with synchronized since we are IO bound
    val vrm = new VersionRangeMatcher("range", settings.getDefaultLatestStrategy)
    vrm.accept(ModuleRevisionId.newInstance(moduleOrg, moduleName, a), ModuleRevisionId.newInstance(moduleOrg, moduleName, b))
  }

  def isDynamicVersion(moduleOrg: String, moduleName: String, settings: IvySettings, a: String) = synchronized {
    val vrm = new VersionRangeMatcher("range", settings.getDefaultLatestStrategy)
    vrm.isDynamic(ModuleRevisionId.newInstance(moduleOrg, moduleName, a))
  }
}

class DependencyResolver(settings: IvySettings) { handler: DependencyResolverHandler =>

  private def getRepositoryResolvers(resolvers: List[_]): List[RepositoryResolver] = {
    import scala.collection.JavaConversions._
    resolvers flatMap { //TODO: @tailrec

      case r: RepositoryResolver => List(r)
      case r: ChainResolver => getRepositoryResolvers(r.getResolvers.toList)
      //TODO: error msg handling case _ => throw UnsupportedResolver(ivy)  
    }
  }

  def resolveDependency(dep: Dependency, scopes: List[String], parent: Option[ResolvedDep], transitive: Boolean) = {
    val Dependency(moduleOrg, moduleName, revision) = dep
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

      val possibleRevs = if (DependencyResolver.isDynamicVersion(moduleOrg, moduleName, settings, revision)) {
        ResolverHelper.listTokenValues(resolver.getRepository, partiallyResolvedPattern,
          IvyPatternHelper.REVISION_KEY)
      } else Array(revision)

      val revs = if (Deputy.latestVersion) {
        possibleRevs.sorted.lastOption.toList
      } else {
        possibleRevs.toList
      }

      val arts = for {
        currentRev <- revs.toList if !DependencyResolver.isDynamicVersion(moduleOrg, moduleName, settings, revision) || DependencyResolver.acceptRevision(moduleOrg, moduleName, settings, revision, currentRev)
      } yield {
        val url = IvyPatternHelper.substituteToken(partiallyResolvedPattern,
          IvyPatternHelper.REVISION_KEY, currentRev)
        val newDep = Dependency(moduleOrg, moduleName, currentRev)
        val resolvedDep = ResolvedDep(Some(newDep), Some(url), Some(moduleType), scopes, parent.flatMap(_.artifact))
        handler.printThenContinue(resolvedDep, transitive)
      }
    }
  }
}