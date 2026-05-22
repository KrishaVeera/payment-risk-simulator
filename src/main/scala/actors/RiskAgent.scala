package actors

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import models._

import java.time.{Instant, ZoneId}

object RiskAgent {

  sealed trait Command
  case class Score(forward: ForwardToRisk, replyTo: ActorRef[MerchantAgent.Command]) extends Command

  val rng            = util.Random
  val VelocityWindow = 60000L
  val VelocityLimit  = 3

  def apply(eventStore: ActorRef[EventStoreActor.Command]): Behavior[Command] =
    active(Map.empty, eventStore)

  private def active(
                      recentPurchases: Map[String, List[Long]],
                      eventStore:      ActorRef[EventStoreActor.Command]
                    ): Behavior[Command] =
    Behaviors.receive { (context, message) =>
      message match {

        case Score(forward, replyTo) =>
          val payment = forward.payment
          val now     = payment.timestamp

          context.log.info(s"RiskAgent scoring: ${payment.id} for $$${payment.amount} at ${payment.merchantName}")

          // --- Velocity Check ---
          val cardHistory     = recentPurchases.getOrElse(payment.cardId, List.empty)
          val recentWindow    = cardHistory.filter(t => now - t < VelocityWindow)
          val velocityTripped = recentWindow.size >= VelocityLimit

          val updatedHistory  = now :: recentWindow
          val updatedPurchases = recentPurchases.updated(payment.cardId, updatedHistory)

          val outcome = if (velocityTripped) {
            context.log.info(s"  [VELOCITY] Card ${payment.cardId} made ${recentWindow.size} purchases in 60s - auto-flagged!")
            Declined
          } else {

            // --- Weighted Scoring ---
            var score = 0

            if (payment.amount > 500) {
              score += 30
              context.log.info(s"  [+30] High amount: $$${payment.amount}")
            }

            val hour = Instant.ofEpochMilli(payment.timestamp)
              .atZone(ZoneId.of("America/Toronto"))
              .getHour
            if (hour >= 23 || hour < 5) {
              score += 25
              context.log.info(s"  [+25] Late night transaction: ${hour}:00")
            }

            if (rng.nextDouble() < 0.15) {
              score += 25
              context.log.info(s"  [+25] Location anomaly flagged: ${payment.location}")
            }

            if (score >= 70) Declined
            else if (score >= 40) HeldForReview
            else Approved
          }

          context.log.info(s"RiskAgent decision for ${payment.id}: $outcome")

          val decision = RiskDecision(paymentId = payment.id, outcome = outcome)
          replyTo ! MerchantAgent.ReceiveDecision(decision)
          eventStore ! EventStoreActor.RecordEvent(forward.payment, decision)

          active(updatedPurchases, eventStore)
      }
    }
}