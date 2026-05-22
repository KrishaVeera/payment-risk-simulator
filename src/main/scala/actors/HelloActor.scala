package actors

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors

object HelloActor {

sealed trait Command
  case class SayHello(name: String) extends Command

  def apply(): Behavior[Command] =
        Behaviors.receive { (context, message) =>
    message match {
        case SayHello(name) =>
            context.log.info("Hello, {}! Phase 0 actor is alive.", name)
            Behaviors.same
    }
}
}