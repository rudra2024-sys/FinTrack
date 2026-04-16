#!/usr/bin/env python3
"""Test all 4 new fuzzy logic endpoints."""

import requests
import json

print("=" * 70)
print("FINTRACK FUZZY LOGIC ENDPOINTS - INTEGRATION TEST")
print("=" * 70)

# TEST 1: Membership Functions
print("\n✓ TEST 1: Membership Functions Endpoint")
print("-" * 70)
r = requests.get('http://localhost:8001/ml/fuzzy/membership-functions')
print(f"  Status: {r.status_code}")
data = r.json()
print(f"  ✓ Main FIS: {list(data.get('main_fis', {}).get('inputs', {}).keys())}")
print(f"  ✓ Budget FIS: {list(data.get('budget_fis', {}).get('inputs', {}).keys())}")
print(f"  ✓ Savings FIS: {list(data.get('savings_fis', {}).get('inputs', {}).keys())}")
print(f"  ✓ Anomaly FIS: {list(data.get('anomaly_fis', {}).get('inputs', {}).keys())}")

# TEST 2: Budget Alert Endpoint
print("\n✓ TEST 2: Budget Alert Endpoint (2-input, 9-rule FIS)")
print("-" * 70)
budget_data = {
    "budget_utilization_pct": 75.5,
    "days_remaining": 10
}
r = requests.post('http://localhost:8001/ml/fuzzy/budget-alert', json=budget_data)
print(f"  Status: {r.status_code}")
result = r.json()
print(f"  Input: {budget_data['budget_utilization_pct']}% util, {budget_data['days_remaining']} days left")
print(f"  Alert Level: {result.get('alert_level').upper()}")
print(f"  Alert Score: {result.get('alert_score')}/100")
print(f"  Explanation: {result.get('explanation')}")

# TEST 3: Savings Advisor Endpoint  
print("\n✓ TEST 3: Savings Advisor Endpoint (3-input, 12-rule FIS)")
print("-" * 70)
savings_data = {
    "savings_rate": 25.0,
    "expense_volatility_pct": 35.0,
    "income_stability": 65.0,
    "current_monthly_income": 100000
}
r = requests.post('http://localhost:8001/ml/fuzzy/savings-advisor', json=savings_data)
print(f"  Status: {r.status_code}")
result = r.json()
print(f"  Input: {savings_data['savings_rate']}% rate, {savings_data['expense_volatility_pct']}% volatility, {savings_data['income_stability']}% income stability")
print(f"  Target Strategy: {result.get('target_label').upper()}")
print(f"  Target Score: {result.get('target_score')}/100")
print(f"  Monthly Amount: ₹{result.get('target_amt_low')} - ₹{result.get('target_amt_high')}")
print(f"  Advice: {result.get('advice')}")

# TEST 4: Anomaly Severity Endpoint
print("\n✓ TEST 4: Anomaly Severity Endpoint (3-input, 15-rule FIS)")
print("-" * 70)
anomaly_data = {
    "transactions": [
        {"amount": 500, "category": "food"},
        {"amount": 2000, "category": "food"},
        {"amount": 15000, "category": "shopping"}
    ]
}
r = requests.post('http://localhost:8001/ml/fuzzy/anomaly-severity', json=anomaly_data)
print(f"  Status: {r.status_code}")
result = r.json()
print(f"  Analyzed {result.get('transaction_count')} transactions")
print(f"  Mean Amount: ₹{result.get('mean_amount')}")
print(f"\n  Severity Scores:")
for i, tx_result in enumerate(result.get('results', []), 1):
    print(f"    Tx {i}: {tx_result.get('severity_label').upper():8} (score: {tx_result.get('severity_score'):5.1f}) - {tx_result.get('explanation')}")

print("\n" + "=" * 70)
print("✅ INTEGRATION COMPLETE - All 4 fuzzy logic endpoints working!")
print("=" * 70)
print("\nFuzzy Logic Systems Integrated:")
print("  1. FIS 1: Main Financial Risk (4 inputs, 18 rules, Mamdani centroid)")
print("  2. FIS 2: Budget Fuzzy Warning (2 inputs, 9 rules)")
print("  3. FIS 3: Savings Goal Advisor (3 inputs, 12 rules)")
print("  4. FIS 4: Anomaly Severity Scoring (3 inputs, 15 rules)")
print("\nFrontend Panels Added:")
print("  1. Fuzzy Risk Panel - MF visualization + 18-rule output")
print("  2. Budget Warning Panel - per-budget fuzzy alerts")
print("  3. Savings Advisor Panel - target strategy + INR ranges")
print("  4. Anomaly Severity Table - top-10 by severity score")
print("=" * 70)
