import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import actors._

import scala.concurrent.Await
import scala.concurrent.duration._

object Main extends App {

  val system = ActorSystem(
    Behaviors.setup[Any] { context =>
      val eventStore    = context.spawn(EventStoreActor(), "event-store")
      val riskAgent     = context.spawn(RiskAgent(eventStore), "risk-agent")
      val chaosAgent    = context.spawn(ChaosAgent(riskAgent), "chaos-agent")
      val merchantAgent = context.spawn(MerchantAgent(chaosAgent), "merchant-agent")
      val customerAgent = context.spawn(CustomerAgent(merchantAgent), "customer-agent")
      val fraudster     = context.spawn(FraudsterAgent(merchantAgent), "fraudster-agent")
      Behaviors.empty
    },
    "payment-risk-simulator"
  )

  // Run for 60 seconds then shut down
  Thread.sleep(60000)
  system.terminate()
  Await.result(system.whenTerminated, 5.seconds)
}