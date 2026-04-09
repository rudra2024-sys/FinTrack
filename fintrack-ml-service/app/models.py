from __future__ import annotations

from datetime import date
from typing import List, Literal, Optional

from pydantic import BaseModel, Field


TransactionType = Literal["credit", "debit"]


class CategorizeRequest(BaseModel):
    descriptions: List[str]
    type: str


class CategorizeResponse(BaseModel):
    categories: List[str]


class AnomalyTransaction(BaseModel):
    amount: float
    description: str


class AnomalyRequest(BaseModel):
    transactions: List[AnomalyTransaction]


class AnomalyItem(BaseModel):
    description: str
    amount: float
    score: float
    anomaly: bool


class AnomalyResponse(BaseModel):
    anomalies: List[AnomalyItem]


class PredictRequest(BaseModel):
    series: List[float]


class PredictResponse(BaseModel):
    predicted: float


class InsightRequest(BaseModel):
    categories: List[str]
    predicted_next_month_expense: Optional[float] = None


class InsightResponse(BaseModel):
    messages: List[str]


class TransactionRecord(BaseModel):
    date: date
    time: Optional[str] = None
    transaction_type: TransactionType
    merchant_person: str
    amount: float = Field(ge=0)
    description: Optional[str] = None
    category: Optional[str] = None
    confidence: Optional[float] = None


class PdfExtractionResponse(BaseModel):
    source: str
    transaction_count: int
    transactions: List[TransactionRecord]


class IntelligenceRequestModel(BaseModel):
    transactions: List[TransactionRecord]
    income_stability: Optional[float] = Field(default=None, ge=0, le=100)
    savings_rate: Optional[float] = Field(default=None, ge=-100, le=100)
    debt_pressure: Optional[float] = Field(default=None, ge=0, le=100)


class TrendPoint(BaseModel):
    period: str
    income: float
    expense: float
    net: float


class CategorySpend(BaseModel):
    category: str
    amount: float
    confidence: float


class HiddenStatePoint(BaseModel):
    date: str
    expense: float
    hidden_state: str
    anomaly: bool
    anomaly_score: float


class RiskAssessment(BaseModel):
    income_stability: float
    expense_level: float
    savings_rate: float
    debt_pressure: float
    financial_risk: str
    risk_score: float
    recommendation: str


class AlertItem(BaseModel):
    level: str
    title: str
    detail: str


class IntelligenceResponseModel(BaseModel):
    extracted_transaction_count: int
    daily_trends: List[TrendPoint]
    weekly_trends: List[TrendPoint]
    category_distribution: List[CategorySpend]
    hidden_state_timeline: List[HiddenStatePoint]
    risk_assessment: RiskAssessment
    insights: List[str]
    alerts: List[AlertItem]


class PdfIntelligenceResponse(BaseModel):
    extraction: PdfExtractionResponse
    intelligence: IntelligenceResponseModel
