package deputy.logic

import deputy.models.ResolvedDep
import deputy.Constants._

object Graph {
  case class Node(rd: ResolvedDep, children: Set[Node] = Set.empty)
  def create(deps: Seq[ResolvedDep]) = {
    val filteredDeps = deps.filter { rd => rd.moduleType == DependencyTypes.pom || rd.moduleType == DependencyTypes.ivy }.toSet

    val lookup = filteredDeps.groupBy(_.resolvedFromArtifact) //TODO: should we use a buffer and remove the already used to improve perf?

    def getNodes(path: Option[String]): Set[Node] = { //TODO: find a way to make this tailrec?
      lookup.get(path).toSet.flatMap { children: Set[ResolvedDep] =>
        children.map { child =>
          Node(child, getNodes(Some(child.path)))
        }
      }
    }

    getNodes(None)
  }

  def flatten(nodes: Set[Node]): Seq[ResolvedDep] = {
    nodes.toSeq.flatMap { n =>
      n.rd +: flatten(n.children)
    }
  }

  def withArtifactsFrom(deps: Seq[ResolvedDep], all: Seq[ResolvedDep]) = {
    val resolvedMap = all.groupBy(_.resolvedFromArtifact)
    deps.flatMap { descrDep =>
      descrDep +: resolvedMap.get(Some(descrDep.path)).flatten.toList.filter(rd => !(rd.moduleType == DependencyTypes.pom || rd.moduleType == DependencyTypes.ivy))
    }.distinct
  }

}