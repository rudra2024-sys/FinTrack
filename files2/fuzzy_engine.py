"""
Enhanced Fuzzy Logic Engine for FinTrack
=========================================
4 FIS systems:
1. Main Financial Risk Assessment (4 inputs, 18 rules, Mamdani + centroid)
2. Budget Fuzzy Warning System (2 inputs, 9 rules)
3. Savings Goal Fuzzy Advisor (3 inputs, 12 rules)
4. Anomaly Severity Scoring (3 inputs, 15 rules)
"""
from __future__ import annotations

import math
import statistics
from typing import Optional

import numpy as np
import skfuzzy as fuzz
from skfuzzy import control as ctrl

from .models import AlertItem, RiskAssessment, TrendPoint

UNIVERSE = np.arange(0, 101, 1)

RECOMMENDATION_SCALE = {12: "safe_to_invest", 35: "save_more", 60: "reduce_spending", 85: "emergency_alert"}
BUDGET_ALERT_SCALE   = {12: "safe", 35: "caution", 60: "warning", 85: "critical"}
ANOMALY_SEVERITY_SCALE = {15: "mild", 45: "moderate", 80: "severe"}

def _nearest_label(score, scale):
    return scale[min(scale, key=lambda k: abs(k - score))]

def _risk_label(score):
    if score < 33: return "low"
    if score < 66: return "medium"
    return "high"

def _mf_data(u, arr):
    return [round(float(v), 4) for v in arr]

