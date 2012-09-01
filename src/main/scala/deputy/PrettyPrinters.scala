package deputy

import deputy.logic.Graph._

object PrettyPrinters {
  def treePrint(nodes: Set[Node], colors: Boolean = true) = {
    def treePrint(nodes: Set[Node], level: Int, colors: Boolean = true): Unit = {
      nodes.foreach { n =>
        val dep = n.rd.dep
        val sep = if (colors) Console.YELLOW + ":" + Console.RESET else ":"
        def colorize(s: String) = Console.RESET + Console.BOLD + Console.BLACK_B + Console.WHITE + s + Console.RESET
        val moduleOrg = if (colors) colorize(dep.moduleOrg) else dep.moduleOrg
        val moduleName = if (colors) colorize(dep.moduleName) else dep.moduleName
        val revision = if (colors) colorize(dep.revision) else dep.revision
        val arrows = if (colors) Console.GREEN + ("   |" * level) + "-->" + Console.RESET else ("   |" * level) + "-->"
        val resolver = if (level == 0) " (" + n.rd.resolverName + ")" else ""
        System.err.println(arrows + moduleOrg + sep + moduleName + sep + revision + resolver)
        treePrint(n.children, level + 1)
      }
    }
    treePrint(nodes, level = 0)
  }
}