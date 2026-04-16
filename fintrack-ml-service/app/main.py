from __future__ import annotations

from collections import Counter
<<<<<<< HEAD
from typing import List, Optional
=======
from typing import Any, Dict, List, Optional
>>>>>>> e1a9d3d17b04bdb02a96c90202500c7c7b8417de

import numpy as np
import skfuzzy as fuzz
from fastapi import FastAPI, File, Form, UploadFile
<<<<<<< HEAD
from fastapi.middleware.cors import CORSMiddleware
=======
>>>>>>> e1a9d3d17b04bdb02a96c90202500c7c7b8417de
from pydantic import BaseModel
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

# ── Fuzzy request/response models ─────────────────────────────
class FuzzyRiskRequest(BaseModel):
    income_stability: float = 50.0
    expense_level: float = 50.0
    savings_rate: float = 50.0
    debt_pressure: float = 50.0

class BudgetAlertRequest(BaseModel):
    class BudgetItem(BaseModel):
        name: str
        spent: float
        limit: float
    budgets: List[BudgetItem]

class SavingsAdvisorRequest(BaseModel):
    monthly_income: float
    monthly_expenses: float
    current_savings: float
    savings_goal: Optional[float] = None

class AnomalySeverityRequest(BaseModel):
    class TransactionItem(BaseModel):
        id: Optional[int] = None
        description: Optional[str] = None
        amount: float
    transactions: List[TransactionItem]

# ── Pre-compute membership function data (cached at startup) ──
_UNIVERSE = np.arange(0, 101, 1).tolist()

def _trimf_values(points: list[float]) -> list[float]:
    return fuzz.trimf(np.array(_UNIVERSE), points).tolist()

_MEMBERSHIP_DATA: Dict[str, Any] = {
    "universe": _UNIVERSE,
    "variables": {
        "income_stability": {
            "low":    _trimf_values([0, 0, 45]),
            "medium": _trimf_values([30, 50, 70]),
            "high":   _trimf_values([55, 100, 100]),
        },
        "expense_level": {
            "low":    _trimf_values([0, 0, 35]),
            "medium": _trimf_values([25, 50, 75]),
            "high":   _trimf_values([60, 100, 100]),
        },
        "savings_rate": {
            "low":    _trimf_values([0, 0, 30]),
            "medium": _trimf_values([20, 45, 70]),
            "high":   _trimf_values([55, 100, 100]),
        },
        "debt_pressure": {
            "low":    _trimf_values([0, 0, 30]),
            "medium": _trimf_values([20, 45, 70]),
            "high":   _trimf_values([55, 100, 100]),
        },
        "financial_risk": {
            "low":    _trimf_values([0, 0, 40]),
            "medium": _trimf_values([25, 50, 75]),
            "high":   _trimf_values([60, 100, 100]),
        },
        "recommendation": {
            "safe_to_invest":  _trimf_values([0, 15, 30]),
            "save_more":       _trimf_values([25, 40, 55]),
            "reduce_spending": _trimf_values([50, 65, 80]),
            "emergency_alert": _trimf_values([75, 90, 100]),
        },
    }
}

# ── Helpers ───────────────────────────────────────────────────
def _fuzzy_budget_score(pct: float) -> tuple[str, float]:
    """Return (alert_level, score) for a budget usage percentage."""
    u = np.arange(0, 101, 1)
    safe   = fuzz.trimf(u, [0, 0, 50])
    caution= fuzz.trimf(u, [40, 60, 80])
    warning= fuzz.trimf(u, [65, 80, 95])
    critical=fuzz.trimf(u, [85, 100, 100])
    v = float(np.clip(pct, 0, 100))
    ms  = float(fuzz.interp_membership(u, safe,    v))
    mc  = float(fuzz.interp_membership(u, caution, v))
    mw  = float(fuzz.interp_membership(u, warning, v))
    mcr = float(fuzz.interp_membership(u, critical,v))
    best = max([(ms, "Safe"), (mc, "Caution"), (mw, "Warning"), (mcr, "Critical")], key=lambda x: x[0])
    score = round(pct, 1)
    return best[1], score

