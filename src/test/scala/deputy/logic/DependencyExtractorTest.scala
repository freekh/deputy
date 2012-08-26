package deputy.logic

import org.specs2.mutable.Specification
import org.apache.ivy.core.IvyContext
import java.io.File
import org.apache.ivy.Ivy
import org.specs2.matcher.MustMatchers
import java.net.URL
import org.apache.ivy.core.settings.IvySettings
import deputy.actors.ResolveDep
import deputy.models.Dependency
import deputy.models.ResolvedDep

class DependencyExtractorTest extends Specification with MustMatchers {

  trait TestArtifactsHandler extends DependencyExtractorHandler {
    //TODO: redesign this to not use vars?
    var nbOfDeps: Int
    var resolveDepMsgs: Seq[ResolveDep]
    var excludes: Map[(Dependency, Option[String]), Seq[(String, Option[String])]] = Map.empty

    def dependenciesFound(nbOfDeps: Int): Unit = {
      this.nbOfDeps += nbOfDeps
    }

    def resolveDependency(dep: Dependency, scopes: List[String], parent: Option[ResolvedDep], transitive: Boolean): Unit = {
      resolveDepMsgs = resolveDepMsgs :+ ResolveDep(dep, scopes, parent, transitive)
    }

    def addExcludeRule(parent: Dependency, id: String, excludeOrg: String, excludeNameOpt: Option[String]): Unit = {
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

  def createDependencyExtractor(path: String, settings: IvySettings) = {
    new DependencyExtractor(settings) with TestArtifactsHandler {
      override var nbOfDeps = 0
      override var resolveDepMsgs = Seq.empty[ResolveDep]
      override def location(rd: ResolvedDep) = Some((new File(path)).toURI.toURL.toString)
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
    val dummyRd = ResolvedDep(None, None, None, List.empty, None) //we have all None, because we override the location
    val localeSettings = settings(new File(settingsPath))
    ("be resolved correctly when using a pom with excludes: " + playPath) in {

      val dependencyExtractor = createDependencyExtractor(playPath, localeSettings)

      val dependentArtifact = None
      dependencyExtractor.dependenciesFor(dummyRd, Seq.empty)
      dependencyExtractor.excludes.get(Dependency("org.reflections", "reflections", "0.9.6") -> dependencyExtractor.location(dummyRd)) must beEqualTo(Some(Seq("com.google.guava" -> Some("guava"), "javassist" -> Some("javassist"))))

      dependencyExtractor.nbOfDeps must beEqualTo(38)
    }

    ("be resolved correctly for a pom that has optionals: " + nettyPath) in {
      val dependencyExtractor = createDependencyExtractor(nettyPath, localeSettings)
      dependencyExtractor.dependenciesFor(dummyRd, Seq.empty)
      val createdArtifactMsgs = dependencyExtractor.resolveDepMsgs
      createdArtifactMsgs.size must beEqualTo(6)
      createdArtifactMsgs.filter(!_.scopes.contains("test")).size must beEqualTo(0) //we should end up with no artifacts  
    }
  }

}