def get_membership_function_data():
    u = UNIVERSE
    x = u.tolist()
    return {
        "universe": x,
        "main_fis": {
            "inputs": {
                "income_stability": {
                    "label": "Income Stability (%)",
                    "low":    _mf_data(u, fuzz.trapmf(u, [0,0,25,45])),
                    "medium": _mf_data(u, fuzz.trimf(u, [30,50,70])),
                    "high":   _mf_data(u, fuzz.trapmf(u, [55,75,100,100]))
                },
                "expense_level": {
                    "label": "Expense Level (% of income)",
                    "low":    _mf_data(u, fuzz.trapmf(u, [0,0,20,40])),
                    "medium": _mf_data(u, fuzz.trimf(u, [25,50,75])),
                    "high":   _mf_data(u, fuzz.trapmf(u, [60,80,100,100]))
                },
                "savings_rate": {
                    "label": "Savings Rate (%)",
                    "low":    _mf_data(u, fuzz.trapmf(u, [0,0,15,30])),
                    "medium": _mf_data(u, fuzz.trimf(u, [20,40,60])),
                    "high":   _mf_data(u, fuzz.trapmf(u, [50,70,100,100]))
                },
                "debt_pressure": {
                    "label": "Debt Pressure (%)",
                    "low":    _mf_data(u, fuzz.trapmf(u, [0,0,20,35])),
                    "medium": _mf_data(u, fuzz.trimf(u, [25,50,75])),
                    "high":   _mf_data(u, fuzz.trapmf(u, [60,75,100,100]))
                }
            },
            "outputs": {
                "financial_risk": {
                    "label": "Financial Risk Score",
                    "low":    _mf_data(u, fuzz.trapmf(u, [0,0,25,40])),
                    "medium": _mf_data(u, fuzz.trimf(u, [25,50,75])),
                    "high":   _mf_data(u, fuzz.trapmf(u, [60,75,100,100]))
                },
                "recommendation": {
                    "label": "Recommendation Score",
                    "safe_to_invest":  _mf_data(u, fuzz.trapmf(u, [0,0,10,25])),
                    "save_more":       _mf_data(u, fuzz.trimf(u, [20,35,50])),
                    "reduce_spending": _mf_data(u, fuzz.trimf(u, [45,60,75])),
                    "emergency_alert": _mf_data(u, fuzz.trapmf(u, [70,85,100,100]))
                }
            }
        },
        "budget_fis": {
            "inputs": {
                "budget_utilization": {
                    "label": "Budget Utilization (%)",
                    "low":    _mf_data(u, fuzz.trapmf(u, [0,0,20,40])),
                    "medium": _mf_data(u, fuzz.trimf(u, [30,55,75])),
                    "high":   _mf_data(u, fuzz.trapmf(u, [65,80,100,100]))
                },
                "days_remaining": {
                    "label": "Days Remaining (normalized)",
                    "few":  _mf_data(u, fuzz.trapmf(u, [0,0,15,30])),
                    "some": _mf_data(u, fuzz.trimf(u, [20,45,65])),
                    "many": _mf_data(u, fuzz.trapmf(u, [55,70,100,100]))
                }
            },
            "outputs": {
                "alert_level": {
                    "label": "Budget Alert Level",
                    "safe":     _mf_data(u, fuzz.trapmf(u, [0,0,15,30])),
                    "caution":  _mf_data(u, fuzz.trimf(u, [20,35,50])),
                    "warning":  _mf_data(u, fuzz.trimf(u, [45,60,75])),
                    "critical": _mf_data(u, fuzz.trapmf(u, [70,85,100,100]))
                }
            }
        },
        "savings_fis": {
            "inputs": {
                "savings_rate": {
                    "label": "Current Savings Rate (%)",
                    "low":    _mf_data(u, fuzz.trapmf(u, [0,0,15,30])),
                    "medium": _mf_data(u, fuzz.trimf(u, [20,40,60])),
                    "high":   _mf_data(u, fuzz.trapmf(u, [50,70,100,100]))
                },
                "expense_volatility": {
                    "label": "Expense Volatility",
                    "stable":   _mf_data(u, fuzz.trapmf(u, [0,0,20,35])),
                    "moderate": _mf_data(u, fuzz.trimf(u, [25,50,75])),
                    "volatile": _mf_data(u, fuzz.trapmf(u, [60,75,100,100]))
                },
                "income_stability": {
                    "label": "Income Stability (%)",
                    "low":    _mf_data(u, fuzz.trapmf(u, [0,0,25,45])),
                    "medium": _mf_data(u, fuzz.trimf(u, [30,50,70])),
                    "high":   _mf_data(u, fuzz.trapmf(u, [55,75,100,100]))
                }
            },
            "outputs": {
                "savings_target": {
                    "label": "Savings Target Score",
                    "conservative": _mf_data(u, fuzz.trapmf(u, [0,0,15,30])),
                    "moderate":     _mf_data(u, fuzz.trimf(u, [20,40,60])),
                    "aggressive":   _mf_data(u, fuzz.trimf(u, [50,70,85])),
                    "maximum":      _mf_data(u, fuzz.trapmf(u, [75,90,100,100]))
                }
            }
        },
        "anomaly_fis": {
            "inputs": {
                "amount_deviation": {
                    "label": "Amount Deviation from Baseline (%)",
                    "small":  _mf_data(u, fuzz.trapmf(u, [0,0,20,40])),
                    "medium": _mf_data(u, fuzz.trimf(u, [30,55,75])),
                    "large":  _mf_data(u, fuzz.trapmf(u, [65,80,100,100]))
                },
                "category_risk": {
                    "label": "Category Risk Weight",
                    "safe":     _mf_data(u, fuzz.trapmf(u, [0,0,20,40])),
                    "moderate": _mf_data(u, fuzz.trimf(u, [30,50,70])),
                    "risky":    _mf_data(u, fuzz.trapmf(u, [60,80,100,100]))
                },
                "recurrence_rarity": {
                    "label": "Recurrence Rarity",
                    "common":   _mf_data(u, fuzz.trapmf(u, [0,0,20,40])),
                    "uncommon": _mf_data(u, fuzz.trimf(u, [30,50,70])),
                    "rare":     _mf_data(u, fuzz.trapmf(u, [60,80,100,100]))
                }
            },
            "outputs": {
                "anomaly_severity": {
                    "label": "Anomaly Severity",
                    "mild":     _mf_data(u, fuzz.trapmf(u, [0,0,15,35])),
                    "moderate": _mf_data(u, fuzz.trimf(u, [25,45,65])),
                    "severe":   _mf_data(u, fuzz.trapmf(u, [55,75,100,100]))
                }
            }
        }
    }


