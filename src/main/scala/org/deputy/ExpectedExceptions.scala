package org.deputy

object expectedExceptions {
  case class IvyCoordsParseException(actualIvyCoords: String) extends Exception("Expected valid module coordinates in this format: <org>:<name>:<version> but found: " + actualIvyCoords)

  case class LineParseException(line: String, expr: String) extends Exception("Line: " + line + " cannot be parsed by reg exp: " + expr)

  case class IntParseException(line: String, wrongInt: String) extends Exception("While parsing: " + line + " expected this: " + wrongInt + " to be a parsable integer which it is not")

  case class PomParseException(file: String, error: String) extends Exception("Got the following error: " + error + " while parsing pom file: " + file)
}