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

  // Small pool of stolen cards — reused constantly to trip velocity check
  val stolenCards = List("STOLEN_01", "STOLEN_02", "STOLEN_03")

  // Suspicious locations — always trips the location anomaly rule
  val suspiciousLocations = List("Lagos", "Reykjavik", "Tokyo")

  val merchants = List(
    ("MERCH_001", "Tim Hortons"),
    ("MERCH_002", "Shoppers Drug Mart"),
    ("MERCH_003", "Amazon")
  )

  val rng = util.Random

  def apply(merchantAgent: ActorRef[MerchantAgent.Command]): Behavior[Command] = Behaviors.setup { context =>
    Behaviors.withTimers { timers =>
      timers.startTimerWithFixedDelay(Tick, 300.millis)  // rapid burst

      Behaviors.receiveMessage {
        case Tick =>
          val (merchantId, merchantName) = merchants(rng.nextInt(merchants.size))
          val cardId   = stolenCards(rng.nextInt(stolenCards.size))
          val location = suspiciousLocations(rng.nextInt(suspiciousLocations.size))
          val payment  = PaymentRequest(
            id           = UUID.randomUUID().toString,
            cardId       = cardId,
            amount       = 600 + rng.nextDouble() * 400,  // always >$500 — trips high amount rule
            merchantId   = merchantId,
            merchantName = merchantName,
            timestamp    = System.currentTimeMillis(),
            location     = location
          )
          context.log.info(s"[FRAUD] FraudsterAgent: ${payment.cardId} $$${payment.amount} at ${payment.merchantName} from ${payment.location}")
          merchantAgent ! MerchantAgent.ReceivePayment(payment)
          Behaviors.same
      }
    }
  }
}