def _safe_run(sim, inputs, output_key):
    try:
        for k, v in inputs.items():
            sim.input[k] = max(0.0, min(100.0, float(v)))
        sim.compute()
        return float(sim.output[output_key])
    except Exception:
        return 50.0


def _build_main_fis():
    u = UNIVERSE
    income  = ctrl.Antecedent(u, "income_stability")
    expense = ctrl.Antecedent(u, "expense_level")
    savings = ctrl.Antecedent(u, "savings_rate")
    debt    = ctrl.Antecedent(u, "debt_pressure")
    risk    = ctrl.Consequent(u, "financial_risk")
    rec     = ctrl.Consequent(u, "recommendation")

    income["low"]    = fuzz.trapmf(u, [0,0,25,45]);  income["medium"] = fuzz.trimf(u, [30,50,70]);  income["high"]   = fuzz.trapmf(u, [55,75,100,100])
    expense["low"]   = fuzz.trapmf(u, [0,0,20,40]);  expense["medium"]= fuzz.trimf(u, [25,50,75]);  expense["high"]  = fuzz.trapmf(u, [60,80,100,100])
    savings["low"]   = fuzz.trapmf(u, [0,0,15,30]);  savings["medium"]= fuzz.trimf(u, [20,40,60]);  savings["high"]  = fuzz.trapmf(u, [50,70,100,100])
    debt["low"]      = fuzz.trapmf(u, [0,0,20,35]);  debt["medium"]   = fuzz.trimf(u, [25,50,75]);  debt["high"]     = fuzz.trapmf(u, [60,75,100,100])
    risk["low"]      = fuzz.trapmf(u, [0,0,25,40]);  risk["medium"]   = fuzz.trimf(u, [25,50,75]);  risk["high"]     = fuzz.trapmf(u, [60,75,100,100])
    rec["safe_to_invest"] = fuzz.trapmf(u, [0,0,10,25]); rec["save_more"] = fuzz.trimf(u, [20,35,50]); rec["reduce_spending"] = fuzz.trimf(u, [45,60,75]); rec["emergency_alert"] = fuzz.trapmf(u, [70,85,100,100])

    rules = [
        ctrl.Rule(income["high"] & expense["low"] & savings["high"] & debt["low"],   (risk["low"],    rec["safe_to_invest"])),
        ctrl.Rule(income["high"] & expense["low"] & savings["medium"] & debt["low"], (risk["low"],    rec["save_more"])),
        ctrl.Rule(income["high"] & expense["medium"] & savings["medium"] & debt["low"], (risk["medium"], rec["save_more"])),
        ctrl.Rule(income["medium"] & expense["low"] & savings["medium"] & debt["low"], (risk["low"],   rec["save_more"])),
        ctrl.Rule(income["medium"] & expense["medium"] & savings["medium"] & debt["medium"], (risk["medium"], rec["save_more"])),
        ctrl.Rule(income["medium"] & expense["high"] & savings["low"],               (risk["high"],   rec["reduce_spending"])),
        ctrl.Rule(debt["high"] & income["low"],                                       (risk["high"],   rec["emergency_alert"])),
        ctrl.Rule(debt["high"] & expense["high"],                                     (risk["high"],   rec["reduce_spending"])),
        ctrl.Rule(savings["low"] & expense["high"],                                   (risk["high"],   rec["reduce_spending"])),
        ctrl.Rule(income["low"] & debt["medium"],                                     (risk["high"],   rec["emergency_alert"])),
        ctrl.Rule(income["high"] & expense["high"] & savings["medium"] & debt["low"],(risk["medium"], rec["reduce_spending"])),
        ctrl.Rule(savings["low"] & expense["medium"],                                 (risk["medium"], rec["reduce_spending"])),
        ctrl.Rule(income["high"] & debt["low"] & savings["low"],                     (risk["low"],    rec["save_more"])),
        ctrl.Rule(income["low"] & expense["medium"] & debt["medium"],                (risk["high"],   rec["reduce_spending"])),
        ctrl.Rule(income["medium"] & expense["medium"] & savings["low"] & debt["medium"], (risk["medium"], rec["reduce_spending"])),
        ctrl.Rule(income["low"] & expense["low"] & debt["low"],                      (risk["medium"], rec["save_more"])),
        ctrl.Rule(savings["high"] & debt["low"],                                      (risk["low"],    rec["safe_to_invest"])),
        ctrl.Rule(income["low"] & expense["high"] & savings["low"] & debt["high"],   (risk["high"],   rec["emergency_alert"])),
    ]
    return ctrl.ControlSystemSimulation(ctrl.ControlSystem(rules))


