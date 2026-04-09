from __future__ import annotations

from collections import defaultdict
from datetime import timedelta
from typing import Iterable, List

from .categorization import categorize_transaction
from .models import CategorySpend, TransactionRecord, TrendPoint


def enrich_transactions(transactions: Iterable[TransactionRecord]) -> List[TransactionRecord]:
    enriched: List[TransactionRecord] = []
    for transaction in sorted(transactions, key=lambda item: (item.date, item.time or "")):
        category, confidence = categorize_transaction(
            f"{transaction.merchant_person} {transaction.description or ''}"
        )
        enriched.append(
            transaction.model_copy(update={"category": category, "confidence": confidence})
        )
    return enriched


def build_daily_trends(transactions: Iterable[TransactionRecord]) -> List[TrendPoint]:
    buckets = defaultdict(lambda: {"income": 0.0, "expense": 0.0})
    ordered = sorted(transactions, key=lambda item: item.date)
    if not ordered:
        return []

    current = ordered[0].date
    end = ordered[-1].date
    while current <= end:
        buckets[current.isoformat()]
        current += timedelta(days=1)

    for transaction in ordered:
        bucket = buckets[transaction.date.isoformat()]
        if transaction.transaction_type == "credit":
            bucket["income"] += transaction.amount
        else:
            bucket["expense"] += transaction.amount

    return [
        TrendPoint(period=period, income=round(values["income"], 2), expense=round(values["expense"], 2), net=round(values["income"] - values["expense"], 2))
        for period, values in sorted(buckets.items())
    ]


def build_weekly_trends(transactions: Iterable[TransactionRecord]) -> List[TrendPoint]:
    buckets = defaultdict(lambda: {"income": 0.0, "expense": 0.0})
    for transaction in sorted(transactions, key=lambda item: item.date):
        year, week, _ = transaction.date.isocalendar()
        key = f"{year}-W{week:02d}"
        bucket = buckets[key]
        if transaction.transaction_type == "credit":
            bucket["income"] += transaction.amount
        else:
            bucket["expense"] += transaction.amount

    return [
        TrendPoint(period=period, income=round(values["income"], 2), expense=round(values["expense"], 2), net=round(values["income"] - values["expense"], 2))
        for period, values in sorted(buckets.items())
    ]


def build_category_distribution(transactions: Iterable[TransactionRecord]) -> List[CategorySpend]:
    totals = defaultdict(float)
    confidences = defaultdict(list)
    for transaction in transactions:
        if transaction.transaction_type != "debit":
            continue
        category = transaction.category or "others"
        totals[category] += transaction.amount
        confidences[category].append(transaction.confidence or 0.0)

    ranked = sorted(totals.items(), key=lambda item: item[1], reverse=True)
    return [
        CategorySpend(
            category=category,
            amount=round(amount, 2),
            confidence=round(sum(confidences[category]) / max(len(confidences[category]), 1), 3),
        )
        for category, amount in ranked
    ]