def _savings_strategy(income: float, expenses: float, savings: float, goal: Optional[float]) -> dict:
    if income <= 0:
        return {"strategy": "Moderate", "target_range": [0, 0], "advice": "Enter your income to get a personalised recommendation."}
    rate = max(0.0, (income - expenses) / income * 100)
    if rate >= 30:
        strategy = "Aggressive"
        target = [int(savings * 1.4), int(savings * 2.0)]
        advice = "You have a strong savings rate. Consider growth investments."
    elif rate >= 15:
        strategy = "Moderate"
        target = [int(savings * 1.1), int(savings * 1.5)]
        advice = "Increase savings gradually — aim for 25%+ savings rate."
    else:
        strategy = "Conservative"
        target = [int(savings * 0.8), int(savings * 1.2)]
        advice = "Focus on reducing expenses before boosting savings contributions."
    return {"strategy": strategy, "target_range": target, "advice": advice}

def _anomaly_severity(amounts: list[float]) -> list[float]:
    if len(amounts) < 2:
        return [0.0] * len(amounts)
    arr = np.array(amounts)
    mean = float(arr.mean())
    std  = float(arr.std()) or 1.0
    scores = []
    for a in amounts:
        z = abs(a - mean) / std
        raw = min(z * 25, 100)          # z=4 → 100
        scores.append(round(raw, 1))
    return scores

app = FastAPI(title="FinTrack ML Service", version="0.2.0")

# Add CORS middleware to allow requests from frontend
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # Allow all origins (for localhost development)
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


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


<<<<<<< HEAD
# ─────────────────────────────────────────────────────────────────────────────
# New Enhanced Fuzzy Logic Endpoints (4 FIS systems + visualization)
# ─────────────────────────────────────────────────────────────────────────────
from pydantic import BaseModel
from typing import List, Optional as Opt
from .fuzzy_engine import (
    get_membership_function_data,
    assess_budget_alert,
    advise_savings_goal,
    score_anomaly_severity,
)


@app.get("/ml/fuzzy/membership-functions")
def fuzzy_mf():
    """Returns all fuzzy membership function data for all 4 FIS systems (for UI visualization)."""
    return get_membership_function_data()


class BudgetAlertRequest(BaseModel):
    budget_utilization_pct: float
    days_remaining: float


@app.post("/ml/fuzzy/budget-alert")
def fuzzy_budget_alert(req: BudgetAlertRequest):
    """Fuzzy budget warning: 2-input (utilization %, days remaining), 9 rules → Safe/Caution/Warning/Critical."""
    return assess_budget_alert(req.budget_utilization_pct, req.days_remaining)


class SavingsAdvisorRequest(BaseModel):
    savings_rate: float
    expense_volatility_pct: float
    income_stability: float
    current_monthly_income: float


@app.post("/ml/fuzzy/savings-advisor")
def fuzzy_savings_advisor(req: SavingsAdvisorRequest):
    """Fuzzy savings goal advisor: 3-input (rate, volatility, stability) → conservative/moderate/aggressive/maximum target."""
    return advise_savings_goal(req.savings_rate, req.expense_volatility_pct, req.income_stability, req.current_monthly_income)


class AnomalySeverityItem(BaseModel):
    amount: float
    category: Optional[str] = None


class AnomalySeverityRequest(BaseModel):
    transactions: List[AnomalySeverityItem]


@app.post("/ml/fuzzy/anomaly-severity")
def fuzzy_anomaly_severity(req: AnomalySeverityRequest):
    """Fuzzy anomaly severity: 3-input (deviation, category_risk, rarity) → Mild/Moderate/Severe per transaction."""
    if not req.transactions:
        return {"results": []}
    amounts = [t.amount for t in req.transactions]
    mean_amt = float(sum(amounts) / len(amounts))
    std_amt  = float((sum((a - mean_amt)**2 for a in amounts) / len(amounts)) ** 0.5)
    from collections import Counter
    cat_counts = Counter(t.category for t in req.transactions if t.category)
    total = len(req.transactions)
    results = []
    for t in req.transactions:
        freq = cat_counts.get(t.category, 0)
        results.append(score_anomaly_severity(t.amount, t.category, mean_amt, std_amt, freq, total))
    return {"results": results, "transaction_count": total, "mean_amount": round(mean_amt, 2)}
