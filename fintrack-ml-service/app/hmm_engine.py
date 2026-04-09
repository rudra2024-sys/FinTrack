from __future__ import annotations

from collections import Counter
from typing import List, Dict

import numpy as np
from hmmlearn.hmm import GaussianHMM

from .models import AlertItem, HiddenStatePoint, TrendPoint


def analyze_hidden_states(daily_trends: List[TrendPoint]) -> tuple[list[HiddenStatePoint], list[str], list[AlertItem]]:
    """
    Analyze spending patterns using Hidden Markov Model to identify hidden financial states.
    Returns timeline with states, insights, and alerts.
    """
    if not daily_trends:
        return [], ["No expenses available yet to train the Hidden Markov Model."], []

    if len(daily_trends) < 3:
        return _fallback_analysis(daily_trends)

    # Extract features for HMM
    expenses = np.array([point.expense for point in daily_trends], dtype=float)
    
    # Feature 1: Daily expenses
    # Feature 2: Day-over-day change
    deltas = np.diff(np.concatenate(([expenses[0]], expenses)))
    
    # Feature 3: Rolling average (3-day window)
    rolling = np.array([
        float(np.mean(expenses[max(0, index - 2): index + 1]))
        for index in range(len(expenses))
    ])
    
    # Feature 4: Frequency of transactions (volatility indicator)    
    volatility = np.array([
        float(np.std(expenses[max(0, index - 2): index + 1])) if index > 0 else expenses[0]
        for index in range(len(expenses))
    ])
    
    features = np.column_stack([expenses, deltas, rolling, volatility])
    normalized = _normalize_features(features)

    # Train HMM if we have enough data
    if len(daily_trends) >= 4 and np.any(expenses > 0):
        try:
            model = GaussianHMM(n_components=4, covariance_type="diag", n_iter=500, random_state=42)
            model.fit(normalized)
            raw_states = model.predict(normalized)
            state_map = _map_state_names(raw_states, expenses, deltas, volatility)
        except Exception as e:
            raw_states = np.zeros(len(daily_trends), dtype=int)
            state_map = {0: "stable_spending"}
    else:
        raw_states = np.zeros(len(daily_trends), dtype=int)
        state_map = {0: "stable_spending"}

    # Compute anomaly scores using Z-score
    anomaly_scores = _compute_anomaly_scores(expenses)
    
    # Build timeline with state and anomaly information
    timeline = [
        HiddenStatePoint(
            date=point.period,
            expense=round(point.expense, 2),
            hidden_state=state_map.get(int(raw_state), "stable_spending"),
            anomaly=anomaly_scores[index] >= 2.0 or state_map.get(int(raw_state)) in ["risky_phase", "very_high_spending"],
            anomaly_score=round(float(anomaly_scores[index]), 3),
        )
        for index, (point, raw_state) in enumerate(zip(daily_trends, raw_states))
    ]

    # Generate insights
    insights = _generate_insights(timeline, expenses)
    
    # Generate alerts for critical situations
    alerts = _generate_alerts(timeline, expenses)

    return timeline, insights, alerts


def _fallback_analysis(daily_trends: List[TrendPoint]) -> tuple[list[HiddenStatePoint], list[str], list[AlertItem]]:
    """Fallback analysis when we don't have enough data for HMM."""
    expenses = np.array([point.expense for point in daily_trends], dtype=float)
    anomaly_scores = _compute_anomaly_scores(expenses)
    
    timeline = [
        HiddenStatePoint(
            date=point.period,
            expense=round(point.expense, 2),
            hidden_state="insufficient_data",
            anomaly=anomaly_scores[index] >= 1.5,
            anomaly_score=round(float(anomaly_scores[index]), 3),
        )
        for index, point in enumerate(daily_trends)
    ]
    
    insights = ["Limited data available. Build more transaction history for better pattern analysis."]
    alerts = []
    return timeline, insights, alerts


def _generate_insights(timeline: List[HiddenStatePoint], expenses: np.ndarray) -> List[str]:
    """Generate actionable insights from the HMM analysis."""
    insights = []
    
    # Most common state
    if timeline:
        state_counts = Counter(item.hidden_state for item in timeline)
        dominant = state_counts.most_common(1)[0][0]
        fraction = sum(1 for item in timeline if item.hidden_state == dominant) / len(timeline)
        insights.append(
            f"Your primary spending pattern is '{dominant}' ({fraction*100:.0f}% of the time)."
        )
    
    # Anomaly detection
    anomalies = sum(1 for item in timeline if item.anomaly)
    if anomalies > 0:
        insights.append(f"Detected {anomalies} days with unusual spending patterns.")
    
    # Spending trend
    if len(expenses) >= 2:
        recent_avg = float(np.mean(expenses[-7:] if len(expenses) >= 7 else expenses[-3:]))
        older_avg = float(np.mean(expenses[:7] if len(expenses) >= 7 else expenses[:3]))
        trend_pct = ((recent_avg - older_avg) / max(older_avg, 1)) * 100
        if trend_pct > 20:
            insights.append(f"Spending trend is increasing ({trend_pct:.1f}% higher recently).")
        elif trend_pct < -20:
            insights.append(f"Spending trend is decreasing ({abs(trend_pct):.1f}% lower recently).")
    
    return insights


