package org.deputy.resolvers

abstract class Resolver {
  val artifactPatterns: List[String]
  val ivyPatterns: Option[List[String]]
  val isM2Compatible: Boolean
}
case class UrlResolver(artifactPatterns: List[String], ivyPatterns: Option[List[String]] = None, isM2Compatible: Boolean = false) extends Resolver {

}
