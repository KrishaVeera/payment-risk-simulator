import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import actors.HelloActor

object main extends App {

  val system = ActorSystem(
    Behaviors.setup[Any] { context =>
      val helloActor = context.spawn(HelloActor(), "hello-actor")
      helloActor ! HelloActor.SayHello("World")
      Behaviors.empty
    },
    name = "payment-risk-system"
  )

  Thread.sleep(500)
  system.terminate()
}