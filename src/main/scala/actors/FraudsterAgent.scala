package actors

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import models._

import java.util.UUID
import scala.concurrent.duration._

object FraudsterAgent {

  sealed trait Command
  case object Tick extends Command

  // Realistic compromised card numbers — no obvious labels
  val compromisedCards = List(
    "4532-7391", "4532-8814", "5201-3847",
    "4916-2731", "4556-9023", "5432-1876"
  )

  val allLocations = List(
    "Lagos", "Reykjavik", "Tokyo",           // flagged
    "Toronto", "Vancouver", "Montreal"        // normal — fraudsters sometimes blend in
  )

  val merchants = List(
    ("MERCH_001", "Tim Hortons"),
    ("MERCH_002", "Shoppers Drug Mart"),
    ("MERCH_003", "Amazon")
  )

  val rng = util.Random

  def apply(merchantAgent: ActorRef[MerchantAgent.Command]): Behavior[Command] =
    Behaviors.setup { context =>
      Behaviors.withTimers { timers =>
        timers.startTimerWithFixedDelay(Tick, 300.millis)

        Behaviors.receiveMessage {
          case Tick =>
            val (merchantId, merchantName) = merchants(rng.nextInt(merchants.size))
            val cardId   = compromisedCards(rng.nextInt(compromisedCards.size))

            // 70% classic fraud pattern, 30% sneaky low-profile fraud
            val isSneaky = rng.nextDouble() < 0.30
            val amount   = if (isSneaky) 50 + rng.nextDouble() * 300
            else 500 + rng.nextDouble() * 500
            val location = if (isSneaky)
              List("Toronto", "Vancouver", "Montreal")(rng.nextInt(3))
            else
              List("Lagos", "Reykjavik", "Tokyo")(rng.nextInt(3))

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
              s"[FRAUD] FraudsterAgent: ${payment.cardId} $$${payment.amount} at ${payment.merchantName} from ${payment.location}"
            )
            merchantAgent ! MerchantAgent.ReceivePayment(payment)
            Behaviors.same
        }
      }
    }
}