def _generate_alerts(timeline: List[HiddenStatePoint], expenses: np.ndarray) -> List[AlertItem]:
    """Generate actionable alerts for concerning spending patterns."""
    alerts: List[AlertItem] = []
    
    if not timeline:
        return alerts
    
    # Check for persistent risky behavior
    risky_streak = _max_streak([item.hidden_state == "risky_phase" for item in timeline])
    if risky_streak >= 3:
        alerts.append(AlertItem(
            level="high",
            title="Persistent risky spending detected",
            detail=f"Risky spending behavior detected for {risky_streak} consecutive days.",
        ))
    
    # Check for very high spending
    very_high_streak = _max_streak([item.hidden_state == "very_high_spending" for item in timeline])
    if very_high_streak >= 2:
        alerts.append(AlertItem(
            level="high",
            title="Very high spending phase",
            detail=f"Exceptionally high spending detected for {very_high_streak} consecutive days.",
        ))
    
    # Check for expense spikes
    max_anomaly = max((item.anomaly_score for item in timeline), default=0)
    if max_anomaly >= 2.8:
        alerts.append(AlertItem(
            level="medium",
            title="Expense spike detected",
            detail=f"One or more daily expenses are significantly above your baseline (anomaly score: {max_anomaly:.2f}).",
        ))
    
    # Check for unusual volatility
    if len(expenses) >= 5:
        volatility = float(np.std(expenses))
        mean_expense = float(np.mean(expenses))
        if mean_expense > 0 and volatility / mean_expense > 0.5:
            alerts.append(AlertItem(
                level="medium",
                title="High spending variability",
                detail="Your daily spending varies significantly. Consider setting spending limits.",
            ))
    
    return alerts


def _normalize_features(features: np.ndarray) -> np.ndarray:
    """Normalize features to zero mean and unit variance."""
    means = features.mean(axis=0)
    stds = features.std(axis=0)
    stds[stds == 0] = 1.0
    return (features - means) / stds


def _map_state_names(
    raw_states: np.ndarray, 
    expenses: np.ndarray, 
    deltas: np.ndarray,
    volatility: np.ndarray
) -> Dict[int, str]:
    """Map HMM states to meaningful financial behavior names."""
    state_metrics = []
    
    for state in np.unique(raw_states):
        indexes = np.where(raw_states == state)[0]
        if len(indexes) > 0:
            state_metrics.append({
                "state": int(state),
                "expense_mean": float(np.mean(expenses[indexes])),
                "volatility": float(np.mean(np.abs(deltas[indexes]))),
                "volatility_std": float(np.mean(volatility[indexes])),
            })
        else:
            state_metrics.append({
                "state": int(state),
                "expense_mean": 0.0,
                "volatility": 0.0,
                "volatility_std": 0.0,
            })

    # Sort by expense level and volatility
    state_metrics.sort(key=lambda item: (item["expense_mean"], item["volatility"]))
    
    labels = {}
    ordered_states = [item["state"] for item in state_metrics]
    
    if not ordered_states:
        return {0: "stable_spending"}
    
    # Assign labels based on expense level and volatility
    for idx, state in enumerate(ordered_states):
        metric = state_metrics[idx]
        
        if metric["expense_mean"] == 0:
            labels[state] = "no_spending"
        elif metric["expense_mean"] < np.percentile([m["expense_mean"] for m in state_metrics if m["expense_mean"] > 0], 33):
            labels[state] = "low_spending"
        elif metric["expense_mean"] < np.percentile([m["expense_mean"] for m in state_metrics], 66):
            labels[state] = "normal_spending"
        elif metric["volatility"] > np.median([m["volatility"] for m in state_metrics]):
            labels[state] = "high_spending"
        else:
            labels[state] = "risky_phase"
    
    # Override for extreme cases
    max_expense_state = max(ordered_states, key=lambda s: state_metrics[ordered_states.index(s)]["expense_mean"])
    if state_metrics[ordered_states.index(max_expense_state)]["expense_mean"] > np.mean(expenses) * 1.5:
        labels[max_expense_state] = "very_high_spending"
    
    return labels


def _compute_anomaly_scores(expenses: np.ndarray) -> np.ndarray:
    """Compute Z-score based anomaly scores for expenses."""
    if len(expenses) < 2:
        return np.zeros_like(expenses)
    avg = float(np.mean(expenses))
    std = float(np.std(expenses)) or 1.0
    return np.abs(expenses - avg) / std


def _max_streak(values: List[bool]) -> int:
    """Calculate the longest consecutive streak of True values."""
    longest = 0
    current = 0
    for value in values:
        current = current + 1 if value else 0
        longest = max(longest, current)
    return longest

