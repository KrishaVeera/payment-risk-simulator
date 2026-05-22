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

  val merchants = List(
    ("MERCH_001", "Tim Hortons"),
    ("MERCH_002", "Shoppers Drug Mart"),
    ("MERCH_003", "Amazon")
  )

  val cards = List("CARD_A", "CARD_B", "CARD_C", "CARD_D")
  val locations = List("Toronto", "New York", "London", "Lagos", "Tokyo", "Reykjavik")

  val rng = util.Random

  def apply(merchantAgent: ActorRef[MerchantAgent.Command]): Behavior[Command] = Behaviors.setup { context =>
    Behaviors.withTimers { timers =>
      timers.startTimerWithFixedDelay(Tick, 2.seconds)

      Behaviors.receiveMessage {
        case Tick =>
          val (merchantId, merchantName) = merchants(rng.nextInt(merchants.size))
          val cardId = cards(rng.nextInt(cards.size))
          val payment = PaymentRequest(
            id = UUID.randomUUID().toString,
            cardId = cardId,
            amount = 50 + rng.nextDouble() * 950,
            merchantId = merchantId,
            merchantName = merchantName,
            timestamp = System.currentTimeMillis(),
            location = locations(rng.nextInt(locations.size))
          )
          context.log.info(s"CustomerAgent generated: ${payment.id} for $$${payment.amount} at ${payment.merchantName}")
          merchantAgent ! MerchantAgent.ReceivePayment(payment)
          Behaviors.same
      }
    }
  }
}
