from __future__ import annotations

from collections import Counter

import numpy as np
from fastapi import FastAPI, File, Form, UploadFile
from sklearn.ensemble import IsolationForest
from sklearn.linear_model import LinearRegression

from .categorization import categorize_one
from .intelligence import build_intelligence, build_pdf_intelligence
from .models import (
    AnomalyItem,
    AnomalyRequest,
    AnomalyResponse,
    CategorizeRequest,
    CategorizeResponse,
    InsightRequest,
    InsightResponse,
    IntelligenceRequestModel,
    IntelligenceResponseModel,
    PdfExtractionResponse,
    PdfIntelligenceResponse,
    PredictRequest,
    PredictResponse,
)
from .pdf_parser import extract_google_pay_transactions

app = FastAPI(title="FinTrack ML Service", version="0.2.0")


@app.get("/health")
def health():
    return {"status": "ok"}


@app.post("/ml/categorize", response_model=CategorizeResponse)
def categorize(request: CategorizeRequest):
    return CategorizeResponse(
        categories=[categorize_one(description, request.type) for description in request.descriptions]
    )


@app.post("/ml/anomaly", response_model=AnomalyResponse)
def anomaly(request: AnomalyRequest):
    if not request.transactions:
        return AnomalyResponse(anomalies=[])

    amounts = np.array([[transaction.amount] for transaction in request.transactions])
    if len(request.transactions) < 3:
        return AnomalyResponse(
            anomalies=[
                AnomalyItem(
                    description=transaction.description,
                    amount=transaction.amount,
                    score=0.0,
                    anomaly=False,
                )
                for transaction in request.transactions
            ]
        )

    model = IsolationForest(random_state=42, contamination="auto")
    model.fit(amounts)
    scores = model.decision_function(amounts)
    flags = model.predict(amounts)

    return AnomalyResponse(
        anomalies=[
            AnomalyItem(
                description=transaction.description,
                amount=transaction.amount,
                score=float(score),
                anomaly=flag == -1,
            )
            for transaction, score, flag in zip(request.transactions, scores, flags)
        ]
    )


@app.post("/ml/predict", response_model=PredictResponse)
def predict(request: PredictRequest):
    if not request.series:
        return PredictResponse(predicted=0.0)
    if len(request.series) == 1:
        return PredictResponse(predicted=float(request.series[0]))

    x = np.arange(len(request.series)).reshape(-1, 1)
    y = np.array(request.series)
    model = LinearRegression()
    model.fit(x, y)
    next_value = model.predict(np.array([[len(request.series)]]))[0]
    return PredictResponse(predicted=max(float(next_value), 0.0))


@app.post("/ml/insights", response_model=InsightResponse)
def insights(request: InsightRequest):
    if not request.categories:
        return InsightResponse(messages=["Upload transactions to generate richer insights."])

    counter = Counter(request.categories)
    total = sum(counter.values()) or 1
    top_category, top_count = counter.most_common(1)[0]
    messages = [
        f"{top_category} is your dominant category at {round(top_count * 100 / total, 1)}% of labeled transactions."
    ]
    if request.predicted_next_month_expense is not None:
        messages.append(
            f"Predicted expense next month is approximately INR {round(request.predicted_next_month_expense, 2)}."
        )
    if len(counter) > 1:
        messages.append(f"You are spending across {len(counter)} major categories.")
    return InsightResponse(messages=messages)


@app.post("/ml/intelligence", response_model=IntelligenceResponseModel)
def intelligence(request: IntelligenceRequestModel):
    return build_intelligence(request)


@app.post("/ml/pdf/extract", response_model=PdfExtractionResponse)
async def pdf_extract(file: UploadFile = File(...)):
    payload = await file.read()
    return extract_google_pay_transactions(payload, file.filename or "google-pay-statement.pdf")


@app.post("/ml/pdf/intelligence", response_model=PdfIntelligenceResponse)
async def pdf_intelligence(
    file: UploadFile = File(...),
    income_stability: float | None = Form(default=None),
    savings_rate: float | None = Form(default=None),
    debt_pressure: float | None = Form(default=None),
):
    payload = await file.read()
    extraction = extract_google_pay_transactions(payload, file.filename or "google-pay-statement.pdf")
    request = IntelligenceRequestModel(
        transactions=extraction.transactions,
        income_stability=income_stability,
        savings_rate=savings_rate,
        debt_pressure=debt_pressure,
    )
    return build_pdf_intelligence(extraction, request)
