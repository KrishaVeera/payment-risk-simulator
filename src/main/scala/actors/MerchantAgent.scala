package actors

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import models._

object MerchantAgent {

  sealed trait Command
  case class ReceivePayment(payment: PaymentRequest) extends Command
  case class ReceiveDecision(decision: RiskDecision) extends Command

  def apply(chaosAgent: ActorRef[ChaosAgent.Command]): Behavior[Command] =
    Behaviors.receive { (context, message) =>
      message match {

        case ReceivePayment(payment) =>
          context.log.info(s"MerchantAgent received payment: ${payment.id} from card ${payment.cardId} for $$${payment.amount} at ${payment.merchantName}")
          val forward = ForwardToRisk(
            payment   = payment,
            timestamp = System.currentTimeMillis(),
            location  = payment.location
          )
          chaosAgent ! ChaosAgent.Forward(forward, context.self)
          Behaviors.same

        case ReceiveDecision(decision) =>
          context.log.info(s"MerchantAgent received decision for ${decision.paymentId}: ${decision.outcome}")
          Behaviors.same
      }
    }
}