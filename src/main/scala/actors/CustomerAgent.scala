package actors
import akka.actor.typed.ActorRef

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import models._

import java.util.UUID
import scala.concurrent.duration._

object CustomerAgent {

  // Messages this actor can receive
  sealed trait Command
  case object Tick extends Command  // timer fires → generate a payment

  // Hardcoded list of fake merchants
  val merchants = List(
    ("MERCH_001", "Tim Hortons"),
    ("MERCH_002", "Shoppers Drug Mart"),
    ("MERCH_003", "Amazon")
  )

  // Hardcoded list of fake card IDs
  val cards = List("CARD_A", "CARD_B", "CARD_C", "CARD_D")

  def apply(merchantAgent: ActorRef[MerchantAgent.Command]): Behavior[Command] = Behaviors.setup { context =>
    Behaviors.withTimers { timers =>
      timers.startTimerWithFixedDelay(Tick, 2.seconds)  // fire every 2 seconds

      Behaviors.receiveMessage {
        case Tick =>
          val (merchantId, merchantName) = merchants(util.Random.nextInt(merchants.size))
          val payment = PaymentRequest(
            id           = UUID.randomUUID().toString,
            cardId       = cards(util.Random.nextInt(cards.size)),
            amount       = BigDecimal(util.Random.nextDouble() * 500)
              .setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble,
            merchantId   = merchantId,
            merchantName = merchantName
          )
          context.log.info(s" CustomerAgent generated: ${payment.id} for $${payment.amount} at ${payment.merchantName}")
          merchantAgent ! MerchantAgent.ReceivePayment(payment)  // ← sends to merchant
          Behaviors.same
      }
    }
  }
}
