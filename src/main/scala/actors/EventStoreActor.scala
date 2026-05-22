package actors

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import models._

import scala.concurrent.duration._

object EventStoreActor {

  sealed trait Command
  case class RecordEvent(payment: PaymentRequest, decision: RiskDecision) extends Command
  case object PrintSummary extends Command

  def apply(): Behavior[Command] = Behaviors.withTimers { timers =>
    timers.startTimerWithFixedDelay(PrintSummary, 30.seconds)
    active(Vector.empty)
  }

  private def active(events: Vector[PaymentEvent]): Behavior[Command] =
    Behaviors.receive { (context, message) =>
      message match {

        case RecordEvent(payment, decision) =>
          val event = PaymentEvent(
            paymentId    = payment.id,
            cardId       = payment.cardId,
            amount       = payment.amount,
            merchantId   = payment.merchantId,
            merchantName = payment.merchantName,
            location     = payment.location,
            timestamp    = payment.timestamp,
            outcome      = decision.outcome
          )
          active(events :+ event)

        case PrintSummary =>
          val total    = events.size
          val approved = events.count(_.outcome == Approved)
          val declined = events.count(_.outcome == Declined)
          val held     = events.count(_.outcome == HeldForReview)

          val approvalRate = if (total == 0) 0.0 else approved.toDouble / total * 100
          val fraudRate    = if (total == 0) 0.0 else declined.toDouble / total * 100

          context.log.info("=" * 50)
          context.log.info("  EVENT STORE SUMMARY")
          context.log.info(s"  Total decisions : $total")
          context.log.info(f"  Approved        : $approved ($approvalRate%.1f%%)")
          context.log.info(f"  Declined        : $declined ($fraudRate%.1f%%)")
          context.log.info(s"  Held for review : $held")
          context.log.info("=" * 50)

          Behaviors.same
      }
    }
}