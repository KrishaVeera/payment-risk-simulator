package models
// Outcome types for a risk decision
sealed trait Outcome
case object Approved        extends Outcome
case object Declined        extends Outcome
case object HeldForReview   extends Outcome

// Messages
case class PaymentRequest(
                           id: String,
                           cardId: String,
                           amount: Double,
                           merchantId: String,
                           merchantName: String
                         )

case class ForwardToRisk(
                          payment: PaymentRequest,
                          timestamp: Long,
                          location: String
                        )

case class RiskDecision(
                         paymentId: String,
                         outcome: Outcome
                       )