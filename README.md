# Payment Risk Simulator

A real-time fraud detection system built with Akka Typed (Scala), LightGBM (Python), and a live browser dashboard. 
Autonomous agents simulate payment traffic, detect fraud using both rule-based logic and a trained ML model, and persist every decision to SQLite - all visible in a live dashboard.

Multiple independent agents run concurrently and communicate through messages:

- CustomerAgent generates realistic payment transactions every ~2 seconds
- FraudsterAgent generates suspicious transactions at high velocity, mimicking a compromised card
- MerchantAgent receives all payments and routes them through the risk pipeline
- ChaosAgent randomly delays, drops, or duplicates messages — testing system resilience
- RiskAgent scores every transaction using rules AND calls a LightGBM ML model via Flask
- EventStoreActor persists every decision to SQLite — survives restarts
- DashboardServer serves a live browser UI showing transactions and fraud statistics in real time

### Architecture

<img width="781" height="279" alt="image" src="https://github.com/user-attachments/assets/5f6b31c4-8e60-4d01-8d0d-12c4d10d6bd4" />

Every payment flows:

- Agent generates a PaymentRequest
- MerchantAgent receives it and forwards to ChaosAgent
- ChaosAgent may delay, drop, or duplicate it
- RiskAgent scores it using rules + ML model
- Decision is sent back to MerchantAgent and recorded in SQLite
- Browser dashboard reads from SQLite every 2 seconds

### Project Structure

<img width="781" height="508" alt="image" src="https://github.com/user-attachments/assets/5ceadbdd-ac2f-4edf-b88b-a749cfe2d032" />
