package actors

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import models._

import java.sql.DriverManager
import scala.concurrent.duration._

object EventStoreActor {

  sealed trait Command
  case class RecordEvent(payment: PaymentRequest, decision: RiskDecision) extends Command
  case object PrintSummary extends Command

  // Database file will be created in your project root as events.db
  val DbUrl = "jdbc:sqlite:events.db"

  // Initialize DB on startup — create table if it doesn't exist
  def initDb(): Unit = {
    val conn = DriverManager.getConnection(DbUrl)
    val stmt = conn.createStatement()
    stmt.execute("""
      CREATE TABLE IF NOT EXISTS payment_events (
        payment_id    TEXT PRIMARY KEY,
        card_id       TEXT NOT NULL,
        amount        REAL NOT NULL,
        merchant_id   TEXT NOT NULL,
        merchant_name TEXT NOT NULL,
        location      TEXT NOT NULL,
        timestamp     INTEGER NOT NULL,
        outcome       TEXT NOT NULL
      )
    """)
    stmt.close()
    conn.close()
  }

  def insertEvent(event: PaymentEvent): Unit = {
    val conn = DriverManager.getConnection(DbUrl)
    val ps = conn.prepareStatement("""
      INSERT OR IGNORE INTO payment_events
        (payment_id, card_id, amount, merchant_id, merchant_name, location, timestamp, outcome)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?)
    """)
    ps.setString(1, event.paymentId)
    ps.setString(2, event.cardId)
    ps.setDouble(3, event.amount)
    ps.setString(4, event.merchantId)
    ps.setString(5, event.merchantName)
    ps.setString(6, event.location)
    ps.setLong(7, event.timestamp)
    ps.setString(8, event.outcome.toString)
    ps.executeUpdate()
    ps.close()
    conn.close()
  }

  def loadSummary(): (Int, Int, Int, Int) = {
    val conn  = DriverManager.getConnection(DbUrl)
    val stmt  = conn.createStatement()
    val rs    = stmt.executeQuery("SELECT outcome, COUNT(*) as cnt FROM payment_events GROUP BY outcome")
    var approved = 0; var declined = 0; var held = 0
    while (rs.next()) {
      rs.getString("outcome") match {
        case "Approved"      => approved = rs.getInt("cnt")
        case "Declined"      => declined = rs.getInt("cnt")
        case "HeldForReview" => held     = rs.getInt("cnt")
        case _               =>
      }
    }
    rs.close(); stmt.close(); conn.close()
    val total = approved + declined + held
    (total, approved, declined, held)
  }

  def apply(): Behavior[Command] = Behaviors.setup { context =>
    initDb()
    context.log.info("EventStoreActor started — SQLite database initialized")

    Behaviors.withTimers { timers =>
      timers.startTimerWithFixedDelay(PrintSummary, 30.seconds)

      Behaviors.receiveMessage {
        case RecordEvent(payment, decision) =>
          val event = PaymentEvent(
            paymentId    = payment.id,
            cardId       = payment.cardId,
            amount       = payment.amount,
            merchantId   = payment.merchantId,
            merchantName = payment.merchantName,
            location     = payment.location,
            timestamp    = payment.timestamp,
            outcome      = decision.outcome
          )
          insertEvent(event)
          Behaviors.same

        case PrintSummary =>
          val (total, approved, declined, held) = loadSummary()
          val approvalRate = if (total == 0) 0.0 else approved.toDouble / total * 100
          val fraudRate    = if (total == 0) 0.0 else declined.toDouble / total * 100
          context.log.info("=" * 50)
          context.log.info("  EVENT STORE SUMMARY (persistent)")
          context.log.info(s"  Total decisions : $total")
          context.log.info(f"  Approved        : $approved ($approvalRate%.1f%%)")
          context.log.info(f"  Declined        : $declined ($fraudRate%.1f%%)")
          context.log.info(s"  Held for review : $held")
          context.log.info("=" * 50)
          Behaviors.same
      }
    }
  }
}