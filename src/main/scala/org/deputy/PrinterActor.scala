package org.deputy

import akka.actor.Actor
import org.deputy.models.Artifact
import java.io.PrintStream

/**
 * Prints an artifact iif it does NOT depend on anything not YET printed
 */
class PrinterActor(out: PrintStream) extends Actor {
  val printedArtifacts = Vector.empty[Artifact]

  def receive = {
    case a: Artifact => //TODO: fill 
      out.println(a.format)
  }
}