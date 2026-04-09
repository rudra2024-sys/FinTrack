#!/usr/bin/env python3
"""
Direct test of the PDF parser to debug extraction issues.
"""

import sys
sys.path.insert(0, '/fintrack/fintrack-ml-service')

from io import BytesIO
from pypdf import PdfReader
from app.pdf_parser import extract_google_pay_transactions, parse_line, _extract_by_date_chunks, sanitize
import re

# Test with sample data
test_pdf_content = """
20 FEBRUARY 2026 SENT 21:21:03 RECEIVED 40.903 RATE & TIME TRANSACTION IN DETAILS AMOUNT OF FEB. 2026 04:02 AM PAID TO REHMAN SAYYED UPI TRANSACTION ID 603213949185 PAID BY STATE BANK OF INDIA 1092 400

21 FEBRUARY 2026 10:15 AM PAID TO ZOMATO FOOD DELIVERY 350.50

22 FEBRUARY 2026 02:30 PM PAID TO UBER TRANSPORTATION 120.00

23 FEBRUARY 2026 11:45 AM PAID TO AMAZON SHOPPING 1299.99
"""

print("=" * 70)
print("PDF PARSER DIRECT TEST")
print("=" * 70)

# Test 1: Sanitize function
print("\n[TEST 1] Sanitize function")
print("-" * 70)
test_text = "20  FEBRUARY    2026   PAID   TO   REHMAN   400"
sanitized = sanitize(test_text)
print(f"Input:  {test_text}")
print(f"Output: {sanitized}")

# Test 2: Parse individual lines
print("\n[TEST 2] Parse individual lines")
print("-" * 70)
test_lines = [
    "20 FEBRUARY 2026 04:02 AM PAID TO REHMAN SAYYED 400",
    "21 FEBRUARY 2026 10:15 AM PAID TO ZOMATO 350.50",
    "22 FEBRUARY 2026 02:30 PM PAID TO UBER 120.00",
]

for line in test_lines:
    result = parse_line(line)
    if result:
        print(f"✓ Parsed: {result.merchant_person} - ₹{result.amount}")
    else:
        print(f"✗ Failed: {line}")

# Test 3: Regex patterns
print("\n[TEST 3] Regex pattern matching")
print("-" * 70)

DATE_RE = r"(?P<date>\d{1,2}[/-]\d{1,2}[/-]\d{2,4}|\d{1,2}\s+[A-Za-z]{3,9},?\s+\d{2,4})"
TIME_RE = r"(?P<time>\d{1,2}:\d{2}(?::\d{2})?\s?(?:AM|PM|am|pm)?)"
AMOUNT_RE = r"(?:Rs\.?|INR|₹|â‚¹)?\s?(?P<amount>[0-9,]+(?:\.\d{1,2})?)"
EVENT_RE = r"(?P<kind>paid to|sent to|debited to|received from|credited by|payment to|collect from|transferred to)"

DATE_FINDER = re.compile(DATE_RE, re.IGNORECASE)
PATTERN_FULL = re.compile(
    rf"{DATE_RE}\s+{TIME_RE}.*?{EVENT_RE}\s+"
    rf"(?P<party>[A-Za-z0-9&@._'()\-/ ]+?)\s+{AMOUNT_RE}",
    re.IGNORECASE | re.DOTALL,
)

test_line = "20 FEBRUARY 2026 04:02 AM PAID TO REHMAN SAYYED 400"
date_match = DATE_FINDER.search(test_line)
full_match = PATTERN_FULL.search(test_line)

print(f"Test Line: {test_line}")
print(f"Date Match: {date_match.group('date') if date_match else 'NO MATCH'}")
print(f"Full Pattern Match: {bool(full_match)}")
if full_match:
    print(f"  - Date: {full_match.group('date')}")
    print(f"  - Time: {full_match.group('time')}")
    print(f"  - Event: {full_match.group('kind')}")
    print(f"  - Party: {full_match.group('party')}")
    print(f"  - Amount: {full_match.group('amount')}")

print("\n" + "=" * 70)
print("TESTS COMPLETE")
print("=" * 70)
