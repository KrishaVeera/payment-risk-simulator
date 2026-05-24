package actors

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import spray.json._
import java.sql.DriverManager

object DashboardServer {

  case class TransactionRow(
                             paymentId:     String,
                             cardId:        String,
                             amount:        Double,
                             merchantName:  String,
                             location:      String,
                             outcome:       String,
                             timestamp:     Long,
                             mlProbability: Double
                           )

  case class StatsRow(
                       total:        Int,
                       approved:     Int,
                       declined:     Int,
                       held:         Int,
                       approvalRate: Double,
                       fraudRate:    Double
                     )

  object JsonProtocol extends DefaultJsonProtocol {
    implicit val transactionFormat: RootJsonFormat[TransactionRow] = jsonFormat8(TransactionRow)
    implicit val statsFormat:       RootJsonFormat[StatsRow]       = jsonFormat6(StatsRow)
  }

  import JsonProtocol._

  val DbUrl = "jdbc:sqlite:events.db"

  def recentEvents(limit: Int = 20): List[TransactionRow] = {
    val conn = DriverManager.getConnection(DbUrl)
    val stmt = conn.createStatement()
    val rs   = stmt.executeQuery(
      s"SELECT payment_id, card_id, amount, merchant_name, location, outcome, timestamp, ml_probability FROM payment_events ORDER BY timestamp DESC LIMIT $limit"
    )
    val rows = scala.collection.mutable.ListBuffer[TransactionRow]()
    while (rs.next()) {
      rows += TransactionRow(
        rs.getString("payment_id"),
        rs.getString("card_id"),
        rs.getDouble("amount"),
        rs.getString("merchant_name"),
        rs.getString("location"),
        rs.getString("outcome"),
        rs.getLong("timestamp"),
        rs.getDouble("ml_probability")
      )
    }
    rs.close(); stmt.close(); conn.close()
    rows.toList
  }

  def stats(): StatsRow = {
    val conn = DriverManager.getConnection(DbUrl)
    val stmt = conn.createStatement()
    val rs   = stmt.executeQuery(
      "SELECT outcome, COUNT(*) as cnt FROM payment_events GROUP BY outcome"
    )
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
    val total        = approved + declined + held
    val approvalRate = if (total == 0) 0.0 else approved.toDouble / total * 100
    val fraudRate    = if (total == 0) 0.0 else declined.toDouble / total * 100
    StatsRow(total, approved, declined, held, approvalRate, fraudRate)
  }

