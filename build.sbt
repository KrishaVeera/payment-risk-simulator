name := "payment-risk-simulator"
version := "0.1.0"
scalaVersion := "2.13.12"

val AkkaVersion = "2.8.5"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor-typed" % AkkaVersion,
  "com.typesafe.akka" %% "akka-stream"      % AkkaVersion,
  "com.typesafe.akka" %% "akka-slf4j"       % AkkaVersion,
  "ch.qos.logback"     % "logback-classic"  % "1.4.11",
  "com.typesafe.akka" %% "akka-actor-testkit-typed" % AkkaVersion % Test,
  "org.scalatest"     %% "scalatest"        % "3.2.17" % Test
)