import pandas as pd
import numpy as np

np.random.seed(42)

TOTAL = 10000
FRAUD_COUNT = 2000
LEGIT_COUNT = TOTAL - FRAUD_COUNT

def generate_legit(n):
    return pd.DataFrame({
        "amount":                np.random.uniform(10, 800, n),
        "velocity":              np.random.randint(1, 5, n),
        "location_risk":         np.random.choice([0, 0, 0, 0, 1], n),
        "hour":                  np.random.randint(6, 23, n),
        "is_high_risk_merchant": np.random.choice([0, 1], n),
        "is_fraud":              0
    })

def generate_fraud(n):
    # 70% classic fraud — high velocity, flagged location
    classic = int(n * 0.7)
    # 30% sneaky fraud — looks like legit behaviour
    sneaky  = n - classic

    classic_df = pd.DataFrame({
        "amount":                np.random.uniform(400, 1000, classic),
        "velocity":              np.random.randint(4, 15, classic),
        "location_risk":         np.random.choice([1, 1, 1, 0], classic),
        "hour":                  np.random.randint(0, 24, classic),
        "is_high_risk_merchant": np.random.choice([0, 1], classic),
        "is_fraud":              1
    })

    sneaky_df = pd.DataFrame({
        "amount":                np.random.uniform(10, 500, sneaky),
        "velocity":              np.random.randint(1, 3, sneaky),
        "location_risk":         np.random.choice([0, 0, 1], sneaky),
        "hour":                  np.random.randint(8, 22, sneaky),
        "is_high_risk_merchant": np.random.choice([0, 1], sneaky),
        "is_fraud":              1
    })

    return pd.concat([classic_df, sneaky_df])

legit = generate_legit(LEGIT_COUNT)
fraud = generate_fraud(FRAUD_COUNT)

df = pd.concat([legit, fraud]).sample(frac=1, random_state=42).reset_index(drop=True)

df.to_csv("transactions.csv", index=False)
print(f"Generated {len(df)} transactions")
print(df["is_fraud"].value_counts())
print(df.head())