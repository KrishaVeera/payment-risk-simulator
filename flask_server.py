from flask import Flask, request, jsonify
import pickle
import numpy as np

app = Flask(__name__)

# Load the trained model once on startup
with open("fraud_model.pkl", "rb") as f:
    model = pickle.load(f)

print("Model loaded successfully")

@app.route("/score", methods=["POST"])
def score():
    data = request.get_json()

    features = [[
        data["amount"],
        data["velocity"],
        data["location_risk"],
        data["hour"],
        data["is_high_risk_merchant"]
    ]]

    probability = model.predict_proba(features)[0][1]
    is_fraud = bool(probability >= 0.5)

    return jsonify({
        "fraud_probability": round(probability, 4),
        "is_fraud": is_fraud
    })

if __name__ == "__main__":
    app.run(port=5001, debug=False)