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

class PruneVersions(settings: IvySettings) {

  def extractHighestVersions(lines: List[String]) = {
    val all = lines.map(ResolvedDep.parse)
    val highestVersions = {
      val highestStrategy = settings.getDefaultLatestStrategy
      val versionsMut = collection.mutable.Map.empty[(String, String, String), String]
      all.map { rd =>
        val dep = rd.dep
        val key = (dep.moduleOrg, dep.moduleName, rd.resolverName)
        val latest = versionsMut.get(key).map { lastRev =>
          val last = new MRIDArtifactInfo(ModuleRevisionId.newInstance(dep.moduleOrg, dep.moduleName, lastRev));
          val current = new MRIDArtifactInfo(ModuleRevisionId.newInstance(dep.moduleOrg, dep.moduleName, dep.revision));
          val found = highestStrategy.findLatest(Array(current, last), null).getRevision
          Deputy.debug(dep.moduleOrg + ":" + dep.moduleName + " who wins: " + lastRev + " VS " + dep.revision + ". " + found + " WINS!")
          found
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
          val key = (moduleOrg, moduleName, child.rd.resolverName)
          val keep = highestVersions(key) == revision //we assume on purpose that the key is present - if this fails we want to get the stacktrace
          if (!keep) Deputy.debug("Pruning: " + child.rd.dep)
          else Deputy.debug("Keeping: " + child.rd.dep)
          keep
        }
        //System.err.println("filtered children: " + children)
        Node(n.rd, children = highestVersionNodes(children))
      }
    }

    Graph.withArtifactsFrom(Graph.flatten(highestVersionNodes(Graph.create(all))), all).foreach { rd =>
      Deputy.out.println(rd.format)
    }

  }

}