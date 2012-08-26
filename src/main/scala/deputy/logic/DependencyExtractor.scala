package deputy.logic

import deputy.models.Dependency
import deputy.models.ResolvedDep
import org.apache.ivy.core.settings.IvySettings
import java.io.File
import java.net.URL
import org.apache.ivy.plugins.parser.m2.PomModuleDescriptorParser
import deputy.Deputy

trait DependencyExtractorHandler {
  def dependenciesFound(nbOfDeps: Int): Unit
  def resolveDependency(dep: Dependency, scopes: List[String], dependentArt: Option[ResolvedDep], transitive: Boolean): Unit
  def addExcludeRule(parent: Dependency, id: String, excludeOrg: String, excludeNameOpt: Option[String]): Unit
}

class DependencyExtractor(settings: IvySettings) { handler: DependencyExtractorHandler =>
  protected def location(a: ResolvedDep) = a.artifact

  def dependenciesFor(parent: ResolvedDep, excludeRules: Seq[(String, Option[String])]) = {

    val urlOpt = location(parent).flatMap { l =>
      if (l.startsWith("file")) {
        val url = new URL(l)
        val f = new File(url.getFile)
        if (f.exists) {
          Some(l -> url)
        } else {
          None
        }
      } else {
        Some(l -> new URL(l))
      }
    }
    urlOpt.foreach {
      case (location, resolvedDepUrl) =>
        val pomParser = PomModuleDescriptorParser.getInstance
        val pomDescr = pomParser.parseDescriptor(settings, resolvedDepUrl, false)

        val deps = pomDescr.getDependencies

        handler.dependenciesFound(deps.size)
        val filteredDeps = deps.filter { depDescr =>
          val dep = depDescr.getDependencyRevisionId
          !excludeRules.contains(dep.getOrganisation -> Option(dep.getName))
        }

        filteredDeps.map { depDescr =>
          val dep = depDescr.getDependencyRevisionId
          val newDep = Dependency(dep.getOrganisation, dep.getName, dep.getRevision)

          val conf = depDescr.getModuleConfigurations()
          val optional = conf.contains("optional") //TODO: config
          println(optional + " - " + conf.toList + "    >>  " + newDep)
          if (!optional) {
            val currentExcludeRules = depDescr.getExcludeRules(conf)
            val scopes = conf.toList.map(_.toString)

            currentExcludeRules.map { rule =>
              handler.addExcludeRule(newDep, location, rule.getId.getModuleId.getOrganisation, Some(rule.getId.getModuleId.getName))
            }
            Deputy.debug("resolving:" + newDep)
            handler.resolveDependency(newDep, scopes, Some(parent), true)
          } else {
            Deputy.debug("skipping: " + newDep + " because optional")

          }

        }
    }
  }
}