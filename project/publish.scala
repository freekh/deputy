import sbt._
import sbt.Keys._

object PublishBuild extends Build {

  val repository = file("repository")
  val deputyProject = RootProject(uri(System.getProperty("deputy.location")))

  val root = Project("deputy-shell", file("."), aggregate = Seq(deputyProject)).settings(
      publishTo := None,
      publishTo in deputyProject <<= (baseDirectory in deputyProject) { d =>
        println(d)
        Some(FileRepository("deputy local repo", Resolver.defaultFileConfiguration, Patterns(true, repository.getAbsolutePath + "/[organization]/[module](_[scalaVersion])/[revision]/[artifact](_[scalaVersion])-[revision](-[classifier]).[ext]")))
       }

  )

}
