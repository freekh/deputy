seq(conscriptSettings :_*)

organization := "deputy"

name := "deputy"

version <<= baseDirectory {  baseDirectory =>
  val lcFile = baseDirectory / "src" / "main" / "conscript" / "deputy" / "launchconfig"
  val LcVersionExpr = """^\W+version:(.*?)$""".r
  val lcLines = io.Source.fromFile(lcFile).getLines.toList
  val appLines = {
    val appElem = lcLines.find(_.trim() == "[app]").getOrElse( throw new Exception("No [app] in launchconfig?") )
    val appElems = lcLines.slice(lcLines.indexOf(appElem), lcLines.size)
    val endSlice = appElems.slice(1, appElems.size).find(_.trim().startsWith("[")).map( a => lcLines.indexOf(a) ).getOrElse(lcLines.size)
    appElems.slice(0, endSlice)
  }
  val versions = appLines.flatMap(_ match {
    case LcVersionExpr(version) => List(version.trim())
    case _ => List.empty
  })
  if (versions.size != 1) throw new Exception("Could not find exactly one version in " + lcFile + "! Found : " + versions)
  versions.head
}

buildInfoSettings

sourceGenerators in Compile <+= buildInfo

buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion)

buildInfoPackage <<= name

resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"
 
libraryDependencies ++= Seq(
  "ch.qos.logback"  % "logback-classic" % "1.0.3",
  "net.databinder.dispatch" %% "core" % "0.9.0",
  "com.typesafe.akka" % "akka-actor" % "2.0.3",
  "org.apache.ivy" % "ivy" % "2.3.0-rc1", //REMOVE?
  //tests
  "org.specs2" %% "specs2" % "1.12.1" % "test"  
)


