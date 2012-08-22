package org.deputy.actors

import akka.actor.Actor
import java.io.PrintStream
import org.deputy.models.Artifact

/**
 * Prints an artifact iif it does NOT depend on anything not YET printed
 */
class OrderedPrinterActor(out: PrintStream) extends Actor {
  val printedArtifacts = Vector.empty[Artifact]

  def receive = {
    case a: Artifact => //TODO: fill 
      out.println(a.format)
  }
}