def _build_budget_fis():
    u = UNIVERSE
    util    = ctrl.Antecedent(u, "budget_utilization")
    days    = ctrl.Antecedent(u, "days_remaining")
    alert   = ctrl.Consequent(u, "alert_level")

    util["low"]    = fuzz.trapmf(u, [0,0,20,40]); util["medium"] = fuzz.trimf(u, [30,55,75]); util["high"]   = fuzz.trapmf(u, [65,80,100,100])
    days["few"]    = fuzz.trapmf(u, [0,0,15,30]); days["some"]   = fuzz.trimf(u, [20,45,65]); days["many"]   = fuzz.trapmf(u, [55,70,100,100])
    alert["safe"]  = fuzz.trapmf(u, [0,0,15,30]); alert["caution"] = fuzz.trimf(u, [20,35,50]); alert["warning"] = fuzz.trimf(u, [45,60,75]); alert["critical"] = fuzz.trapmf(u, [70,85,100,100])

    rules = [
        ctrl.Rule(util["low"] & days["many"],    alert["safe"]),
        ctrl.Rule(util["low"] & days["some"],    alert["safe"]),
        ctrl.Rule(util["low"] & days["few"],     alert["caution"]),
        ctrl.Rule(util["medium"] & days["many"], alert["safe"]),
        ctrl.Rule(util["medium"] & days["some"], alert["caution"]),
        ctrl.Rule(util["medium"] & days["few"],  alert["warning"]),
        ctrl.Rule(util["high"] & days["many"],   alert["caution"]),
        ctrl.Rule(util["high"] & days["some"],   alert["warning"]),
        ctrl.Rule(util["high"] & days["few"],    alert["critical"]),
    ]
    return ctrl.ControlSystemSimulation(ctrl.ControlSystem(rules))


