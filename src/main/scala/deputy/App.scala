package deputy

import akka.actor.ActorSystem
import akka.actor.Props
import akka.dispatch.Await
import akka.pattern.Patterns
import akka.util.Timeout.durationToTimeout
import akka.util.Duration
import dispatch.Http
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.PrintStream
import org.apache.ivy.core.IvyContext
import deputy.actors.CoordsWithResolvers
import deputy.actors.Executor
import deputy.actors.Explode
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

  val latestVersion = true

  /**
   * Shared by the launched version and the runnable version,
   * returns the process status code
   */
  def run(args: Array[String]): Int = {
    //  echo "org.apache.ivy:ivy:2.2.20" | deputy with-resolvers /file/ivy-settings.xml | deputy check --keep-all | deputy explode |  deputy download --format="test/[mavenorg]" /directory  # or grep jar | cut -d '|' -f 3 | xargs curl 

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

    val availableCommands = List("coords-artifacts", "artifacts-resolve", "artifacts-transitive", "artifacts-results")
    val List(resolverCommand, checkCommand, explodeCommand, resultsCommand) = availableCommands

    val ivySettingsPath = args.find(_ == "ivy-settings").flatMap { ivySettingsParam =>
      if (args.size > args.indexOf(ivySettingsParam))
        Some(args(args.indexOf(ivySettingsParam)))
      else
        None
    }.getOrElse {
      "ivy-settings.xml" //TODO: different default?
    }
    val ivySettingsFile = new File(ivySettingsPath)
    if (!ivySettingsFile.isFile) {
      System.err.println("Cannot find ivy settings xml file in path: " + ivySettingsPath + " ...") //TODO: throw expectedexception instead?
      System.exit(-1)
    }

    //WARNING THIS WILL DISABLE write to System.out
    lazy val disableOut = true
    if (disableOut)
      System.setOut(new PrintStream(new OutputStream() {
        override def write(b: Int) = {}
      }))

    val ivy = {
      val ivy = IvyContext.getContext.getIvy
      ivy.configure(ivySettingsFile)
      ivy
    }

    val actorSystem = ActorSystem("deputy")
    val executor = actorSystem.actorOf(Props(new Executor(ivy.getSettings)))

    val res = args.headOption.map(command => {
      if (command == resolverCommand) {
        Await.result(Patterns.ask(executor, CoordsWithResolvers(commandLineLoop(List())), Duration.parse("5 minutes")), Duration.parse("5 minutes"))
        0
      } else if (command == explodeCommand) {
        Await.result(Patterns.ask(executor, Explode(commandLineLoop(List())), Duration.parse("5 minutes")), Duration.parse("5 minutes"))
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
    Http.shutdown
    actorSystem.shutdown
    res
  }
  /** Standard runnable class entrypoint */
  def main(args: Array[String]) {
    System.exit(run(args))
  }
}

case class Exit(val code: Int) extends xsbti.Exit
