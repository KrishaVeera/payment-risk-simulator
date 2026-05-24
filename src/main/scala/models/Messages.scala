package models

sealed trait Outcome
case object Approved      extends Outcome
case object Declined      extends Outcome
case object HeldForReview extends Outcome

case class PaymentRequest(
                           id:           String,
                           cardId:       String,
                           amount:       Double,
                           merchantId:   String,
                           merchantName: String,
                           timestamp:    Long,
                           location:     String
                         )

case class ForwardToRisk(
                          payment:   PaymentRequest,
                          timestamp: Long,
                          location:  String
                        )

case class RiskDecision(
                         paymentId: String,
                         outcome:   Outcome
                       )

case class PaymentEvent(
                         paymentId:     String,
                         cardId:        String,
                         amount:        Double,
                         merchantId:    String,
                         merchantName:  String,
                         location:      String,
                         timestamp:     Long,
                         outcome:       Outcome,
                         mlProbability: Double  // -1.0 if Flask unavailable
                       )