package deputy.logic

import deputy.models.ResolvedDep
import deputy.Deputy
import deputy.Constants.DependencyTypes
import scala.collection.mutable.ListBuffer
import scala.collection.mutable.Buffer
import org.apache.ivy.core.settings.IvySettings
import org.apache.ivy.plugins.version.VersionRangeMatcher
import org.apache.ivy.core.module.id.ModuleRevisionId
import org.apache.ivy.plugins.latest.ArtifactInfo
import deputy.models.Dependency
import deputy.logic.Graph.Node

class MRIDArtifactInfo(val id: ModuleRevisionId) extends ArtifactInfo {
  override def getLastModified() = 0
  override def getRevision() = id.getRevision()
}

class HighestVersions(settings: IvySettings) {

  def extractHighestVersions(lines: List[String], resolverName: Option[String]) = {
    val all = lines.map { l =>
      val rd = ResolvedDep.parse(l)
      rd
    }

    val highestVersions = {
      val highestStrategy = settings.getDefaultLatestStrategy
      val versionsMut = collection.mutable.Map.empty[(String, String), String]
      all.map { rd =>
        val dep = rd.dep
        val key = (dep.moduleOrg, dep.moduleName)
        val latest = versionsMut.get(key).map { lastRev =>
          val last = new MRIDArtifactInfo(ModuleRevisionId.newInstance(dep.moduleOrg, dep.moduleName, lastRev));
          val current = new MRIDArtifactInfo(ModuleRevisionId.newInstance(dep.moduleOrg, dep.moduleName, dep.revision));

          highestStrategy.findLatest(Array(current, last), null).getRevision

        }.getOrElse {
          dep.revision
        }
        versionsMut += key -> latest
      }
      versionsMut.toMap
    }

    def highestVersionNodes(nodes: Set[Node]): Set[Node] = { //TODO: tailrec?
      nodes.map { n =>
        val children = n.children.filter { child =>
          val Dependency(moduleOrg, moduleName, revision) = child.rd.dep
          highestVersions(moduleOrg -> moduleName) == revision //we assume on purpose that the key is present
        }
        Node(n.rd, children = highestVersionNodes(children))
      }
    }

    val highestDeps = Graph.flatten(highestVersionNodes(Graph.create(all)))

    val resolvedMap = all.groupBy(_.resolvedFromArtifact)
    System.err.println(resolvedMap)
    val highestDepsWithArtifacts = highestDeps.flatMap { descrDep =>
      descrDep +: resolvedMap.get(Some(descrDep.path)).flatten.toList
    }.distinct

    highestDepsWithArtifacts.foreach { rd =>
      Deputy.out.println(rd.format)
    }

  }

}