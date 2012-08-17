seq(conscriptSettings :_*)

organization := "org.deputy"

name := "deputy"

version := "0.1.1"

libraryDependencies += "org.apache.ivy" % "ivy" % "2.2.0"

libraryDependencies += "net.databinder.dispatch" %% "core" % "0.9.0"

{
  val logbackVersion = "1.0.3"
  libraryDependencies ++= Seq("ch.qos.logback"  % "logback-classic" % logbackVersion)
}


