package deputy

import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.PrintStream
import org.apache.ivy.core.IvyContext
import scala.annotation.tailrec

/** The launched conscript entry point */
class App extends xsbti.AppMain {
  def run(config: xsbti.AppConfiguration) = {
    Exit(Deputy.run(config.arguments))
  }
}

object Deputy {

  val out = System.out

  val progressMode = true
  def progress(s: String) = {
    if (progressMode)
      System.err.print(s)
  }
  val debugMode = false
  def debug(s: String) = {
    if (debugMode)
      System.err.println(s)
  }

  val latestVersion = false

  val exitOnFail = true
  def fail(s: String) = {
    System.err.println(s)
    if (exitOnFail) System.exit(-1)
    throw new Exception() //return type
  }

  /**
   * Shared by the launched version and the runnable version,
   * returns the process status code
   */
  def run(args: Array[String]): Int = {

    if (args.contains("--version")) {
      println("0.1.3") //TOOD: git hook
      System.exit(0)
    }

    val reader = new BufferedReader(new InputStreamReader(System.in));

    @tailrec def commandLineLoop(lines: List[String]): List[String] = {
      val line = reader.readLine()
      if (line != null) commandLineLoop(line :: lines)
      else lines
    }

    val availableCommands = List("deps-resolved", "resolved-check", "resolved-transitive", "resolved-results")
    val List(resolveCommand, checkCommand, explodeCommand, resultsCommand) = availableCommands

    val ivySettingsPath = args.find(_.startsWith("--ivy-settings=")).flatMap { param =>
      Some(param.split("--ivy-settings=")(1))
    }.getOrElse {
      "ivy-settings.xml" //TODO: different default?
    }
    val ivySettingsFile = new File(ivySettingsPath)
    if (!ivySettingsFile.isFile) {
      System.err.println("Cannot find ivy settings xml file in path: " + ivySettingsPath + " ...") //TODO: throw expectedexception instead?
      System.exit(-1)
    }
    val resolverName = args.find(_.startsWith("--resolver=")).map { param =>
      param.split("--resolver=")(1)
    }

    //WARNING THIS WILL DISABLE write to System.out
    lazy val disableOut = true
    if (disableOut)
      Deputy.debug("DISABLING SYSTEM.OUT")
    System.setOut(new PrintStream(new OutputStream() {
      override def write(b: Int) = {}
    }))

    val ivy = {
      val ivy = IvyContext.getContext.getIvy //TODO: is this right?
      ivy.configure(ivySettingsFile)
      ivy
    }

    val forkJoiner = new ForkJoiner(ivy.getSettings)

    val res = args.lastOption.map(command => {
      if (command == resolveCommand) {
        forkJoiner.resolveDependencies(commandLineLoop(List()), resolverName)
        0
      } else if (command == explodeCommand) {
        forkJoiner.findDependencies(commandLineLoop(List()), resolverName)
        //Thread.sleep(40000)
        0
      } else {
        System.err.println("Unknown command: " + command)
        -1
      }
    }).getOrElse {
      System.err.println("Provide a command!") //TODO:
      -1
    }
    //Http.shutdown
    res
  }
  /** Standard runnable class entrypoint */
  def main(args: Array[String]) {
    System.exit(run(args))
  }
}

case class Exit(val code: Int) extends xsbti.Exit
