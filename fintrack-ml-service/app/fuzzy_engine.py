from __future__ import annotations

import numpy as np
import skfuzzy as fuzz
from skfuzzy import control as ctrl

from .models import AlertItem, RiskAssessment, TrendPoint


RECOMMENDATION_SCALE = {
    20: "safe_to_invest",
    45: "save_more",
    65: "reduce_spending",
    90: "emergency_alert",
}


def assess_financial_risk(
    daily_trends: list[TrendPoint],
    income_stability: float | None,
    savings_rate: float | None,
    debt_pressure: float | None,
) -> tuple[RiskAssessment, list[str], list[AlertItem]]:
    computed_income_stability = income_stability if income_stability is not None else _derive_income_stability(daily_trends)
    computed_expense_level = _derive_expense_level(daily_trends)
    computed_savings_rate = savings_rate if savings_rate is not None else _derive_savings_rate(daily_trends)
    computed_debt_pressure = debt_pressure if debt_pressure is not None else _derive_debt_pressure(daily_trends)

    simulation = _build_simulation()
    simulation.input["income_stability"] = computed_income_stability
    simulation.input["expense_level"] = computed_expense_level
    simulation.input["savings_rate"] = max(0.0, min(100.0, computed_savings_rate))
    simulation.input["debt_pressure"] = computed_debt_pressure
    simulation.compute()

    risk_score = float(simulation.output["financial_risk"])
    recommendation_score = float(simulation.output["recommendation"])
    risk_label = _risk_label(risk_score)
    recommendation = _recommendation_label(recommendation_score)

    insights = [
        f"Fuzzy risk engine classifies the current profile as '{risk_label}' with a score of {risk_score:.1f}/100.",
        f"Primary recommendation is '{recommendation}'.",
    ]
    alerts: list[AlertItem] = []
    if risk_label == "high":
        alerts.append(AlertItem(
            level="high",
            title="High financial risk",
            detail="Income stability, expense pressure, savings, and debt indicators point to a high-risk profile.",
        ))

    return (
        RiskAssessment(
            income_stability=round(computed_income_stability, 2),
            expense_level=round(computed_expense_level, 2),
            savings_rate=round(computed_savings_rate, 2),
            debt_pressure=round(computed_debt_pressure, 2),
            financial_risk=risk_label,
            risk_score=round(risk_score, 2),
            recommendation=recommendation,
        ),
        insights,
        alerts,
    )


def _build_simulation() -> ctrl.ControlSystemSimulation:
    universe = np.arange(0, 101, 1)

    income = ctrl.Antecedent(universe, "income_stability")
    expense = ctrl.Antecedent(universe, "expense_level")
    savings = ctrl.Antecedent(universe, "savings_rate")
    debt = ctrl.Antecedent(universe, "debt_pressure")
    risk = ctrl.Consequent(universe, "financial_risk")
    recommendation = ctrl.Consequent(universe, "recommendation")

    income["low"] = fuzz.trimf(universe, [0, 0, 45])
    income["medium"] = fuzz.trimf(universe, [30, 50, 70])
    income["high"] = fuzz.trimf(universe, [55, 100, 100])

    expense["low"] = fuzz.trimf(universe, [0, 0, 35])
    expense["medium"] = fuzz.trimf(universe, [25, 50, 75])
    expense["high"] = fuzz.trimf(universe, [60, 100, 100])

    savings["low"] = fuzz.trimf(universe, [0, 0, 30])
    savings["medium"] = fuzz.trimf(universe, [20, 45, 70])
    savings["high"] = fuzz.trimf(universe, [55, 100, 100])

    debt["low"] = fuzz.trimf(universe, [0, 0, 30])
    debt["medium"] = fuzz.trimf(universe, [20, 45, 70])
    debt["high"] = fuzz.trimf(universe, [55, 100, 100])

    risk["low"] = fuzz.trimf(universe, [0, 0, 40])
    risk["medium"] = fuzz.trimf(universe, [25, 50, 75])
    risk["high"] = fuzz.trimf(universe, [60, 100, 100])

    recommendation["safe_to_invest"] = fuzz.trimf(universe, [0, 15, 30])
    recommendation["save_more"] = fuzz.trimf(universe, [25, 40, 55])
    recommendation["reduce_spending"] = fuzz.trimf(universe, [50, 65, 80])
    recommendation["emergency_alert"] = fuzz.trimf(universe, [75, 90, 100])

    rules = [
        ctrl.Rule(income["high"] & expense["low"] & savings["high"] & debt["low"], (risk["low"], recommendation["safe_to_invest"])),
        ctrl.Rule(expense["medium"] & savings["medium"], (risk["medium"], recommendation["save_more"])),
        ctrl.Rule(expense["high"] | debt["high"], (risk["high"], recommendation["reduce_spending"])),
        ctrl.Rule(income["low"] & debt["high"], (risk["high"], recommendation["emergency_alert"])),
        ctrl.Rule(savings["low"] & expense["high"], (risk["high"], recommendation["reduce_spending"])),
        ctrl.Rule(income["medium"] & debt["medium"] & expense["medium"], (risk["medium"], recommendation["save_more"])),
    ]

    system = ctrl.ControlSystem(rules)
    return ctrl.ControlSystemSimulation(system)


def _derive_income_stability(daily_trends: list[TrendPoint]) -> float:
    incomes = [point.income for point in daily_trends if point.income > 0]
    if len(incomes) < 2:
        return 50.0
    avg = float(np.mean(incomes))
    std = float(np.std(incomes))
    cv = std / avg if avg else 1.0
    return max(0.0, min(100.0, 100 - (cv * 100)))


def _derive_expense_level(daily_trends: list[TrendPoint]) -> float:
    income = sum(point.income for point in daily_trends)
    expense = sum(point.expense for point in daily_trends)
    if income <= 0:
        return 75.0 if expense > 0 else 0.0
    return max(0.0, min(100.0, (expense / income) * 100))


def _derive_savings_rate(daily_trends: list[TrendPoint]) -> float:
    income = sum(point.income for point in daily_trends)
    expense = sum(point.expense for point in daily_trends)
    if income <= 0:
        return 0.0
    return ((income - expense) / income) * 100


def _derive_debt_pressure(daily_trends: list[TrendPoint]) -> float:
    negative_days = sum(1 for point in daily_trends if point.net < 0)
    total_days = len(daily_trends) or 1
    return (negative_days / total_days) * 100


def _risk_label(score: float) -> str:
    if score < 35:
        return "low"
    if score < 65:
        return "medium"
    return "high"


def _recommendation_label(score: float) -> str:
    closest = min(RECOMMENDATION_SCALE, key=lambda key: abs(key - score))
    return RECOMMENDATION_SCALE[closest]