def _build_savings_fis():
    u = UNIVERSE
    sav  = ctrl.Antecedent(u, "savings_rate")
    vol  = ctrl.Antecedent(u, "expense_volatility")
    stab = ctrl.Antecedent(u, "income_stability")
    tgt  = ctrl.Consequent(u, "savings_target")

    sav["low"]     = fuzz.trapmf(u, [0,0,15,30]);  sav["medium"]   = fuzz.trimf(u, [20,40,60]);  sav["high"]     = fuzz.trapmf(u, [50,70,100,100])
    vol["stable"]  = fuzz.trapmf(u, [0,0,20,35]);  vol["moderate"] = fuzz.trimf(u, [25,50,75]);  vol["volatile"] = fuzz.trapmf(u, [60,75,100,100])
    stab["low"]    = fuzz.trapmf(u, [0,0,25,45]);  stab["medium"]  = fuzz.trimf(u, [30,50,70]);  stab["high"]    = fuzz.trapmf(u, [55,75,100,100])
    tgt["conservative"] = fuzz.trapmf(u, [0,0,15,30]); tgt["moderate"] = fuzz.trimf(u, [20,40,60]); tgt["aggressive"] = fuzz.trimf(u, [50,70,85]); tgt["maximum"] = fuzz.trapmf(u, [75,90,100,100])

    rules = [
        ctrl.Rule(sav["high"] & stab["high"] & vol["stable"],    tgt["maximum"]),
        ctrl.Rule(sav["high"] & stab["high"] & vol["moderate"],   tgt["aggressive"]),
        ctrl.Rule(sav["medium"] & stab["high"] & vol["stable"],   tgt["aggressive"]),
        ctrl.Rule(sav["medium"] & stab["medium"] & vol["stable"], tgt["moderate"]),
        ctrl.Rule(sav["medium"] & stab["medium"] & vol["moderate"], tgt["moderate"]),
        ctrl.Rule(sav["low"] & stab["high"] & vol["stable"],      tgt["moderate"]),
        ctrl.Rule(sav["low"] & stab["medium"] & vol["stable"],    tgt["moderate"]),
        ctrl.Rule(vol["volatile"] & stab["low"],                   tgt["conservative"]),
        ctrl.Rule(vol["volatile"] & stab["medium"],                tgt["conservative"]),
        ctrl.Rule(vol["volatile"] & stab["high"],                  tgt["moderate"]),
        ctrl.Rule(stab["low"] & sav["low"],                        tgt["conservative"]),
        ctrl.Rule(sav["high"] & stab["low"],                       tgt["moderate"]),
    ]
    return ctrl.ControlSystemSimulation(ctrl.ControlSystem(rules))


def _build_anomaly_fis():
    u = UNIVERSE
    dev  = ctrl.Antecedent(u, "amount_deviation")
    cat  = ctrl.Antecedent(u, "category_risk")
    rar  = ctrl.Antecedent(u, "recurrence_rarity")
    sev  = ctrl.Consequent(u, "anomaly_severity")

    dev["small"]  = fuzz.trapmf(u, [0,0,20,40]); dev["medium"] = fuzz.trimf(u, [30,55,75]); dev["large"]  = fuzz.trapmf(u, [65,80,100,100])
    cat["safe"]   = fuzz.trapmf(u, [0,0,20,40]); cat["moderate"] = fuzz.trimf(u, [30,50,70]); cat["risky"]  = fuzz.trapmf(u, [60,80,100,100])
    rar["common"] = fuzz.trapmf(u, [0,0,20,40]); rar["uncommon"] = fuzz.trimf(u, [30,50,70]); rar["rare"]   = fuzz.trapmf(u, [60,80,100,100])
    sev["mild"]   = fuzz.trapmf(u, [0,0,15,35]); sev["moderate"] = fuzz.trimf(u, [25,45,65]); sev["severe"] = fuzz.trapmf(u, [55,75,100,100])

    rules = [
        ctrl.Rule(dev["small"] & cat["safe"],                                   sev["mild"]),
        ctrl.Rule(dev["small"] & cat["moderate"],                               sev["mild"]),
        ctrl.Rule(dev["small"] & cat["risky"],                                  sev["moderate"]),
        ctrl.Rule(dev["medium"] & cat["safe"] & rar["common"],                  sev["mild"]),
        ctrl.Rule(dev["medium"] & cat["safe"] & rar["uncommon"],                sev["moderate"]),
        ctrl.Rule(dev["medium"] & cat["moderate"] & rar["rare"],                sev["moderate"]),
        ctrl.Rule(dev["medium"] & cat["risky"] & rar["uncommon"],               sev["moderate"]),
        ctrl.Rule(dev["medium"] & cat["risky"] & rar["rare"],                   sev["severe"]),
        ctrl.Rule(dev["large"] & cat["safe"] & rar["common"],                   sev["moderate"]),
        ctrl.Rule(dev["large"] & cat["safe"] & rar["rare"],                     sev["moderate"]),
        ctrl.Rule(dev["large"] & cat["moderate"] & rar["uncommon"],             sev["moderate"]),
        ctrl.Rule(dev["large"] & cat["moderate"] & rar["rare"],                 sev["severe"]),
        ctrl.Rule(dev["large"] & cat["risky"] & rar["common"],                  sev["moderate"]),
        ctrl.Rule(dev["large"] & cat["risky"] & rar["uncommon"],                sev["severe"]),
        ctrl.Rule(dev["large"] & cat["risky"] & rar["rare"],                    sev["severe"]),
    ]
    return ctrl.ControlSystemSimulation(ctrl.ControlSystem(rules))


