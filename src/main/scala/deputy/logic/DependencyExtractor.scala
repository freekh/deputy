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

class DependencyExtractor(settings: IvySettings) {
  val ProtocolRegExp = """((\w+):/.*?)""".r

  val resolver = new DependencyResolver(settings)

  protected def getUrl(path: String) = {
    path match {
      case ProtocolRegExp(urlPath, protocol) => protocol match {
        case "file" => {
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

  def findDependencies(parent: ResolvedDep, resolverName: Option[String], excludeRules: Seq[(String, Option[String])]) = try {
    import deputy.Constants._

    getUrl(parent.path).toSeq.flatMap { descriptorUrl =>
      val descriptorParserOpt = if (parent.moduleType == DependencyTypes.pom) Some(PomModuleDescriptorParser.getInstance) else if (parent.moduleType == DependencyTypes.ivy) Some(XmlModuleDescriptorParser.getInstance) else None
      descriptorParserOpt.toSeq.flatMap { descriptorParser =>
        val pomDescr = descriptorParser.parseDescriptor(settings, descriptorUrl, false)

        val deps = pomDescr.getDependencies

        val filteredDeps = deps.filter { depDescr =>
          val depRevId = depDescr.getDependencyRevisionId
          !excludeRules.contains(depRevId.getOrganisation -> Option(depRevId.getName))
        }

        filteredDeps.flatMap { depDescr =>
          val depRevId = depDescr.getDependencyRevisionId
          val dep = Dependency(depRevId.getOrganisation, depRevId.getName, depRevId.getRevision)

          val conf = depDescr.getModuleConfigurations()
          if (deputy.Config.skipBasedOnConfig(conf)) {
            val excludeRules = depDescr.getExcludeRules(conf).map { rule =>
              rule.getId.getModuleId.getOrganisation -> Some(rule.getId.getModuleId.getName)
            }
            val scopes = conf.toList.map(_.toString)

            val resolvedDep = resolver.resolveDependency(dep, scopes, Some(parent.path), resolverName.map(r => Some(r)).getOrElse(Some(parent.resolverName)))
            resolvedDep.map { rd =>
              rd -> excludeRules.toSeq
            }
          } else {
            Deputy.debug("Skipping: " + dep + ". Module config is: " + conf.map(_.toString).toList)
            Seq.empty
          }
        }
      }
    }
  } catch {
    case e => {
      Deputy.debug("Got exception : " + e + " while processing " + parent)
      Seq.empty
    }
  }
}