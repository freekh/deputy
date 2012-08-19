package org.deputy.descriptors

import scala.xml.Elem
import org.deputy.models.Coords
import scala.xml.NodeSeq
import java.io.InputStream
import org.deputy.expectedExceptions._
import dispatch.Promise

object Pom {

  def findRef(ref: String, pom: Pom): Option[String] = { //TODO: fix this properly! http://docs.codehaus.org/display/MAVENUSER/MavenPropertiesGuide and http://svn.apache.org/viewvc/maven/maven-2/branches/maven-2.2.x/maven-core/src/main/resources/META-INF/maven/plugin-expressions/

    def findParentCoords = {
      //scan all parents with revisions defined and take the first one
      pom.parents.filter(_._1.revision.isDefined).headOption.map {
        case (coords, location) =>
          coords
      }
    }

    def findParentVersion = findParentCoords.flatMap(_.revision)
    def findVersion = if (pom.coords.revision.isDefined) pom.coords.moduleName
    else if (pom.parents.nonEmpty) {
      findParentVersion
    } else None

    def findArtifactId = pom.coords.moduleName
    def findParentArtifactId = findParentCoords.flatMap(_.moduleName)

    def findGroupId = pom.coords.moduleOrg
    def findParentGroupId = findParentCoords.flatMap(_.moduleOrg)

    ref match {
      case "project.version" => findVersion
      case "pom.version" => findVersion
      case "version" => findVersion
      case "parent.version" => findParentVersion
      //
      case "project.artifactId" => findArtifactId
      case "pom.artifactId" => findArtifactId
      case "artifactId" => findArtifactId
      case "parent.artifactId" => findParentArtifactId
      //
      case "project.groupId" => findGroupId
      case "pom.groupId" => findGroupId
      case "groupId" => findGroupId
      case "parent.groupId" => findParentGroupId

      case _ => throw new PomParseException(pom.artifact, "Pom: " + pom + " has an unknown ref: " + ref)
    }
  }

  def resolveReferences(pom: Pom) = {
    val Reference = """\$\{(.*?)\}""".r

    def update[A](updateElem: Option[String], input: A, copy: Option[String] => A) = {
      updateElem match {
        case Some(Reference(ref)) => {
          findRef(ref, pom) match {
            case r @ Some(_) => {
              copy(r)
            }
            case _ => input
          }
        }
        case _ => {
          input
        }
      }
    }
    val updatedPom1 = update(pom.coords.revision, pom, r => pom.copy(coords = pom.coords.copy(revision = r)))
    val updatedPom2 = update(updatedPom1.coords.moduleName, updatedPom1, r => updatedPom1.copy(coords = updatedPom1.coords.copy(moduleName = r)))
    val updatedPom3 = update(updatedPom2.coords.moduleOrg, updatedPom2, r => updatedPom2.copy(coords = updatedPom2.coords.copy(moduleOrg = r)))
    val updatedDependencies = updatedPom3.dependencies.map { dep =>
      update(dep.revision, dep, r => dep.copy(revision = r))
    }
    updatedPom3.copy(dependencies = updatedDependencies)
  }

  def parse(xml: Elem, artifact: String): Pom = {
    val project = xml \\ "project"
    //TODO: check that the file is correct etc etc...
    def firstText(e: NodeSeq, in: String) = (e \ in).headOption.map(_.text)
    def m2GroupId(e: NodeSeq) = firstText(e, "groupId")
    def m2ArtifactId(e: NodeSeq) = firstText(e, "artifactId")
    def m2Version(e: NodeSeq) = firstText(e, "version")
    def m2Coords(e: NodeSeq) = Option(DescriptorCoords(m2GroupId(e), m2ArtifactId(e), m2Version(e)))

    val parents = {
      val parents = for {
        parent <- project \ "parent"
        coords <- m2Coords(parent)
        location = (parent \ "relativePath").headOption.map(_.text)
      } yield {
        coords -> location
      }
      if (parents.size > 1) throw PomParseException(artifact, "could not find any groupId, artifactId or version elements in pom") else parents
    }

    val deps = for {
      deps <- project \ "dependencies"
      dep <- deps \ "dependency"
      coords <- m2Coords(dep)
    } yield {
      if (!coords.revision.isDefined && parents.headOption.isDefined) {
        coords.copy(revision = parents.headOption.get._1.revision)
      } else {
        coords
      }
    }

    val coords = m2Coords(project).getOrElse {
      throw PomParseException(artifact, "could not find any groupId, artifactId or version elements in pom")
    }

    resolveReferences(Pom(artifact, coords, deps.toList, parents))
  }
}

case class Pom(artifact: String, coords: DescriptorCoords, dependencies: Seq[DescriptorCoords], parents: Seq[(DescriptorCoords, Option[String])]) extends Descriptor {

}