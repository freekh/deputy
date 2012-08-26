package deputy.logic

import org.specs2.mutable.Specification
import deputy.actors.CreateArtifacts
import deputy.models.Coord
import deputy.models.Artifact
import org.apache.ivy.core.IvyContext
import java.io.File
import org.apache.ivy.Ivy
import org.specs2.matcher.MustMatchers
import java.net.URL
import org.apache.ivy.core.settings.IvySettings

class ArtifactsTest extends Specification with MustMatchers {

  trait TestArtifactsHandler extends ArtifactsHandler {
    //TODO: redesign this to not use vars?
    var nbOfDeps: Int
    var createdArtifactsMsgs: Seq[CreateArtifacts]
    var excludes: Map[(Coord, Option[String]), Seq[(String, Option[String])]] = Map.empty

    def dependenciesFound(nbOfDeps: Int): Unit = {
      this.nbOfDeps += nbOfDeps
    }

    def createArtifacts(coord: Coord, scopes: List[String], dependentArt: Option[Artifact], transitive: Boolean): Unit = {
      createdArtifactsMsgs = createdArtifactsMsgs :+ CreateArtifacts(coord, scopes, dependentArt, transitive)
    }

    def addExcludeRule(parent: Coord, id: String, excludeOrg: String, excludeNameOpt: Option[String]): Unit = {
      val key = parent -> Some(id)
      val newExcludeRules = excludes.get(parent -> Some(id)).map { rules =>
        rules :+ (excludeOrg, excludeNameOpt)
      }.getOrElse {
        Seq(excludeOrg -> excludeNameOpt)
      }
      excludes += key -> newExcludeRules
    }

  }

  def settings(ivySettingsFile: File) = {
    val ivy = Ivy.newInstance
    ivy.configure(ivySettingsFile)
    ivy.getSettings
  }

  def createArtifactsLogic(path: String, settings: IvySettings) = {
    new Artifacts(settings) with TestArtifactsHandler {
      override var nbOfDeps: Int = 0
      override var createdArtifactsMsgs: Seq[CreateArtifacts] = Seq.empty
      override def location(a: Artifact) = Some((new File(path)).toURI.toURL.toString)
    }
  }

  def checkPaths(paths: String*): List[String] = {
    paths.foreach {
      _ must beAnExistingPath
    }
    paths.toList
  }

  "Dependencies" should {
    val List(settingsPath, playPath, nettyPath) = checkPaths(
      "test/settings/local-settings.xml",
      "test/samples/poms/play.pom",
      "test/samples/poms/netty.pom")
    val dummyArtifact = Artifact(None, None, None, List.empty, None) //we have all None, because we override the location
    val localeSettings = settings(new File(settingsPath))
    ("be resolved correctly when using a pom with excludes: " + playPath) in {

      val artifactsLogic = createArtifactsLogic(playPath, localeSettings)

      val dependentArtifact = None
      artifactsLogic.depdenciesFor(dummyArtifact, Seq.empty)
      artifactsLogic.excludes.get(Coord("org.reflections", "reflections", "0.9.6") -> artifactsLogic.location(dummyArtifact)) must beEqualTo(Some(Seq("com.google.guava" -> Some("guava"), "javassist" -> Some("javassist"))))

      artifactsLogic.nbOfDeps must beEqualTo(38)
    }

    ("be resolved correctly for a pom that has optionals: " + nettyPath) in {
      val artifactsLogic = createArtifactsLogic(nettyPath, localeSettings)
      artifactsLogic.depdenciesFor(dummyArtifact, Seq.empty)
      val createdArtifactMsgs = artifactsLogic.createdArtifactsMsgs
      createdArtifactMsgs.size must beEqualTo(6)
      createdArtifactMsgs.filter(!_.scopes.contains("test")).size must beEqualTo(0) //we should end up with no artifacts  
    }
  }

}