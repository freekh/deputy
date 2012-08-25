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

class ArtifactsTest extends Specification with MustMatchers {

  trait TestArtifactsHandler extends ArtifactsHandler {
    //TODO: redesign this to not use vars?
    var nbOfDeps: Int
    var createdArtifactsMsgs: Seq[CreateArtifacts]
    var excludes: Map[(Coord, Option[String]), Seq[(String, Option[String])]] = Map.empty

    def dependenciesFound(nbOfDeps: Int): Unit = {
      this.nbOfDeps += nbOfDeps
    }

    def createArtifacts(coord: Coord, dependentArt: Option[Artifact], transitive: Boolean): Unit = {
      createdArtifactsMsgs = createdArtifactsMsgs :+ CreateArtifacts(coord, dependentArt, transitive)
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

  "Dependencies" should {
    val playPom = "play.pom"
    ("be resolved correctly when using " + playPom) in {
      val pomPath = "test/samples/poms/" + playPom
      pomPath must beAnExistingPath
      val testLocation = Some((new File(pomPath)).toURI.toURL.toString)

      val settingsPath = "test/settings/local-settings.xml"
      settingsPath must beAnExistingPath

      val artifactsLogic = new Artifacts(settings(new File(settingsPath))) with TestArtifactsHandler {
        override var nbOfDeps: Int = 0
        override var createdArtifactsMsgs: Seq[CreateArtifacts] = Seq.empty
        override def location(a: Artifact) = testLocation
      }

      val dependentArtifact = None
      artifactsLogic.depdenciesFor(Artifact(None, None, None, None, None), Seq.empty) //we have all None, because we override the location
      artifactsLogic.excludes.get(Coord("org.reflections", "reflections", "0.9.6") -> testLocation) must beEqualTo(Some(Seq("com.google.guava" -> Some("guava"), "javassist" -> Some("javassist"))))

      artifactsLogic.nbOfDeps must beEqualTo(38)

    }
  }

}