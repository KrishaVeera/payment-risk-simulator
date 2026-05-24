import pandas as pd
import lightgbm as lgb
from sklearn.model_selection import train_test_split
from sklearn.metrics import classification_report
import pickle

# Load the generated data
df = pd.read_csv("transactions.csv")

features = ["amount", "velocity", "location_risk", "hour", "is_high_risk_merchant"]
X = df[features]
y = df["is_fraud"]

# Split into train and test sets (80% train, 20% test)
X_train, X_test, y_train, y_test = train_test_split(
    X, y, test_size=0.2, random_state=42, stratify=y
)

# Train LightGBM
model = lgb.LGBMClassifier(
    n_estimators=100,
    learning_rate=0.1,
    num_leaves=31,
    random_state=42
)
model.fit(X_train, y_train)

# Evaluate
y_pred = model.predict(X_test)
print(classification_report(y_test, y_pred, target_names=["Legit", "Fraud"]))

# Save the model
with open("fraud_model.pkl", "wb") as f:
    pickle.dump(model, f)

print("Model saved to fraud_model.pkl")