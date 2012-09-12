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
import scala.util.matching.Regex

class DependencyResolver(settings: IvySettings, quick: Boolean, grepExprs: List[Regex]) {

  def getRepositoryResolvers(resolvers: List[_]): List[RepositoryResolver] = {
    import scala.collection.JavaConversions._
    resolvers flatMap { //TODO: @tailrec

      case r: RepositoryResolver => List(r)
      case r: ChainResolver => getRepositoryResolvers(r.getResolvers.toList)
      //TODO: error msg handling case _ => throw UnsupportedResolver(ivy)  
    }
  }

  private def versionRangeMatcher() = {
    val id = if (!quick) System.currentTimeMillis.toString //trick to override latest version only needed to display all possible versions of something
    else "range"
    new VersionRangeMatcher(id, settings.getDefaultLatestStrategy)
  }

  def acceptRevision(moduleOrg: String, moduleName: String, a: String, b: String) = synchronized { //VersionRangeMatcher is not thread safe! I think we can live with synchronized since we are IO bound
    versionRangeMatcher().accept(ModuleRevisionId.newInstance(moduleOrg, moduleName, a), ModuleRevisionId.newInstance(moduleOrg, moduleName, b))
  }

  def isDynamicVersion(moduleOrg: String, moduleName: String, a: String) = synchronized {
    versionRangeMatcher().isDynamic(ModuleRevisionId.newInstance(moduleOrg, moduleName, a))
  }

  def getJars(artifact: Artifact, parent: ResolvedDep) = {
    import scala.collection.JavaConversions._
    (for {
      resolver <- getRepositoryResolvers(settings.getResolvers.toList).filter(_.getRepository.getName == parent.resolverName)
      pattern <- resolver.getArtifactPatterns.map { _.toString }
    } yield {
      val fixedPattern = {
        if (resolver.isM2compatible) {
          IvyPatternHelper.substituteToken(pattern, IvyPatternHelper.ORGANISATION_KEY, artifact.getId.getModuleRevisionId.getOrganisation.replace(".", "/"))
        } else {
          pattern
        }
      }

      IvyPatternHelper.substitute(fixedPattern, artifact)
    }).distinct
  }

  //TODO: I am not very proud of this mutable. can we find another way?
  private val revisions = collection.mutable.Map.empty[(String, String, Option[String]), String]
  def resolveDependency(dep: Dependency, scopes: List[String], parentPath: Option[String], resolverName: Option[String]) = try {
    import deputy.Constants._
    import scala.collection.JavaConversions._

    val Dependency(moduleOrg, moduleName, revision) = dep

    //TODO: duplicate code in highest versions
    val highestStrategy = settings.getDefaultLatestStrategy
    def isNewestVersion(currentRev: String) = {
      val key = (moduleOrg, moduleName, resolverName)
      revisions.get(key).map { lastRev =>
        val lastArt = new MRIDArtifactInfo(ModuleRevisionId.newInstance(dep.moduleOrg, dep.moduleName, lastRev));
        val currentArt = new MRIDArtifactInfo(ModuleRevisionId.newInstance(dep.moduleOrg, dep.moduleName, currentRev));
        val foundLatest = highestStrategy.findLatest(Array(currentArt, lastArt), null).getRevision
        val isNewest = currentRev == foundLatest
        if (isNewest) revisions += key -> foundLatest //WARNING MUTATE!
        if (!isNewest) Deputy.debug("Skipping: " + dep + " because a newer version was found: " + foundLatest)
        isNewest
      }.getOrElse {
        revisions += key -> currentRev //WARNING MUTATE!
        true
      }
    }

    def useResolver(rn: String) = {
      resolverName.map { crn =>
        crn == rn
      }.getOrElse {
        true
      }
    }

    val dummyPubDate = new Date() //just a dummy - we are not going to publish either way
    val resolvedDeps = for {
      resolver <- getRepositoryResolvers(settings.getResolvers.toList).distinct if useResolver(resolver.getName)
      pattern <- resolver.getIvyPatterns.map(_.toString)
      isPom <- List(resolver.isM2compatible, false).distinct
    } yield {
      val (module, artifact) = if (resolver.isM2compatible) {
        val pomModule = ModuleRevisionId.newInstance(moduleOrg.replace(".", "/"), moduleName, revision) //TODO: / works on windows?
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

      val revsOpt = possibleRevsOpt.map { revs =>
        if (quick) {
          revs.filter(rev => isNewestVersion(rev))
        } else {
          revs
        }
      }

      val potentials = for {
        revs <- possibleRevsOpt.toList
        currentRev <- revs.toList if (!quick || isNewestVersion(currentRev)) && (!isDynamicVersion(moduleOrg, moduleName, revision) || (isDynamicVersion(moduleOrg, moduleName, revision) && acceptRevision(moduleOrg, moduleName, revision, currentRev)))
      } yield {
        val path = IvyPatternHelper.substituteToken(partiallyResolvedPattern,
          IvyPatternHelper.REVISION_KEY, currentRev)
        val newDep = Dependency(moduleOrg, moduleName, currentRev)
        val moduleType = if (path.endsWith(DependencyTypes.pom)) DependencyTypes.pom else DependencyTypes.ivy
        ResolvedDep(newDep, moduleType, resolver.getName, scopes, path, parentPath)
      }

      potentials.filter { rd =>
        val line = rd.format
        grepExprs.foldLeft(true)((last, regExpr) => last && regExpr.findFirstIn(line).isDefined)
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