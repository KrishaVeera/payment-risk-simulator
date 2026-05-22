package actors

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import models._

object RiskAgent {

  // Messages this actor can receive
  sealed trait Command
  case class Score(forward: ForwardToRisk, replyTo: ActorRef[MerchantAgent.Command]) extends Command

  def apply(): Behavior[Command] =
    Behaviors.receive { (context, message) =>
      message match {

        case Score(forward, replyTo) =>
          context.log.info(s" RiskAgent scoring payment: ${forward.payment.id} for $${forward.payment.amount} at ${forward.payment.merchantName}")

          // Stub logic — random decision for now (Phase 2 will add real scoring)
          val outcome = util.Random.nextInt(3) match {
            case 0 => Approved
            case 1 => Declined
            case _ => HeldForReview
          }

          val decision = RiskDecision(
            paymentId = forward.payment.id,
            outcome   = outcome
          )

          context.log.info(s" RiskAgent decision for ${forward.payment.id}: $outcome")
          replyTo ! MerchantAgent.ReceiveDecision(decision)
          Behaviors.same
      }
    }
}
