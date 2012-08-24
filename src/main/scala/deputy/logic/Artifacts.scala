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
}

class Artifacts(settings: IvySettings) { handler: ArtifactsHandler =>
  def depdenciesFor(artifact: Artifact) = {
    val location = artifact.artifact
    val urlOpt = location.flatMap { l =>
      if (l.startsWith("file")) {
        val f = new File(l)
        if (f.exists) {
          Some(f.toURI.toURL)
        } else {
          None
        }
      } else {
        Some(new URL(l))
      }
    }

    urlOpt.foreach { artifactUrl =>
      val pomParser = PomModuleDescriptorParser.getInstance
      val pomDescr = pomParser.parseDescriptor(settings, artifactUrl, false)

      val deps = pomDescr.getDependencies

      handler.dependenciesFound(deps.size)
      deps.map { depDescr =>
        val dep = depDescr.getDependencyRevisionId

        Deputy.debug(depDescr.getModuleConfigurations().toList.map(_.toString).toString)
        val c = Coord(dep.getOrganisation, dep.getName, dep.getRevision)
        val conf = depDescr.getModuleConfigurations()
        val transitive = !conf.contains("optional") && (conf.contains("compile") || conf.contains("runtime")) //TODO: config
        val excludeRules = depDescr.getExcludeRules(conf)
        val excludeCoord = excludeRules.forall {
          case r =>
            r.getId().getModuleId().getName() == dep.getName &&
              r.getId().getModuleId().getOrganisation == dep.getOrganisation
        }
        if (excludeCoord) {
          Deputy.debug("resolving:" + c)
          handler.createArtifacts(c, Some(artifact), transitive)
        } else {
          Deputy.debug("excluding: " + c)
        }

      }
    }
  }
}