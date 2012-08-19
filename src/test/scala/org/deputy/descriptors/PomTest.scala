package org.deputy.descriptors

import org.specs2.mutable._
import scala.xml.XML

class PomTest extends Specification {
  "Pom" should {
    "parse the following pom file correctly" in {
      val location = "descriptors/fluentlenium-festassert-0.5.6.pom"
      val pom = XML.load(getClass.getClassLoader.getResourceAsStream(location))
      println(Pom.parse(pom, location))

      println(Pom.findRef("project.version", Pom.parse(pom, location)))
      true

    }
  }
}