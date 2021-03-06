package deputy.logic

import deputy.models.Dependency
import deputy.models.ResolvedDep
import org.apache.ivy.core.settings.IvySettings
import java.io.File
import java.net.URL
import org.apache.ivy.plugins.parser.m2.PomModuleDescriptorParser
import deputy.Deputy
import org.apache.ivy.plugins.parser.ModuleDescriptorParser
import org.apache.ivy.plugins.parser.xml.XmlModuleDescriptorParser
import org.apache.ivy.core.IvyPatternHelper
import org.apache.ivy.core.module.descriptor.DependencyDescriptor
import scala.util.matching.Regex

object DependencyExtractor {
  val FileProtocol = "file"
  val HttpProtocol = "http"
}

class DependencyExtractor(settings: IvySettings, quick: Boolean, grepExprs: List[Regex]) {
  import DependencyExtractor._

  val ProtocolRegExp = """((\w+):/.*?)""".r

  val resolver = new DependencyResolver(settings, quick, grepExprs)

  def getUrl(path: String) = {
    path match {
      case ProtocolRegExp(urlPath, protocol) => protocol match {
        case FileProtocol => {
          val url = new URL(urlPath)
          val file = new File(url.getFile)
          if (file.exists) {
            Some(url)
          } else {
            None
          }
        }
        case _ => Some(new URL(urlPath))
      }
      case filePath => {
        val file = new File(filePath)
        if (file.exists) {
          Some(file.toURI.toURL)
        } else {
          None
        }
      }
    }
  }
  
  def reduce[A, B](s: Seq[Either[A, B]]): Either[A, Seq[B]] =
    s.foldLeft(Right(Nil): Either[A, List[B]]) {
      (acc, e) => for (xs <- acc.right; x <- e.right) yield x :: xs
    }.right.map(_.reverse)

  def convertToResolvedDep(deps: Seq[DependencyDescriptor], excludeRules: Seq[(String, Option[String])], resolverName: Option[String], parent: ResolvedDep) = try {
    val filteredDeps = deps.filter { depDescr =>
      val depRevId = depDescr.getDependencyRevisionId
      !excludeRules.contains(depRevId.getOrganisation -> Option(depRevId.getName))
    }

    
    filteredDeps.map { depDescr =>
      val depRevId = depDescr.getDependencyRevisionId
      val dep = Dependency(depRevId.getOrganisation, depRevId.getName, depRevId.getRevision)
      val conf = depDescr.getModuleConfigurations()
      if (deputy.Config.skipBasedOnConfig(conf)) {
        val excludeRules = depDescr.getExcludeRules(conf).map { rule =>
          rule.getId.getModuleId.getOrganisation -> Some(rule.getId.getModuleId.getName)
        }
        val scopes = conf.toList.map(_.toString)

        val resolvedDep = resolver.resolveDependency(dep, scopes, Some(parent.path), resolverName.map(r => Some(r)).getOrElse(Some(parent.resolverName)))
        
        val a = resolvedDep.map { rd =>
          rd -> excludeRules.toSeq
        }
        a
      } else {
        Deputy.debug("Skipping: " + dep + ". Module config is: " + conf.map(_.toString).toList)
        Seq.empty
      }
    }
  } catch {
    case e: Exception => {
      Deputy.debug("Got exception : " + e + " while converting " + deps)
      e.printStackTrace()
      Seq.empty
    }
  }

  def parseDescriptor(parent: ResolvedDep) = try {
    import deputy.Constants._

    getUrl(parent.path).toSeq.flatMap { descriptorUrl =>
      val descriptorParserOpt = if (parent.moduleType == DependencyTypes.pom) Some(PomModuleDescriptorParser.getInstance) else if (parent.moduleType == DependencyTypes.ivy) Some(XmlModuleDescriptorParser.getInstance) else None
      descriptorParserOpt.toSeq.map { descriptorParser =>
        val descr = descriptorParser.parseDescriptor(settings, descriptorUrl, false)

        val resolvedArtifacts = descr.getAllArtifacts.flatMap { artifact =>
          resolver.getJars(artifact, parent).map { artifactPath =>
            parent.copy(path = artifactPath, moduleType = artifact.getType, resolvedFromArtifact = Some(parent.path))
          }
        }
        descr.getDependencies -> resolvedArtifacts
      }
    }
  } catch {
    case e: Exception => {
      Deputy.debug("Got exception : " + e + " while processing " + parent)
      Seq.empty
    }
  }
}
