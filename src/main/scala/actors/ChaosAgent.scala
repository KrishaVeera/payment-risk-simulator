package actors

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import models._

import scala.concurrent.duration._

object ChaosAgent {

  sealed trait Command
  case class Forward(forward: ForwardToRisk, replyTo: ActorRef[MerchantAgent.Command]) extends Command

  val rng = util.Random

  def apply(riskAgent: ActorRef[RiskAgent.Command]): Behavior[Command] =
    Behaviors.receive { (context, message) =>
      message match {
        case Forward(forward, replyTo) =>
          val roll = rng.nextDouble()

          if (roll < 0.10) {
            // DROP — silently discard, merchant never gets a response
            context.log.info(s"[CHAOS] Dropped payment: ${forward.payment.id}")

          } else if (roll < 0.20) {
            // DUPLICATE — send to RiskAgent twice
            context.log.info(s"[CHAOS] Duplicating payment: ${forward.payment.id}")
            riskAgent ! RiskAgent.Score(forward, replyTo)
            riskAgent ! RiskAgent.Score(forward, replyTo)

          } else if (roll < 0.40) {
            // DELAY — wait 500ms–2s then forward
            val delayMs = 500 + rng.nextInt(1500)
            context.log.info(s"[CHAOS] Delaying payment ${forward.payment.id} by ${delayMs}ms")
            Thread.sleep(delayMs)
            riskAgent ! RiskAgent.Score(forward, replyTo)

          } else {
            // NORMAL — forward cleanly
            riskAgent ! RiskAgent.Score(forward, replyTo)
          }

          Behaviors.same
      }
    }
}
