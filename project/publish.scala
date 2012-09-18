import sbt._
import sbt.Keys._

object PublishBuild extends Build {
  val repository = file("repository")
  val deputyProject = RootProject(uri("git://github.com/freekh/deputy.git"))
  

  val root = Project("deputy-shell", file("."), aggregate = Seq(deputyProject)).settings(
      publishTo := None,
      publishTo in deputyProject := Some(FileRepository("deputy local repo", Resolver.defaultFileConfiguration, Patterns(false, repository.getAbsolutePath + "/[organization]/[module](_[scalaVersion])/[revision]/[artifact](_[scalaVersion])-[revision](-[classifier]).[ext]")))

  )

}
