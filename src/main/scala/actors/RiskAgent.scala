package actors

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import models._

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.time.{Instant, ZoneId}

object RiskAgent {

  sealed trait Command
  case class Score(forward: ForwardToRisk, replyTo: ActorRef[MerchantAgent.Command]) extends Command

  val rng            = util.Random
  val VelocityWindow = 60000L
  val VelocityLimit  = 3
  val DedupWindow    = 60000L
  val FlaskUrl       = "http://127.0.0.1:5001/score"

  // HTTP client — reused across all requests
  val httpClient: HttpClient = HttpClient.newHttpClient()

  def callFlask(payment: PaymentRequest, velocityCount: Int): Option[(Double, Boolean)] = {
    try {
      val hour = Instant.ofEpochMilli(payment.timestamp)
        .atZone(ZoneId.of("America/Toronto"))
        .getHour

      val locationRisk = if (Set("Tokyo", "Lagos", "Reykjavik").contains(payment.location)) 1 else 0
      val highRiskMerchant = if (payment.merchantName == "Amazon") 1 else 0

      val body = ujson.Obj(
        "amount"                -> payment.amount,
        "velocity"              -> velocityCount,
        "location_risk"         -> locationRisk,
        "hour"                  -> hour,
        "is_high_risk_merchant" -> highRiskMerchant
      ).toString()

      val request = HttpRequest.newBuilder()
        .uri(URI.create(FlaskUrl))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(body))
        .build()

      val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
      val json     = ujson.read(response.body())

      val prob    = json("fraud_probability").num
      val isFraud = json("is_fraud").bool

      Some((prob, isFraud))

    } catch {
      case e: Exception => None  // Flask not running — skip ML scoring silently
    }
  }

  def apply(eventStore: ActorRef[EventStoreActor.Command]): Behavior[Command] =
    active(Map.empty, Map.empty, eventStore)

  private def active(
                      recentPurchases: Map[String, List[Long]],
                      seenPayments:    Map[String, Long],
                      eventStore:      ActorRef[EventStoreActor.Command]
                    ): Behavior[Command] =
    Behaviors.receive { (context, message) =>
      message match {

        case Score(forward, replyTo) =>
          val payment = forward.payment
          val now     = payment.timestamp

          // --- Deduplication Check ---
          val isDuplicate = seenPayments.get(payment.id)
            .exists(firstSeen => now - firstSeen < DedupWindow)

          if (isDuplicate) {
            context.log.info(s"[DEDUP] Dropping duplicate payment: ${payment.id}")
            active(recentPurchases, seenPayments, eventStore)

          } else {

            context.log.info(s"RiskAgent scoring: ${payment.id} for $$${payment.amount} at ${payment.merchantName}")

            val updatedSeenPayments = (seenPayments + (payment.id -> now))
              .filter { case (_, ts) => now - ts < DedupWindow }

            // --- Velocity Check ---
            val cardHistory  = recentPurchases.getOrElse(payment.cardId, List.empty)
            val recentWindow = cardHistory.filter(t => now - t < VelocityWindow)
            val velocityTripped = recentWindow.size >= VelocityLimit

            val updatedHistory   = now :: recentWindow
            val updatedPurchases = recentPurchases.updated(payment.cardId, updatedHistory)

            // --- ML Scoring (runs alongside rules) ---
            callFlask(payment, recentWindow.size) match {
              case Some((prob, mlFraud)) =>
                context.log.info(f"  [ML] fraud_probability=$prob%.4f is_fraud=$mlFraud")
              case None =>
                context.log.info("  [ML] Flask unavailable — skipping ML score")
            }

            // --- Rule-based Decision (still drives actual outcome) ---
            val outcome = if (velocityTripped) {
              context.log.info(s"  [VELOCITY] Card ${payment.cardId} made ${recentWindow.size} purchases in 60s - auto-flagged!")
              Declined
            } else {

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

            active(updatedPurchases, updatedSeenPayments, eventStore)
          }
      }
    }
}