package deputy.actors

import akka.actor.Actor
import java.io.PrintStream
import deputy.models.ResolvedDep

class PrinterActor(out: PrintStream) extends Actor {
  def receive = {
    case rd: ResolvedDep => //TODO: fill 
      out.println(rd.format)
  }
}