package deputy

import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.PrintStream
import org.apache.ivy.core.IvyContext
import scala.annotation.tailrec
import deputy.logic.PruneVersions
import deputy.models.ResolvedDep
import deputy.logic.Graph
import deputy.logic.Results
import deputy.models.Result
import akka.actor.ActorSystem
import dispatch.Http

/** The launched conscript entry point */
class App extends xsbti.AppMain {
  def run(config: xsbti.AppConfiguration) = {
     Exit(Deputy.run(config.arguments))
  }
}

object Deputy {
  val help = """    
                             .
                            ,O,
                           ,OOO,
                      'ooooO   Oooooo'
                       `O DEPUTY! O`
                         `O     O`
                         OOOO'OOOO
                        OOO'   'OOO
                       O'         'O
    
SYNOPSIS:
  Deputy stands for dependency utility and is a command line tool that helps you inspect ivy and maven dependencies.
  It works by piping from one command to the next.

USAGE:
  deputy <options> <command>

EXAMPLE:
  # Prints out a tree of ALL dependencies (does not prune any version) that fredrik.ekholdt:deputy:0.1.3 transitively depends on
  echo fredrik.ekholdt:deputy:0.1.3 | deputy deps-resolved | deputy resolved-transtive | deputy resolved-results | deputy results-download-file
    
COMMANDS:
  deps-resolved              transform from deps (format: org:name:version) to resolved (format: 
                             <org:name:version>|<type>|<resolver>|<conf>|<path>|<resolved-from>|)

  resolved-transitive        transitively find dependencies
  resolved-highest-versions  remove dependencies where there exists a higher version        
  resolved-treeprint         print a tree representing the dependencies
  resolved-results           transform from resolved (format: 
                             <org:name:version>|<type>|<resolver>|<conf>|<path>|<resolved-from>|) 
                             to results (format:<org:name:version>#<type>=<uri>,<uri>,...)
  
  results-download-file      download results and print filename 
    
OPTIONS:
  --quick                    skip lower versioned dependencies that ivy also would skip when resolving
  --resolver=<RESOLVERNAME>  use only the resolver RESOLVERNAME 
  --ivy-settings=<FILE>      use the xml ivy settings file in FILE. Defaults to: ivy-settings.xml
  --grep=<REGEXP>            use only dependencies (and the ones below in the dependency graph) 
                             that matches REGEXP
  --grep-v=<REGEXP>          prune away dependencies (and the ones below in the dependency graph) 
                             that matches REGEXP
    
  --nocolors                 do not use colors
  --version                  prints version and exits
  --help,-h                  prints this help
  --debug                    emit debug to stderr
    
COOKBOOK:
  Check out the homepage to learn more about usage: http://github.com/freekh/deputy"""

  val out = System.out

  var debugMode = false
  def debug(s: => String) = {
    if (debugMode)
      System.err.println(s)
  }

  val exitOnFail = true
  def fail(s: String) = {
    System.err.println(s)
    throw new Exception("Catastrophic failure!") //return type
  }

  lazy val actorSystem = ActorSystem("deputy")

  /**
   * Shared by the launched version and the runnable version,
   * returns the process status code
   */
  def run(args: Array[String]): Int = {
    println("0.2.0.7")
    0
  }
  /** Standard runnable class entrypoint */
  def main(args: Array[String]) {
    System.exit(run(args))
  }
}

case class Exit(val code: Int) extends xsbti.Exit
