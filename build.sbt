seq(conscriptSettings :_*)

organization := "org.deputy"

name := "deputy"

version := "0.1.3"

libraryDependencies ++= Seq(
  "ch.qos.logback"  % "logback-classic" % "1.0.3",
  "net.databinder.dispatch" %% "core" % "0.9.0",
  "org.apache.ivy" % "ivy" % "2.2.0", //REMOVE?
  //tests
  "org.specs2" %% "specs2" % "1.12.1" % "test"  
)


