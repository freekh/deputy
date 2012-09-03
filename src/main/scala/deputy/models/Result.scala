package deputy.models

import deputy.Deputy

object Result {
  def parse(line: String) = {
    try {
      val lineParts = line.split("=")
      val urlsStr = lineParts.toList(1)
      val keySplit = lineParts(0).split("#")
      //TODO: error handling and use Reg ex instead!
      Result(Dependency.parse(keySplit(0)), keySplit(1), urlsStr.split(",").toList)
    } catch {
      case e: Exception =>
        Deputy.fail("Failed while parsing result: " + line)
        throw e
    }
  }
}

case class Result(dep: Dependency, moduleType: String, urls: List[String]) {
  def format = {
    dep.format + "#" + moduleType + "=" + urls.mkString(",")

  }
}