CATEGORY_RISK_WEIGHTS = {"bills":30,"food":35,"transport":30,"health":40,"shopping":55,"entertainment":60,"personal":50,"others":65,"refund":10}

def _category_risk_score(category):
    if not category: return 50.0
    return float(CATEGORY_RISK_WEIGHTS.get(category.lower(), 55))


def assess_financial_risk(daily_trends, income_stability, savings_rate, debt_pressure):
    computed_income_stability = income_stability if income_stability is not None else _derive_income_stability(daily_trends)
    computed_expense_level    = _derive_expense_level(daily_trends)
    computed_savings_rate     = savings_rate if savings_rate is not None else _derive_savings_rate(daily_trends)
    computed_debt_pressure    = debt_pressure if debt_pressure is not None else _derive_debt_pressure(daily_trends)

    inputs = {"income_stability": computed_income_stability, "expense_level": computed_expense_level, "savings_rate": max(0.0, computed_savings_rate), "debt_pressure": computed_debt_pressure}
    risk_score = _safe_run(_build_main_fis(), inputs, "financial_risk")
    rec_score  = _safe_run(_build_main_fis(), inputs, "recommendation")

    risk_label     = _risk_label(risk_score)
    recommendation = _nearest_label(rec_score, RECOMMENDATION_SCALE)

    insights = [
        f"Mamdani FIS (18 rules, 4 inputs) classifies profile as '{risk_label}' — risk score {risk_score:.1f}/100.",
        f"Centroid defuzzification recommends: '{recommendation}'.",
        f"Income stability: {computed_income_stability:.1f}/100 | Expense level: {computed_expense_level:.1f}/100.",
        f"Savings rate: {computed_savings_rate:.1f}% | Debt pressure: {computed_debt_pressure:.1f}/100.",
    ]
    alerts = []
    if risk_label == "high":
        alerts.append(AlertItem(level="high", title="High Financial Risk Detected", detail=f"Fuzzy risk_score={risk_score:.0f}/100. Action: {recommendation}."))
    elif risk_label == "medium":
        alerts.append(AlertItem(level="medium", title="Moderate Financial Risk", detail=f"Risk score {risk_score:.0f}/100. Consider: {recommendation}."))

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


def assess_budget_alert(budget_utilization_pct, days_remaining):
    days_mapped  = min(100.0, (days_remaining / 31.0) * 100.0)
    alert_score  = _safe_run(_build_budget_fis(), {"budget_utilization": budget_utilization_pct, "days_remaining": days_mapped}, "alert_level")
    label = _nearest_label(alert_score, BUDGET_ALERT_SCALE)
    explanations = {
        "safe":     f"Budget usage {budget_utilization_pct:.0f}% with {days_remaining:.0f} days left. On track.",
        "caution":  f"Budget usage {budget_utilization_pct:.0f}% with {days_remaining:.0f} days left. Monitor spending.",
        "warning":  f"Budget usage {budget_utilization_pct:.0f}% with {days_remaining:.0f} days remaining. Reduce discretionary spend.",
        "critical": f"Budget usage {budget_utilization_pct:.0f}% with {days_remaining:.0f} days left. Immediate action needed.",
    }
    return {"alert_level": label, "alert_score": round(alert_score, 2), "budget_utilization": round(budget_utilization_pct, 2), "days_remaining": int(days_remaining), "explanation": explanations[label]}


