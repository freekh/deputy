package org.deputy

import java.io.File
import org.apache.ivy.core.settings.IvySettings
import org.apache.ivy.plugins.resolver.DependencyResolver
import org.apache.ivy.Ivy
import org.apache.ivy.core.retrieve.RetrieveOptions
import org.apache.ivy.core.LogOptions
import org.deputy.resolvers.UrlResolver
import org.deputy.formatting._
import dispatch.Http
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.io.InputStreamReader
import scala.annotation.tailrec
import org.apache.ivy.plugins.resolver.URLResolver

/** The launched conscript entry point */
class App extends xsbti.AppMain {
  def run(config: xsbti.AppConfiguration) = {
    Exit(Deputy.run(config.arguments))
  }
}

object Deputy {
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

    val availableCommands = List("coords-artifacts", "artifacts-check", "artifacts-transitive")
    val List(resolverCommand, checkCommand, explodeCommand) = availableCommands

    val res = args.headOption.map(command => {
      if (command == resolverCommand) {
        val typesafeResolver = UrlResolver(List("http://repo.typesafe.com/typesafe/releases/[organisation]/[module]/[revision]/[artifact]-[revision](-[classifier]).[ext]"), isM2Compatible = true)
        DeputyCommands.withResolvers(commandLineLoop(List.empty), List(typesafeResolver))
      } else if (command == checkCommand) {
        DeputyCommands.check(commandLineLoop(List.empty))
      } else if (command == explodeCommand) {
        val settings = new IvySettings() //TODO: add option
        val urlResolver = new URLResolver()
        urlResolver.addArtifactPattern("http://repo.typesafe.com/typesafe/releases/[organisation]/[module]/[revision]/[artifact]-[revision](-[classifier]).[ext]")
        urlResolver.setM2compatible(true)

        //urlResolver.locate(x$1)
        DeputyCommands.explode(commandLineLoop(List.empty), settings)
      } else {
        System.err.println("Unknown command: " + command)
        -1
      }
    }).getOrElse {
      System.err.println("Provide a command!") //TODO:
      -1
    }
    Http.shutdown
    res
  }
  /** Standard runnable class entrypoint */
  def main(args: Array[String]) {
    System.exit(run(args))
  }
}

case class Exit(val code: Int) extends xsbti.Exit
