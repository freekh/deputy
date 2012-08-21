package org.deputy

import java.io.File
import org.apache.ivy.core.settings.IvySettings
import org.apache.ivy.plugins.resolver.DependencyResolver
import org.apache.ivy.Ivy
import org.apache.ivy.core.retrieve.RetrieveOptions
import org.apache.ivy.core.LogOptions
import org.deputy.models._
import dispatch.Http
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.io.InputStreamReader
import scala.annotation.tailrec
import org.apache.ivy.plugins.resolver.URLResolver
import ch.qos.logback.classic.Level
import org.apache.ivy.core.IvyContext
import org.apache.ivy.core.module.id.ModuleRevisionId
import org.apache.ivy.core.module.id.ModuleId
import org.apache.ivy.plugins.resolver.FileSystemResolver
import org.apache.ivy.plugins.resolver.AbstractPatternsBasedResolver
import org.apache.ivy.plugins.resolver.AbstractResolver
import org.apache.ivy.plugins.resolver.ChainResolver
import org.apache.ivy.plugins.resolver.RepositoryResolver
import org.apache.ivy.plugins.resolver.util.ResolverHelper
import org.apache.ivy.core.module.descriptor.Artifact
import org.apache.ivy.core.module.descriptor.DefaultArtifact
import java.util.Date
import org.apache.ivy.core.IvyPatternHelper

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

    //play:play_2.9.1:2.0.3=http://repo.typesafe.com/typesafe/releases/play/play_2.9.1/2.0.3/play_2.9.1-2.0.3.jar,http://repo1.maven.com/play/play_2.9.1/2.0.3/play_2.9.1-2.0.3.jar
    //cat f | grep jar | deputy --all-versions artifacts-results 

    //deputy results-download

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

    val ivy = DeputyCommands.disableOutput { //TODO: move this one
      val ivy = IvyContext.getContext.getIvy
      ivy.configure(ivySettingsFile)
      /*
        import scala.collection.JavaConversions._
        
        val allRepositoryResolvers = DeputyCommands.getRepositoryResolvers(ivy.getSettings.getResolvers.toList)
        val t = ivy.getSettings.getResolver("typesafe").asInstanceOf[RepositoryResolver]
        //println("list " + t.getRepository.list("http://repo.typesafe.com/typesafe/releases/play"))
        //println(t.locate(DefaultArtifact.newPomArtifact(ModuleRevisionId.newInstance("play", "templates_2.9.1", "[2.0.1,)"), new java.util.Date())))

        val partiallyResolvedPattern = IvyPatternHelper.substitute(t.getArtifactPatterns.get(0).asInstanceOf[String], ModuleRevisionId
          .newInstance(ModuleRevisionId.newInstance("play", "templates_2.9.1", "2.0.3"), IvyPatternHelper.getTokenString(IvyPatternHelper.REVISION_KEY)),
          DefaultArtifact.newPomArtifact(ModuleRevisionId.newInstance("play", "templates_2.9.1", "2.0.3"), new Date()))
        println(partiallyResolvedPattern)

        println(ResolverHelper.listTokenValues(t.getRepository, partiallyResolvedPattern,
          IvyPatternHelper.REVISION_KEY).toList.map(_.toString))
        System.exit(0)
        //println(t.getIvyPatterns.toList.map(_.toString))
        //println(ResolverHelper.findAll(t.getRepository, ModuleRevisionId.newInstance("play", "templates_2.9.1", "2.0.3"), "http://repo.typesafe.com/typesafe/releases/[organisation]/[module]/[revision]/[artifact]-[revision](-[classifier]).[ext]", DefaultArtifact.newIvyArtifact(ModuleRevisionId.newInstance("plfay", "plfay_2.9.1", "2.0.3"), new Date())).map(_.getResource.).toList)
         * 
         * 
         */
      ivy
    }

    val res = args.headOption.map(command => {
      if (command == resolverCommand) {
        DeputyCommands.withResolvers(commandLineLoop(List.empty), ivy.getSettings)
      } else if (command == checkCommand) {
        DeputyCommands.check(commandLineLoop(List.empty))
      } else if (command == explodeCommand) {
        DeputyCommands.explodeLines(commandLineLoop(List.empty), ivy.getSettings)
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
