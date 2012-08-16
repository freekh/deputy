package org.deputy

import java.io.File
import org.apache.ivy.core.settings.IvySettings
import org.apache.ivy.plugins.resolver.DependencyResolver
import org.apache.ivy.Ivy
import org.apache.ivy.core.retrieve.RetrieveOptions
import org.apache.ivy.core.LogOptions
import org.deputy.resolvers.UrlResolver
import org.deputy.formatting.OutputLine
import dispatch.Http

/** The launched conscript entry point */
class App extends xsbti.AppMain {
  def run(config: xsbti.AppConfiguration) = {
    Exit(App.run(config.arguments))
  }
}

object App {
  /**
   * Shared by the launched version and the runnable version,
   * returns the process status code
   */
  def run(args: Array[String]): Int = {
    //  echo "org.apache.ivy:ivy:2.2.20" | deputy with-resolvers /file/ivy-settings.xml | deputy prune | deputy explode |  deputy download --format="test/[mavenorg]" /directory  # or grep jar | cut -d '|' -f 3 | xargs curl 

    //"org.apache.ivy:ivy:2.2.0"

    //println(ListDependencies.forIvyCoordinates("org.apache.ivy", "ivy", "2.2.0", ivyInstance).toString)
    val typesafeResolver = UrlResolver(List("http://repo.typesafe.com/typesafe/releases/[organisation]/[module]/[revision]/[artifact]-[revision](-[classifier]).[ext]"), isM2Compatible = true)

    val res = args.headOption.map(command => {
      if (command == "with-resolvers")
        DeputyCommands.withResolvers(args(1), List(typesafeResolver))
      else if (command == "prune")
        DeputyCommands.prune(args(1))
      else {
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
