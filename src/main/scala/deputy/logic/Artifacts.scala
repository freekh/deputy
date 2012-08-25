package deputy.logic

import deputy.models.Coord
import deputy.models.Artifact
import org.apache.ivy.core.settings.IvySettings
import java.io.File
import java.net.URL
import org.apache.ivy.plugins.parser.m2.PomModuleDescriptorParser
import deputy.Deputy

trait ArtifactsHandler {
  def dependenciesFound(nbOfDeps: Int): Unit
  def createArtifacts(coord: Coord, dependentArt: Option[Artifact], transitive: Boolean): Unit
  def addExcludeRule(parent: Coord, id: String, excludeOrg: String, excludeNameOpt: Option[String]): Unit
}

class Artifacts(settings: IvySettings) { handler: ArtifactsHandler =>
  protected def location(a: Artifact) = a.artifact

  def depdenciesFor(artifact: Artifact, excludeRules: Seq[(String, Option[String])]) = {

    val urlOpt = location(artifact).flatMap { l =>
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
      case (location, artifactUrl) =>
        val pomParser = PomModuleDescriptorParser.getInstance
        val pomDescr = pomParser.parseDescriptor(settings, artifactUrl, false)

        val deps = pomDescr.getDependencies

        handler.dependenciesFound(deps.size)
        val filteredDeps = deps.filter { depDescr =>
          val dep = depDescr.getDependencyRevisionId
          !excludeRules.contains(dep.getOrganisation -> Option(dep.getName))
        }

        filteredDeps.map { depDescr =>
          val dep = depDescr.getDependencyRevisionId
          val c = Coord(dep.getOrganisation, dep.getName, dep.getRevision)

          val conf = depDescr.getModuleConfigurations()
          val transitive = !conf.contains("optional") //TODO: config

          val currentExcludeRules = depDescr.getExcludeRules(conf)
          //TODO: add support for scopes 

          currentExcludeRules.map { rule =>
            handler.addExcludeRule(c, location, rule.getId.getModuleId.getOrganisation, Some(rule.getId.getModuleId.getName))
          }

          Deputy.debug("resolving:" + c)
          handler.createArtifacts(c, Some(artifact), transitive)

        }
    }
  }
}