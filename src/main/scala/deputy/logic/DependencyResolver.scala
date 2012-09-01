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
import org.apache.ivy.core.module.descriptor.Artifact

class DependencyResolver(settings: IvySettings, uniqueVersion: Boolean) {

  def getRepositoryResolvers(resolvers: List[_]): List[RepositoryResolver] = {
    import scala.collection.JavaConversions._
    resolvers flatMap { //TODO: @tailrec

      case r: RepositoryResolver => List(r)
      case r: ChainResolver => getRepositoryResolvers(r.getResolvers.toList)
      //TODO: error msg handling case _ => throw UnsupportedResolver(ivy)  
    }
  }

  private def versionRangeMatcher() = {
    val id = if (!uniqueVersion) System.currentTimeMillis.toString //trick to override latest version only needed to display all possible versions of something
    else "range"
    new VersionRangeMatcher(id, settings.getDefaultLatestStrategy)
  }

  def acceptRevision(moduleOrg: String, moduleName: String, a: String, b: String) = synchronized { //VersionRangeMatcher is not thread safe! I think we can live with synchronized since we are IO bound
    versionRangeMatcher().accept(ModuleRevisionId.newInstance(moduleOrg, moduleName, a), ModuleRevisionId.newInstance(moduleOrg, moduleName, b))
  }

  def isDynamicVersion(moduleOrg: String, moduleName: String, a: String) = synchronized {
    versionRangeMatcher().isDynamic(ModuleRevisionId.newInstance(moduleOrg, moduleName, a))
  }

  def getJars(artifact: Artifact, resolverName: String) = {
    import scala.collection.JavaConversions._
    (for {
      resolver <- getRepositoryResolvers(settings.getResolvers.toList).filter(_.getRepository.getName == resolverName)
      pattern <- resolver.getArtifactPatterns.map { _.toString }
    } yield {
      IvyPatternHelper.substitute(pattern, artifact)
    }).distinct
  }

  def resolveDependency(dep: Dependency, scopes: List[String], parentPath: Option[String], resolverName: Option[String]) = try {
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
    val dummyPubDate = new Date() //just a dummy - we are not going to publish either way

    val resolvedDeps = for {
      resolver <- getRepositoryResolvers(settings.getResolvers.toList).distinct if useResolver(resolver.getName)
      pattern <- resolver.getIvyPatterns.map(_.toString)
      isPom = resolver.isM2compatible
    } yield {
      val moduleType = if (isPom) DependencyTypes.pom else DependencyTypes.ivy

      val (module, artifact) = if (isPom) {
        val pomModule = ModuleRevisionId.newInstance(moduleOrg.replace(".", "/"), moduleName, revision)
        pomModule -> DefaultArtifact.newPomArtifact(pomModule, dummyPubDate)
      } else {
        val ivyModule = ModuleRevisionId.newInstance(moduleOrg, moduleName, revision)
        ivyModule -> DefaultArtifact.newIvyArtifact(ivyModule, dummyPubDate)
      }
      val partiallyResolvedPattern = IvyPatternHelper.substitute(pattern, ModuleRevisionId
        .newInstance(module, IvyPatternHelper.getTokenString(IvyPatternHelper.REVISION_KEY)),
        artifact)

      val possibleRevsOpt = if (isDynamicVersion(moduleOrg, moduleName, revision)) {
        Option(ResolverHelper.listTokenValues(resolver.getRepository, partiallyResolvedPattern,
          IvyPatternHelper.REVISION_KEY))
      } else Some(Array(revision))

      val revsOpt = possibleRevsOpt.map { possibleRevs =>
        if (Deputy.latestVersion) { //TODO: fix this 
          possibleRevs.sorted.lastOption.toList
        } else {
          possibleRevs.toList
        }
      }

      for {
        revs <- revsOpt.toList
        currentRev <- revs.toList if (!isDynamicVersion(moduleOrg, moduleName, revision) || (isDynamicVersion(moduleOrg, moduleName, revision) && acceptRevision(moduleOrg, moduleName, revision, currentRev)))
      } yield {
        val path = IvyPatternHelper.substituteToken(partiallyResolvedPattern,
          IvyPatternHelper.REVISION_KEY, currentRev)
        val newDep = Dependency(moduleOrg, moduleName, currentRev)
        ResolvedDep(newDep, moduleType, resolver.getName, scopes, path, parentPath)
      }
    }
    resolvedDeps.flatten
  } catch {
    case e => {
      Deputy.debug("Got exception : " + e + " while processing " + dep)
      Seq.empty
    }
  }
}