def advise_savings_goal(savings_rate, expense_volatility_pct, income_stability, current_monthly_income):
    target_score = _safe_run(_build_savings_fis(), {"savings_rate": max(0.0, savings_rate), "expense_volatility": expense_volatility_pct, "income_stability": income_stability}, "savings_target")
    label_map = {12: "conservative", 38: "moderate", 68: "aggressive", 90: "maximum"}
    label = _nearest_label(target_score, label_map)
    pct_map = {"conservative": (10, 20), "moderate": (20, 35), "aggressive": (35, 50), "maximum": (50, 65)}
    lo, hi = pct_map[label]
    advice_map = {
        "conservative": "Cautious profile. Build an emergency fund first — target 10–20% savings.",
        "moderate":     "Stable profile. 20–35% savings is achievable and strongly advised.",
        "aggressive":   "Strong income stability. Push for 35–50% savings rate.",
        "maximum":      "Excellent profile. Maximize at 50–65% and invest the surplus.",
    }
    return {
        "target_label": label, "target_score": round(target_score, 2),
        "target_pct_low": lo, "target_pct_high": hi,
        "target_amt_low": round(current_monthly_income * lo / 100, 2), "target_amt_high": round(current_monthly_income * hi / 100, 2),
        "advice": advice_map[label], "current_savings_rate": round(savings_rate, 2),
    }


def score_anomaly_severity(amount, category, mean_amount, std_amount, category_frequency, total_transactions):
    z_score = abs((amount - mean_amount) / std_amount) if std_amount > 0 else (5.0 if amount > mean_amount * 2 else 0.0)
    amount_deviation = min(100.0, (z_score / 5.0) * 100.0)
    cat_risk = _category_risk_score(category)
    freq_ratio = category_frequency / total_transactions if total_transactions > 0 else 0.5
    rarity = min(100.0, (1.0 - freq_ratio) * 100.0)
    severity_score = _safe_run(_build_anomaly_fis(), {"amount_deviation": amount_deviation, "category_risk": cat_risk, "recurrence_rarity": rarity}, "anomaly_severity")
    label = _nearest_label(severity_score, ANOMALY_SEVERITY_SCALE)
    expl = {"mild": f"₹{amount:,.0f} is {z_score:.1f}σ from baseline. Minor — monitor only.", "moderate": f"₹{amount:,.0f} is {z_score:.1f}σ from baseline. Review recommended.", "severe": f"₹{amount:,.0f} is {z_score:.1f}σ — rare, high-risk category. Immediate review."}
    return {"severity_label": label, "severity_score": round(severity_score, 2), "amount_deviation_score": round(amount_deviation, 2), "category_risk_score": round(cat_risk, 2), "rarity_score": round(rarity, 2), "z_score": round(z_score, 2), "explanation": expl[label]}


def _derive_income_stability(daily_trends):
    incomes = [p.income for p in daily_trends if p.income > 0]
    if len(incomes) < 2: return 50.0
    avg = float(np.mean(incomes)); std = float(np.std(incomes))
    cv = std / avg if avg else 1.0
    return max(0.0, min(100.0, 100 - (cv * 100)))

def _derive_expense_level(daily_trends):
    income = sum(p.income for p in daily_trends); expense = sum(p.expense for p in daily_trends)
    if income <= 0: return 75.0 if expense > 0 else 0.0
    return max(0.0, min(100.0, (expense / income) * 100))

def _derive_savings_rate(daily_trends):
    income = sum(p.income for p in daily_trends); expense = sum(p.expense for p in daily_trends)
    if income <= 0: return 0.0
    return ((income - expense) / income) * 100

def _derive_debt_pressure(daily_trends):
    neg = sum(1 for p in daily_trends if p.net < 0)
    return (neg / (len(daily_trends) or 1)) * 100

def _derive_expense_volatility(daily_trends):
    expenses = [p.expense for p in daily_trends if p.expense > 0]
    if len(expenses) < 2: return 30.0
    try:
        return max(0.0, min(100.0, statistics.stdev(expenses) / statistics.mean(expenses) * 100))
    except Exception: return 30.0

import statistics
