from __future__ import annotations

from .fuzzy_engine import assess_financial_risk
from .hmm_engine import analyze_hidden_states
from .models import IntelligenceRequestModel, IntelligenceResponseModel, PdfIntelligenceResponse, PdfExtractionResponse
from .timeseries import build_category_distribution, build_daily_trends, build_weekly_trends, enrich_transactions


def build_intelligence(request: IntelligenceRequestModel) -> IntelligenceResponseModel:
    enriched_transactions = enrich_transactions(request.transactions)
    daily_trends = build_daily_trends(enriched_transactions)
    weekly_trends = build_weekly_trends(enriched_transactions)
    category_distribution = build_category_distribution(enriched_transactions)
    timeline, hmm_insights, hmm_alerts = analyze_hidden_states(daily_trends)
    risk_assessment, fuzzy_insights, fuzzy_alerts = assess_financial_risk(
        daily_trends,
        request.income_stability,
        request.savings_rate,
        request.debt_pressure,
    )

    insight_messages = [
        "Income and expense trends were generated from structured transaction time-series data.",
        *hmm_insights,
        *fuzzy_insights,
    ]

    return IntelligenceResponseModel(
        extracted_transaction_count=len(enriched_transactions),
        daily_trends=daily_trends,
        weekly_trends=weekly_trends,
        category_distribution=category_distribution,
        hidden_state_timeline=timeline,
        risk_assessment=risk_assessment,
        insights=insight_messages,
        alerts=[*hmm_alerts, *fuzzy_alerts],
    )


def build_pdf_intelligence(extraction: PdfExtractionResponse, request: IntelligenceRequestModel) -> PdfIntelligenceResponse:
    return PdfIntelligenceResponse(
        extraction=extraction,
        intelligence=build_intelligence(request),
    )
