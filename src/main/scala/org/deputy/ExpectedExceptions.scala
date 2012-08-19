package org.deputy

object expectedExceptions {
  class ExpectedException(val msg: String) extends Exception(msg)

  case class IvyCoordsParseException(actualIvyCoords: String, expr: String) extends ExpectedException("Expected valid module coordinates in this format: " + expr + " but found: '" + actualIvyCoords + "'")

  case class LineParseException(line: String, expr: String) extends ExpectedException("Line: " + line + " cannot be parsed by reg exp: " + expr)

  case class IntParseException(line: String, wrongInt: String) extends ExpectedException("While parsing: " + line + " expected this: " + wrongInt + " to be a parsable integer which it is not")
}