=======
# ════════════════════════════════════════════════════════════
# FUZZY LOGIC ENDPOINTS
# ════════════════════════════════════════════════════════════

@app.get("/ml/fuzzy/membership-functions")
def fuzzy_membership_functions():
    """Return pre-computed membership function data for all FIS variables. Cached at startup."""
    return _MEMBERSHIP_DATA


@app.post("/ml/fuzzy/risk")
def fuzzy_risk(request: FuzzyRiskRequest):
    """Run the Financial Risk FIS and return risk + recommendation scores."""
    from .fuzzy_engine import _build_simulation, _risk_label, _recommendation_label
    try:
        sim = _build_simulation()
        sim.input["income_stability"] = float(np.clip(request.income_stability, 0, 100))
        sim.input["expense_level"]    = float(np.clip(request.expense_level,    0, 100))
        sim.input["savings_rate"]     = float(np.clip(request.savings_rate,     0, 100))
        sim.input["debt_pressure"]    = float(np.clip(request.debt_pressure,    0, 100))
        sim.compute()
        risk_score   = round(float(sim.output["financial_risk"]), 2)
        rec_score    = round(float(sim.output["recommendation"]),  2)
        return {
            "financial_risk": risk_score,
            "risk_label": _risk_label(risk_score),
            "recommendation": rec_score,
            "recommendation_label": _recommendation_label(rec_score),
        }
    except Exception as exc:
        return {"financial_risk": 50.0, "risk_label": "medium", "recommendation": 45.0, "recommendation_label": "save_more", "error": str(exc)}


@app.post("/ml/fuzzy/budget-alert")
def fuzzy_budget_alert(request: BudgetAlertRequest):
    """Return fuzzy alert level for each budget item."""
    results = []
    for b in request.budgets:
        limit = b.limit if b.limit > 0 else 1.0
        pct   = min((b.spent / limit) * 100, 100)
        level, score = _fuzzy_budget_score(pct)
        results.append({
            "name":        b.name,
            "spent":       b.spent,
            "limit":       b.limit,
            "pct_used":    round(pct, 1),
            "alert_level": level,
            "score":       score,
        })
    return {"alerts": results}


@app.post("/ml/fuzzy/savings-advisor")
def fuzzy_savings_advisor(request: SavingsAdvisorRequest):
    """Return a fuzzy savings strategy with target range and advice."""
    result = _savings_strategy(
        income   = request.monthly_income,
        expenses = request.monthly_expenses,
        savings  = request.current_savings,
        goal     = request.savings_goal,
    )
    return result


@app.post("/ml/fuzzy/anomaly-severity")
def fuzzy_anomaly_severity(request: AnomalySeverityRequest):
    """Score each transaction by anomaly severity using fuzzy z-score mapping."""
    if not request.transactions:
        return []
    amounts = [t.amount for t in request.transactions]
    scores  = _anomaly_severity(amounts)
    results = []
    for i, (tx, score) in enumerate(zip(request.transactions, scores)):
        if score >= 70:
            label = "Severe"
        elif score >= 40:
            label = "Moderate"
        else:
            label = "Mild"
        results.append({
            "transaction_id": tx.id if tx.id is not None else i + 1,
            "description":    tx.description or f"Transaction {i+1}",
            "amount":         tx.amount,
            "severity":       score,
            "label":          label,
        })
    # Sort descending by severity
    results.sort(key=lambda x: x["severity"], reverse=True)
    return results[:10]

>>>>>>> e1a9d3d17b04bdb02a96c90202500c7c7b8417de
