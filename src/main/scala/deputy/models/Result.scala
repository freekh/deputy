package deputy.models

object Result {
  def parse(line: String) = {
    val lineParts = line.split("=")
    val urlsStr = lineParts.toList(1)
    val keySplit = lineParts(0).split("#")
    //TODO: error handling and use Reg ex instead!
    Result(Dependency.parse(keySplit(0)), keySplit(1), urlsStr.split(",").toList)
  }
}

case class Result(dep: Dependency, moduleType: String, urls: List[String]) {
  def format = {
    dep.format + "#" + moduleType + "=" + urls.mkString(",")

  }
}