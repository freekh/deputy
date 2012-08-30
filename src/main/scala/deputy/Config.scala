package deputy

object Config {
  val issuesUrl = "https://github.com/freekh/deputy/issues"
  def skipBasedOnConfig(conf: Array[String]) = {
    !(conf.contains("optional") || conf.contains("provided"))
  }
}