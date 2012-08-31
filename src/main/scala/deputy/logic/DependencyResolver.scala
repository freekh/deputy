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
import org.apache.ivy.plugins.matcher.Matcher

object DependencyResolver {
  private def versionRangeMatcher(settings: IvySettings) = {
    val id = if (Deputy.latestVersion) System.currentTimeMillis.toString //trick to override latest version only needed to display all possible versions of something
    else "range"
    new VersionRangeMatcher(id, settings.getDefaultLatestStrategy)
  }

  def acceptRevision(moduleOrg: String, moduleName: String, settings: IvySettings, a: String, b: String) = synchronized { //VersionRangeMatcher is not thread safe! I think we can live with synchronized since we are IO bound
    versionRangeMatcher(settings).accept(ModuleRevisionId.newInstance(moduleOrg, moduleName, a), ModuleRevisionId.newInstance(moduleOrg, moduleName, b))
  }

  def isDynamicVersion(moduleOrg: String, moduleName: String, settings: IvySettings, a: String) = synchronized {
    versionRangeMatcher(settings).isDynamic(ModuleRevisionId.newInstance(moduleOrg, moduleName, a))
  }
}

class DependencyResolver(settings: IvySettings) {
  private def getRepositoryResolvers(resolvers: List[_]): List[RepositoryResolver] = {
    import scala.collection.JavaConversions._
    resolvers flatMap { //TODO: @tailrec

      case r: RepositoryResolver => List(r)
      case r: ChainResolver => getRepositoryResolvers(r.getResolvers.toList)
      //TODO: error msg handling case _ => throw UnsupportedResolver(ivy)  
    }
  }

  def resolveDependency(dep: Dependency, scopes: List[String], parentPath: Option[String], resolverName: Option[String]) = {
    import deputy.Constants._
    import scala.collection.JavaConversions._

    def useResolver(rn: String) = {
      resolverName.map { crn =>
        crn == rn
      }.getOrElse {
        true
      }
    }

    val Dependency(moduleOrg, moduleName, revision) = dep
    val pubDate = new Date() //???

    val resolvedDeps = for {
      resolver <- getRepositoryResolvers(settings.getResolvers.toList).distinct if useResolver(resolver.getName)
      pattern <- resolver.getIvyPatterns.map(_.toString)
      isPom = resolver.isM2compatible
    } yield {
      val moduleType = if (isPom) DependencyTypes.pom else DependencyTypes.ivy

      val (module, artifact) = if (isPom) {
        val pomModule = ModuleRevisionId.newInstance(moduleOrg.replace(".", "/"), moduleName, revision)
        pomModule -> DefaultArtifact.newPomArtifact(pomModule, pubDate)
      } else {
        val ivyModule = ModuleRevisionId.newInstance(moduleOrg, moduleName, revision)
        ivyModule -> DefaultArtifact.newIvyArtifact(ivyModule, pubDate)
      }
      val partiallyResolvedPattern = IvyPatternHelper.substitute(pattern, ModuleRevisionId
        .newInstance(module, IvyPatternHelper.getTokenString(IvyPatternHelper.REVISION_KEY)),
        artifact)

      val possibleRevsOpt = if (DependencyResolver.isDynamicVersion(moduleOrg, moduleName, settings, revision)) {
        Option(ResolverHelper.listTokenValues(resolver.getRepository, partiallyResolvedPattern,
          IvyPatternHelper.REVISION_KEY))
      } else Some(Array(revision))

      val revsOpt = possibleRevsOpt.map { possibleRevs =>
        if (Deputy.latestVersion) {
          possibleRevs.sorted.lastOption.toList
        } else {
          possibleRevs.toList
        }
      }

      for {
        revs <- revsOpt.toList
        currentRev <- revs.toList if (!DependencyResolver.isDynamicVersion(moduleOrg, moduleName, settings, revision) || (DependencyResolver.isDynamicVersion(moduleOrg, moduleName, settings, revision) && DependencyResolver.acceptRevision(moduleOrg, moduleName, settings, revision, currentRev)))
      } yield {
        val path = IvyPatternHelper.substituteToken(partiallyResolvedPattern,
          IvyPatternHelper.REVISION_KEY, currentRev)
        val newDep = Dependency(moduleOrg, moduleName, currentRev)
        ResolvedDep(newDep, moduleType, resolver.getName, scopes, path, parentPath)
      }
    }
    resolvedDeps.flatten
  }
}