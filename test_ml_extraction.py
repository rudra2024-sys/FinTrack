#!/usr/bin/env python3
"""Test ML service PDF extraction endpoint after BytesIO fix."""

import sys
import json
import base64
from pathlib import Path

# Add the ML service to path
sys.path.insert(0, '/Fintrack/fintrack-ml-service')

from io import BytesIO
from reportlab.pdfgen import canvas
from reportlab.lib.pagesizes import letter


def create_test_pdf():
    """Create a simple test PDF with transaction-like text."""
    buffer = BytesIO()
    c = canvas.Canvas(buffer, pagesize=letter)
    c.setFont("Helvetica", 12)
    
    # Add some transaction-like text to the PDF
    y = 750
    transactions = [
        "20 FEBRUARY 2026 04:02 AM PAID TO REHMAN SAYYED 400.00",
        "21 FEBRUARY 2026 10:15 AM PAID TO ZOMATO 350.50",
        "22 FEBRUARY 2026 02:30 PM PAID TO UBER 120.00",
        "23 FEBRUARY 2026 03:45 PM RECEIVED FROM SALARY 50000.00",
        "24 FEBRUARY 2026 05:00 PM PAID TO AMAZON 1200.00 UPI 603213949185",
    ]
    
    for trans in transactions:
        c.drawString(50, y, trans)
        y -= 25
    
    c.save()
    buffer.seek(0)
    return buffer.getvalue()


def test_ml_service_extraction():
    """Test direct ML service extraction."""
    try:
        # Test 1: Import ml modules
        print("Test 1: Importing ML modules...")
        from app.pdf_parser import extract_google_pay_transactions
        print("✓ Successfully imported pdf_parser")
        
        # Test 2: Create test PDF
        print("\nTest 2: Creating test PDF...")
        pdf_bytes = create_test_pdf()
        print(f"✓ Created test PDF ({len(pdf_bytes)} bytes)")
        
        # Test 3: Extract transactions
        print("\nTest 3: Extracting transactions with BytesIO fix...")
        response = extract_google_pay_transactions(pdf_bytes, "test.pdf")
        print(f"✓ Extraction succeeded!")
        print(f"  Response: {response}")
        
        if response.transactions:
            print(f"✓ Extracted {len(response.transactions)} transactions:")
            for i, trans in enumerate(response.transactions, 1):
                print(f"  {i}. {trans.date} - {trans.merchant_person}: {trans.amount}")
        else:
            print("✗ No transactions extracted (response is empty)")
            return False
            
        return True
        
    except Exception as e:
        print(f"✗ Error: {e}")
        import traceback
        traceback.print_exc()
        return False


if __name__ == "__main__":
    success = test_ml_service_extraction()
    sys.exit(0 if success else 1)
