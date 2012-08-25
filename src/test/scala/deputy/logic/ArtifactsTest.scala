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
    def dependenciesFound(nbOfDeps: Int): Unit = {
      this.nbOfDeps += nbOfDeps
    }

    def createArtifacts(coord: Coord, dependentArt: Option[Artifact], transitive: Boolean): Unit = {
      createdArtifactsMsgs = createdArtifactsMsgs :+ CreateArtifacts(coord, dependentArt, transitive)
    }
  }

  def settings(ivySettingsFile: File) = {
    val ivy = Ivy.newInstance
    ivy.configure(ivySettingsFile)
    ivy.getSettings
  }

  "Dependencies in a POM" should {
    "be resolved correctly" in {
      val pomPath = "test/samples/poms/play.pom"
      pomPath must beAnExistingPath

      val settingsPath = "test/settings/local-settings.xml"
      settingsPath must beAnExistingPath

      val artifactsLogic = new Artifacts(settings(new File(settingsPath))) with TestArtifactsHandler {
        override var nbOfDeps: Int = 0
        override var createdArtifactsMsgs: Seq[CreateArtifacts] = Seq.empty

        override def location(a: Artifact) = Some((new File(pomPath)).toURI.toURL.toString)
      }

      val dependentArtifact = None
      artifactsLogic.depdenciesFor(Artifact(None, None, None, None, None)) //we have all None, because we override the location

      artifactsLogic.nbOfDeps must beEqualTo(38)
    }
  }

}