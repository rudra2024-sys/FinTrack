from __future__ import annotations

from typing import Iterable, List, Tuple

from rapidfuzz import fuzz
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.naive_bayes import MultinomialNB
import numpy as np


# Enhanced category keywords for better classification
CATEGORY_KEYWORDS = {
    "Food": [
        "zomato", "swiggy", "uber eats", "restaurant", "cafe", "coffee", "pizza", "burger", 
        "food", "dining", "lunch", "dinner", "breakfast", "snacks", "bakery", "fast food",
        "kfc", "mcdonalds", "dominos", "dhaba", "diner", "eatery", "bistro"
    ],
    "Transport": [
        "uber", "ola", "taxi", "metro", "petrol", "fuel", "gas station", "irctc", "bus", 
        "cab", "auto", "railway", "flight", "airline", "parking", "toll", "travel", "bike",
        "train", "shuttle", "carpool", "lyft"
    ],
    "Recharge": [
        "recharge", "jio", "airtel", "vi", "vodafone", "bsnl", "broadband", "internet bill",
        "mobile recharge", "phone", "cellular", "data"
    ],
    "Shopping": [
        "amazon", "flipkart", "myntra", "ajio", "shopping", "store", "mart", "mall", "clothing",
        "apparel", "fashion", "market", "shop", "retail", "ebay", "etsy", "fashion", "shoe",
        "boots", "dress", "jeans"
    ],
    "Personal": [
        "friend", "family", "transfer", "upi", "payment", "loan", "debt", "personal",
        "friend transfer", "family transfer", "wallet", "cashback"
    ],
    "Medical": [
        "medical", "pharmacy", "hospital", "doctor", "clinic", "health", "medicine", "pills",
        "healthcare", "dental", "laboratory", "lab test", "diagnose", "virus", "disease"
    ],
    "Entertainment": [
        "netflix", "spotify", "prime", "disney", "movie", "cinema", "theater", "gaming",
        "steam", "playstore", "appstore", "music", "song", "entertainment", "game"
    ],
    "Rent": [
        "rent", "landlord", "lease", "accommodation", "housing", "apartment", "flat",
        "house", "property", "mortgage"
    ],
    "Utilities": [
        "electricity", "water bill", "internet", "gas bill", "power", "broadband",
        "utility", "bill", "service charge", "phone bill", "electricity board"
    ],
    "Salary": [
        "salary", "payroll", "bonus", "gratuity", "wages", "income", "earnings",
        "stipend", "honorarium", "payment from employer"
    ],
    "Others": [
        "misc", "unknown", "other", "transaction", "transfer", "payment"
    ]
}

# Mapping for consistent naming
CATEGORY_NORMALIZE = {
    "food": "Food",
    "transport": "Transport",
    "recharge": "Recharge",
    "shopping": "Shopping",
    "personal": "Personal",
    "medical": "Medical",
    "entertainment": "Entertainment",
    "rent": "Rent",
    "utilities": "Utilities",
    "salary": "Salary",
    "others": "Others",
}

# Enhanced expense rules
EXPENSE_RULES = {
    "zomato": "Food",
    "swiggy": "Food",
    "uber eats": "Food",
    "restaurant": "Food",
    "cafe": "Food",
    "pizza": "Food",
    "burger": "Food",
    "uber": "Transport",
    "ola": "Transport",
    "taxi": "Transport",
    "metro": "Transport",
    "petrol": "Transport",
    "fuel": "Transport",
    "irctc": "Transport",
    "railway": "Transport",
    "flight": "Transport",
    "airline": "Transport",
    "parking": "Transport",
    "toll": "Transport",
    "recharge": "Recharge",
    "jio": "Recharge",
    "airtel": "Recharge",
    "vodafone": "Recharge",
    "bsnl": "Recharge",
    "internet": "Utilities",
    "broadband": "Utilities",
    "amazon": "Shopping",
    "flipkart": "Shopping",
    "myntra": "Shopping",
    "ajio": "Shopping",
    "mall": "Shopping",
    "store": "Shopping",
    "market": "Shopping",
    "rent": "Rent",
    "landlord": "Rent",
    "lease": "Rent",
    "apartment": "Rent",
    "electricity": "Utilities",
    "water": "Utilities",
    "gas": "Utilities",
    "netflix": "Entertainment",
    "spotify": "Entertainment",
    "prime video": "Entertainment",
    "disney": "Entertainment",
    "movie": "Entertainment",
    "cinema": "Entertainment",
    "gaming": "Entertainment",
    "hospital": "Medical",
    "pharmacy": "Medical",
    "doctor": "Medical",
    "clinic": "Medical",
    "medical": "Medical",
    "health": "Medical",
}

# Enhanced income rules
INCOME_RULES = {
    "salary": "Salary",
    "payroll": "Salary",
    "bonus": "Salary",
    "wages": "Salary",
    "earnings": "Salary",
    "income": "Salary",
    "stipend": "Salary",
    "freelance": "Freelance",
    "consulting": "Freelance",
    "contract": "Freelance",
    "dividend": "Investments",
    "interest": "Investments",
    "refund": "Refund",
    "cashback": "Cashback",
    "rebate": "Cashback",
}


def categorize_one(description: str, txn_type: str) -> str:
    """
    Categorize a transaction using rule-based approach.
    """
    normalized = (description or "").lower()
    rules = EXPENSE_RULES if txn_type.upper() in ["EXPENSE", "DEBIT"] else INCOME_RULES
    
    # Check for exact keyword matches first
    for keyword, category in rules.items():
        if keyword in normalized:
            return category
    
    # Fallback
    return "Others" if txn_type.upper() in ["EXPENSE", "DEBIT"] else "Salary"


def categorize_transaction(label: str) -> Tuple[str, float]:
    """
    Categorize a transaction using fuzzy matching with confidence score.
    """
    normalized = (label or "").strip().lower()
    if not normalized:
        return "Others", 0.0

    best_category = "Others"
    best_score = 0.0

    for category, keywords in CATEGORY_KEYWORDS.items():
        score = _match_keywords(normalized, keywords)
        if score > best_score:
            best_score = score
            best_category = category

    # Normalize the category name
    best_category = CATEGORY_NORMALIZE.get(best_category.lower(), best_category)
    
    return best_category, round(best_score / 100.0, 3)


def categorize_transactions_batch(descriptions: List[str], txn_types: List[str]) -> List[Tuple[str, float]]:
    """
    Categorize multiple transactions efficiently.
    """
    results = []
    for desc, txn_type in zip(descriptions, txn_types):
        # Use rule-based categorization for speed
        category = categorize_one(desc, txn_type)
        # Calculate a confidence score based on keyword matching
        normalized = desc.lower() if desc else ""
        category_keywords = CATEGORY_KEYWORDS.get(category, [])
        score = _match_keywords(normalized, category_keywords) / 100.0
        results.append((category, round(score, 3)))
    return results


def _match_keywords(label: str, keywords: Iterable[str]) -> float:
    """
    Match label against a list of keywords using fuzzy matching.
    Returns a score 0-100.
    """
    best = 0.0
    for keyword in keywords:
        # Use partial ratio for substring matches
        partial = float(fuzz.partial_ratio(label, keyword))
        # Use token sort ratio for word-order-independent matches
        token = float(fuzz.token_sort_ratio(label, keyword))
        best = max(best, partial, token)
    return best
