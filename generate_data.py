import pandas as pd
import numpy as np

np.random.seed(42)

TOTAL = 10000
FRAUD_COUNT = 2000
LEGIT_COUNT = TOTAL - FRAUD_COUNT

def generate_legit(n):
    return pd.DataFrame({
        "amount": np.random.uniform(10, 800, n),
        "velocity": np.random.randint(1, 5, n),
        "location_risk": np.random.choice([0, 0, 0, 0, 1], n),
        "hour": np.random.randint(6, 23, n),
        "is_high_risk_merchant": np.random.choice([0, 1], n),
        "is_fraud": 0
    })

def generate_fraud(n):
    return pd.DataFrame({
        "amount": np.random.uniform(200, 1000, n),
        "velocity": np.random.randint(2, 15, n),
        "location_risk": np.random.choice([0, 0, 0, 1, 1, 1, 1], n),
        "hour": np.random.randint(0, 24, n),
        "is_high_risk_merchant": np.random.choice([0, 1], n),
        "is_fraud": 1
    })

legit = generate_legit(LEGIT_COUNT)
fraud = generate_fraud(FRAUD_COUNT)

df = pd.concat([legit, fraud]).sample(frac=1, random_state=42).reset_index(drop=True)

df.to_csv("transactions.csv", index=False)
print(f"Generated {len(df)} transactions")
print(df["is_fraud"].value_counts())
print(df.head())