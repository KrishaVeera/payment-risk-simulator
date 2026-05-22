import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import actors._

object Main extends App {

  val system = ActorSystem(
    Behaviors.setup[Any] { context =>

      val riskAgent     = context.spawn(RiskAgent(), "risk-agent")
      val merchantAgent = context.spawn(MerchantAgent(riskAgent), "merchant-agent")
      val customerAgent = context.spawn(CustomerAgent(merchantAgent), "customer-agent")

      Behaviors.empty
    },
    "payment-risk-simulator"
  )

  // Keep the system alive for 30 seconds so actors can run
  Thread.sleep(30000)
  system.terminate()
}