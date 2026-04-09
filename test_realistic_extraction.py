#!/usr/bin/env python3
"""Test ML service with a realistic PDF containing transaction data."""

import requests
from io import BytesIO
import sys

def create_realistic_pdf():
    """Create a PDF with realistic Google Pay statement text."""
    
    # Create transaction text similar to Google Pay statements
    transaction_text = """Google Pay Statement - February 2026

20 FEBRUARY 2026 04:02 AM SENT 400.00 TO REHMAN SAYYED UPI TRANSACTION ID 603213949185

21 FEBRUARY 2026 10:15 AM PAID TO ZOMATO FOOD DELIVERY 350.50 FOOD DELIVERY

22 FEBRUARY 2026 02:30 PM UBER RIDE 120.00 TRANSPORT

23 FEBRUARY 2026 03:45 PM RECEIVED 50000.00 FROM EMPLOYER SALARY CREDIT

24 FEBRUARY 2026 05:00 PM PAID TO AMAZON 1200.00 SHOPPING ONLINE ORDER

25 FEBRUARY 2026 08:30 AM ELECTRICITY BILL PAYMENT 850.00 UTILITIES

26 FEBRUARY 2026 11:00 AM PAID TO NETFLIX 499.00 ENTERTAINMENT SUBSCRIPTION"""
    
    # PDF bytes crafted directly
    pdf_content = b"""%PDF-1.4
1 0 obj
<< /Type /Catalog /Pages 2 0 R >>
endobj
2 0 obj
<< /Type /Pages /Kids [3 0 R] /Count 1 >>
endobj
3 0 obj
<< /Type /Page /Parent 2 0 R /Resources << /Font << /F1 4 0 R >> >> /MediaBox [0 0 612 792] /Contents 5 0 R >>
endobj
4 0 obj
<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>
endobj
5 0 obj
<< /Length 500 >>
stream
BT
/F1 12 Tf
50 750 Td
(Google Pay Statement - February 2026) Tj
0 -20 Td
(20 FEBRUARY 2026 04:02 AM SENT 400.00 TO REHMAN SAYYED) Tj
0 -15 Td
(21 FEBRUARY 2026 10:15 AM PAID TO ZOMATO FOOD DELIVERY 350.50) Tj
0 -15 Td
(22 FEBRUARY 2026 02:30 PM UBER RIDE 120.00 TRANSPORT) Tj
0 -15 Td
(23 FEBRUARY 2026 03:45 PM RECEIVED 50000.00 FROM EMPLOYER SALARY) Tj
0 -15 Td
(24 FEBRUARY 2026 05:00 PM PAID TO AMAZON 1200.00 SHOPPING) Tj
0 -15 Td
(25 FEBRUARY 2026 08:30 AM ELECTRICITY BILL PAYMENT 850.00) Tj
0 -15 Td
(26 FEBRUARY 2026 11:00 AM PAID TO NETFLIX 499.00 ENTERTAINMENT) Tj
ET
endstream
endobj
xref
0 6
0000000000 65535 f 
0000000010 00000 n 
0000000058 00000 n 
0000000115 00000 n 
0000000257 00000 n 
0000000349 00000 n 
trailer
<< /Size 6 /Root 1 0 R >>
startxref
900
%%EOF"""
    
    return pdf_content

def test_with_realistic_pdf():
    """Test ML service with realistic transaction data."""
    try:
        print("Creating realistic test PDF with transaction data...")
        pdf_bytes = create_realistic_pdf()
        print(f"✓ Created PDF ({len(pdf_bytes)} bytes)")
        
        print("\nTesting ML service extraction...")
        url = "http://localhost:8001/ml/pdf/extract"
        files = {'file': ('google-pay-statement.pdf', pdf_bytes)}
        
        response = requests.post(url, files=files, timeout=10)
        
        print(f"Status: {response.status_code}")
        print(f"Response: {response.text}")
        
        if response.status_code == 200:
            data = response.json()
            transaction_count = data.get('transaction_count', 0)
            transactions = data.get('transactions', [])
            
            print(f"\n✓ ML Service Response:")
            print(f"  Total transactions extracted: {transaction_count}")
            
            if transactions:
                print(f"\n  Transactions found:")
                for i, trans in enumerate(transactions, 1):
                    print(f"    {i}. {trans.get('date')} {trans.get('time')} - {trans.get('merchant_person')}: {trans.get('amount')} ({trans.get('transaction_type')})")
                return True
            else:
                print(f"\n✗ No transactions extracted (empty list)")
                return False if transaction_count == 0 else True
        else:
            print(f"\n✗ ML service returned status {response.status_code}")
            return False
            
    except Exception as e:
        print(f"\n✗ Error: {e}")
        import traceback
        traceback.print_exc()
        return False

if __name__ == "__main__":
    success = test_with_realistic_pdf()
    if success:
        print("\n✓✓✓ ML Service extraction is working! ✓✓✓")
    else:
        print("\n✗ ML Service extraction needs review")
    sys.exit(0 if success else 1)
