package actors

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import models._

import java.util.UUID
import scala.concurrent.duration._

object CustomerAgent {

  sealed trait Command
  case object Tick extends Command

  // Realistic card identifiers
  val cards = List(
    "VISA-4729", "VISA-8831", "VISA-3312", "VISA-6604",
    "MC-5521",   "MC-9987",   "MC-4401",   "MC-7723",
    "AMEX-3341", "AMEX-7782", "AMEX-6619", "AMEX-2290",
    "VISA-1147", "VISA-5563", "MC-8834",   "MC-3317",
    "VISA-9921", "AMEX-5548", "MC-6612",   "VISA-7743"
  )

  val merchants = List(
    ("MERCH_001", "Tim Hortons"),
    ("MERCH_002", "Shoppers Drug Mart"),
    ("MERCH_003", "Amazon")
  )

  // Legit customers mostly shop locally but occasionally travel
  val localLocations  = List("Toronto", "Vancouver", "Montreal", "Calgary", "Ottawa")
  val travelLocations = List("Tokyo", "Lagos", "Reykjavik", "New York", "London")

  val rng = util.Random

  def apply(merchantAgent: ActorRef[MerchantAgent.Command]): Behavior[Command] =
    Behaviors.setup { context =>
      Behaviors.withTimers { timers =>
        timers.startTimerWithFixedDelay(Tick, 1200.millis)

        Behaviors.receiveMessage {
          case Tick =>
            val (merchantId, merchantName) = merchants(rng.nextInt(merchants.size))
            val cardId = cards(rng.nextInt(cards.size))

            // 10% chance customer is travelling — looks suspicious but is legit
            val isTravelling = rng.nextDouble() < 0.10
            val location     = if (isTravelling)
              travelLocations(rng.nextInt(travelLocations.size))
            else
              localLocations(rng.nextInt(localLocations.size))

            // Occasional large legitimate purchase
            val amount = if (rng.nextDouble() < 0.15) 500 + rng.nextDouble() * 300
            else 20 + rng.nextDouble() * 400

            val payment = PaymentRequest(
              id           = UUID.randomUUID().toString,
              cardId       = cardId,
              amount       = amount,
              merchantId   = merchantId,
              merchantName = merchantName,
              timestamp    = System.currentTimeMillis(),
              location     = location
            )
            context.log.info(
              s"[LEGIT] CustomerAgent: ${payment.cardId} $$${payment.amount} at ${payment.merchantName}"
            )
            merchantAgent ! MerchantAgent.ReceivePayment(payment)
            Behaviors.same
        }
      }
    }
}