  val dashboard: String = """
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Payment Risk Simulator</title>
  <style>
    * { margin: 0; padding: 0; box-sizing: border-box; }

    body {
      font-family: 'Segoe UI', sans-serif;
      background: #0f1117;
      color: #e2e8f0;
      min-height: 100vh;
    }

    header {
      background: linear-gradient(135deg, #1e3a5f, #0f1117);
      padding: 24px 40px;
      border-bottom: 1px solid #1e293b;
      display: flex;
      align-items: center;
      justify-content: space-between;
    }

    header h1 {
      font-size: 1.5rem;
      font-weight: 700;
      color: #60a5fa;
      letter-spacing: 0.5px;
    }

    header p {
      font-size: 0.85rem;
      color: #64748b;
      margin-top: 4px;
    }

    .live-badge {
      display: flex;
      align-items: center;
      gap: 8px;
      font-size: 0.8rem;
      color: #4ade80;
      font-weight: 600;
    }

    .dot {
      width: 8px;
      height: 8px;
      border-radius: 50%;
      background: #4ade80;
      animation: pulse 1.5s infinite;
    }

    @keyframes pulse {
      0%, 100% { opacity: 1; }
      50%       { opacity: 0.3; }
    }

    .stats {
      display: grid;
      grid-template-columns: repeat(4, 1fr);
      gap: 16px;
      padding: 24px 40px;
    }

    .stat-card {
      background: #1e293b;
      border-radius: 12px;
      padding: 20px;
      border: 1px solid #334155;
    }

    .stat-card .label {
      font-size: 0.75rem;
      color: #64748b;
      text-transform: uppercase;
      letter-spacing: 1px;
      margin-bottom: 8px;
    }

    .stat-card .value {
      font-size: 2rem;
      font-weight: 700;
      color: #f1f5f9;
    }

    .stat-card .sub {
      font-size: 0.8rem;
      color: #94a3b8;
      margin-top: 4px;
    }

    .stat-card.green  .value { color: #4ade80; }
    .stat-card.red    .value { color: #f87171; }
    .stat-card.yellow .value { color: #fbbf24; }

    .table-section {
      padding: 0 40px 40px;
    }

    .table-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 16px;
    }

    .table-header h2 {
      font-size: 1rem;
      font-weight: 600;
      color: #94a3b8;
      text-transform: uppercase;
      letter-spacing: 1px;
    }

    .last-updated { font-size: 0.75rem; color: #475569; }

    table {
      width: 100%;
      border-collapse: collapse;
      background: #1e293b;
      border-radius: 12px;
      overflow: hidden;
      border: 1px solid #334155;
    }

    thead th {
      background: #0f172a;
      padding: 14px 16px;
      text-align: left;
      font-size: 0.75rem;
      color: #64748b;
      text-transform: uppercase;
      letter-spacing: 1px;
      font-weight: 600;
    }

    tbody tr {
      border-top: 1px solid #334155;
      transition: background 0.15s;
    }

    tbody tr:hover { background: #263548; }

    tbody td {
      padding: 14px 16px;
      font-size: 0.875rem;
    }

    .badge {
      display: inline-block;
      padding: 4px 10px;
      border-radius: 20px;
      font-size: 0.75rem;
      font-weight: 600;
    }

    .badge.approved {
      background: rgba(74, 222, 128, 0.15);
      color: #4ade80;
      border: 1px solid rgba(74, 222, 128, 0.3);
    }

    .badge.declined {
      background: rgba(248, 113, 113, 0.15);
      color: #f87171;
      border: 1px solid rgba(248, 113, 113, 0.3);
    }

    .badge.held {
      background: rgba(251, 191, 36, 0.15);
      color: #fbbf24;
      border: 1px solid rgba(251, 191, 36, 0.3);
    }

    .ml-prob {
      font-family: monospace;
      font-size: 0.8rem;
      font-weight: 600;
    }

    .ml-prob.high   { color: #f87171; }
    .ml-prob.medium { color: #fbbf24; }
    .ml-prob.low    { color: #4ade80; }
    .ml-prob.na     { color: #475569; }

    .amount     { font-weight: 600; color: #e2e8f0; }
    .card-id    { font-family: monospace; font-size: 0.8rem; color: #94a3b8; }
    .payment-id { font-family: monospace; font-size: 0.7rem; color: #475569; }
  </style>
</head>
<body>

<header>
  <div>
    <h1>Payment Risk Simulator</h1>
    <p>Real-time fraud detection powered by Akka actors + LightGBM</p>
  </div>
  <div class="live-badge">
    <div class="dot"></div>
    LIVE
  </div>
</header>

<div class="stats">
  <div class="stat-card">
    <div class="label">Total Decisions</div>
    <div class="value" id="total">—</div>
    <div class="sub">across all sessions</div>
  </div>
  <div class="stat-card green">
    <div class="label">Approved</div>
    <div class="value" id="approved">—</div>
    <div class="sub" id="approval-rate">—</div>
  </div>
  <div class="stat-card red">
    <div class="label">Declined</div>
    <div class="value" id="declined">—</div>
    <div class="sub" id="fraud-rate">—</div>
  </div>
  <div class="stat-card yellow">
    <div class="label">Held for Review</div>
    <div class="value" id="held">—</div>
    <div class="sub">manual review queue</div>
  </div>
</div>

<div class="table-section">
  <div class="table-header">
    <h2>Recent Transactions</h2>
    <span class="last-updated" id="last-updated">Updating...</span>
  </div>
  <table>
    <thead>
      <tr>
        <th>Payment ID</th>
        <th>Card</th>
        <th>Amount</th>
        <th>Merchant</th>
        <th>Location</th>
        <th>ML Score</th>
        <th>Decision</th>
      </tr>
    </thead>
    <tbody id="transactions">
      <tr><td colspan="7" style="text-align:center; color:#475569; padding:40px;">
        Loading transactions...
      </td></tr>
    </tbody>
  </table>
</div>

<script>
  function badgeClass(outcome) {
    if (outcome === 'Approved') return 'approved';
    if (outcome === 'Declined') return 'declined';
    return 'held';
  }

  function mlClass(prob) {
    if (prob < 0)    return 'na';
    if (prob >= 0.7) return 'high';
    if (prob >= 0.4) return 'medium';
    return 'low';
  }

  function mlLabel(prob) {
    if (prob < 0) return '—';
    return (prob * 100).toFixed(1) + '%';
  }

  function shortId(id) {
    return id.substring(0, 8) + '...';
  }

  async function refresh() {
    try {
      const [eventsRes, statsRes] = await Promise.all([
        fetch('/events'),
        fetch('/stats')
      ]);

      const events = await eventsRes.json();
      const stats  = await statsRes.json();

      document.getElementById('total').textContent    = stats.total;
      document.getElementById('approved').textContent = stats.approved;
      document.getElementById('declined').textContent = stats.declined;
      document.getElementById('held').textContent     = stats.held;
      document.getElementById('approval-rate').textContent =
        stats.approvalRate.toFixed(1) + '% approval rate';
      document.getElementById('fraud-rate').textContent =
        stats.fraudRate.toFixed(1) + '% decline rate';

      const tbody = document.getElementById('transactions');
      tbody.innerHTML = events.map(e => `
        <tr>
          <td class="payment-id">${shortId(e.paymentId)}</td>
          <td class="card-id">${e.cardId}</td>
          <td class="amount">$${e.amount.toFixed(2)}</td>
          <td>${e.merchantName}</td>
          <td>${e.location}</td>
          <td><span class="ml-prob ${mlClass(e.mlProbability)}">${mlLabel(e.mlProbability)}</span></td>
          <td><span class="badge ${badgeClass(e.outcome)}">${e.outcome}</span></td>
        </tr>
      `).join('');

      document.getElementById('last-updated').textContent =
        'Updated ' + new Date().toLocaleTimeString();

    } catch (e) {
      document.getElementById('last-updated').textContent = 'Connection error — retrying...';
    }
  }

  refresh();
  setInterval(refresh, 2000);
</script>

</body>
</html>
"""

  def start()(implicit system: ActorSystem[_]): Unit = {
    import system.executionContext

    val route =
      path("") {
        get {
          complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, dashboard))
        }
      } ~
        path("events") {
          get {
            complete(recentEvents(20).toJson.asInstanceOf[spray.json.JsValue])
          }
        } ~
        path("stats") {
          get {
            complete(stats().toJson.asInstanceOf[spray.json.JsValue])
          }
        }

    Http().newServerAt("localhost", 8080).bind(route)
    println("Dashboard running at http://localhost:8080")
  }
}