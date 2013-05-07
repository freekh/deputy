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
  help                       TODO: print help for a command


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
    throw new Exception("Catastrophic failure!") 
  }
  
  //this fixes the config issue in conscript
  val actorSystem = ActorSystem("deputy", com.typesafe.config.ConfigFactory.load("akka.conf"), ActorSystem.getClass.getClassLoader)


  

  /**
   * Shared by the launched version and the runnable version,
   * returns the process status code
   */
  def run(args: Array[String]): Int = {

    val reader = new BufferedReader(new InputStreamReader(System.in));

    @tailrec def commandLineLoop(lines: List[String]): List[String] = {
      val line = reader.readLine()
      if (line != null) commandLineLoop(line :: lines)
      else lines
    }

    //TODO: FIX ENTIRE COMMAND LINE OPTION PARSING - THIS SUCKS

    if (args.contains("--debug")) {
      debugMode = true
    }

    def option(name: String) = {
      args.find(_.startsWith(name+"=")).map { param =>
      param.split(name+"=")(1)
      }
    }
    val availableCommands = List("deps-resolved", "resolved-highest-versions", "resolved-transitive", "resolved-treeprint", "resolved-results", "results-download-file")
    val List(resolveCommand, highestVersionsCommand, explodeCommand, treePrintCommand, resolvedResultsCommand, downloadResultsCommand) = availableCommands

    val ivySettingsPath = option("--ivy-settings")

    val resolverName = option("--resolver")

    val quick = args.find(_.startsWith("--quick")).map { _ =>
      true
    }.getOrElse { false }

    val nocolors = args.find(_.startsWith("--nocolors")).map { _ =>
      true
    }.getOrElse { false }


    
    val grepExpr = option("--grep").map( expr => expr -> expr)
    
    val grepExludeExpr = option("--grep-v").map( expr => "^(?!.*" + expr + ").*$" -> expr ) 
      
      
    val grepExprs = (grepExpr.toList ++ grepExludeExpr.toList).map {
      case (trans, orig) =>
        try {
          trans.r
        } catch {
          case e: java.util.regex.PatternSyntaxException =>
            val transformedMsg = if (orig != trans) " It was transformed to: '" + trans + "'." else ""
            Deputy.fail("Error: grep expr: '" + orig + "' failed!" + transformedMsg + " This is the details: " + e.getDescription)
        }
    }

    //WARNING THIS WILL DISABLE writes to System.out
    lazy val disableOut = true
    if (disableOut) {
      Deputy.debug("DISABLING SYSTEM.OUT")
      System.setOut(new PrintStream(new OutputStream() {
        override def write(b: Int) = {}
      }))
    }

    val ivy = {
      val ivy = IvyContext.getContext.getIvy //TODO: is this right?
      ivySettingsPath.foreach { ivySettingsPath =>
        val ivySettingsFile = new File(ivySettingsPath)
        if (!ivySettingsFile.isFile) {
          System.err.println("Cannot find ivy settings xml file in path: " + ivySettingsPath + " ...") //TODO: throw expectedexception instead?
          //System.exit(-1)
        } else {
          ivy.configure(ivySettingsFile)
        }
      }
      ivy
    }

    
    val res = args.lastOption.map(command => {
      if (args.contains("--version")) {
        Deputy.out.println(io.Source.fromInputStream(this.getClass.getClassLoader.getResourceAsStream("version")).mkString(""))
        
        0
      } else if (args.contains("--help") || args.contains("-h")) {
        Deputy.out.println(help)
        0
      } else if (command == resolveCommand) {
        (new ForkJoiner(ivy.getSettings, commandLineLoop(List()), resolverName, quick, grepExprs)).resolveDependencies()
        0
      } else if (command == explodeCommand) {
        //TODO: use dep as input instead - this way it is cleaner since parsing is all handled here
        (new ForkJoiner(ivy.getSettings, commandLineLoop(List()), resolverName, quick, grepExprs)).findDependencies()
        0
      } else if (command == highestVersionsCommand) {
        //TODO: use resolvedDep as input instead - this way it is cleaner since parsing is all handled here
        (new PruneVersions(ivy.getSettings)).extractHighestVersions(commandLineLoop(List()))
        0
      } else if (command == treePrintCommand) {
        val lines = commandLineLoop(List())
        val deps = lines.map(ResolvedDep.parse)
        PrettyPrinters.treePrint(Graph.create(deps), colors = !nocolors)
        0
      } else if (command == resolvedResultsCommand) {
        Results.fromResolved(commandLineLoop(List()).map(ResolvedDep.parse))
        0
      } else if (command == downloadResultsCommand) {
        Results.download(commandLineLoop(List()).map(Result.parse))
        0
      } else {
        System.err.println("Unknown command: " + command + ". Type deputy --help to learn about the commands.")
        -1
      }
    }).getOrElse {
      System.err.println("You must specify a command! Type deputy --help to learn about the commands.")
      -1
    }
    actorSystem.shutdown()
    Http.shutdown
    
    res
  }
  /** Standard runnable class entrypoint */
  def main(args: Array[String]) {
    System.exit(run(args))
  }
}

