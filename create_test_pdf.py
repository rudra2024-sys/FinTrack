#!/usr/bin/env python3
"""Generate a Google Pay Statement PDF using pypdf and raw PDF content."""

from pypdf import PdfWriter
from io import BytesIO

def create_pdf():
    """Create a PDF with Google Pay transactions."""
    
    writer = PdfWriter()
    
    # Transaction data - 20 realistic transactions
    transactions = [
        "01 FEB, 2026 04:02 AM Sent 400.00 To Rahul Kumar",
        "02 FEB, 2026 08:30 AM Paid 350.50 To Zomato Food Delivery",
        "03 FEB, 2026 02:15 PM Paid 120.00 To Uber Ride",
        "04 FEB, 2026 09:45 AM Received 50000.00 From Company Salary",
        "05 FEB, 2026 10:20 AM Paid 1200.00 To Amazon Shopping",
        "06 FEB, 2026 03:10 PM Paid 850.00 To Electricity Bill Payment",
        "07 FEB, 2026 11:00 AM Paid 499.00 To Netflix Subscription",
        "08 FEB, 2026 05:30 PM Sent 500.00 To Priya Sharma",
        "09 FEB, 2026 12:45 PM Paid 425.50 To Swiggy Food Order",
        "10 FEB, 2026 07:20 AM Paid 2000.00 To Gym Membership",
        "11 FEB, 2026 02:00 PM Sent 2500.00 To Medical Services",
        "12 FEB, 2026 09:15 AM Received 15000.00 From Freelance Project",
        "13 FEB, 2026 04:50 PM Paid 150.00 To Coffee Shop",
        "14 FEB, 2026 10:30 AM Paid 499.00 To Phone Recharge",
        "15 FEB, 2026 03:45 PM Paid 600.00 To Movie Tickets",
        "16 FEB, 2026 08:20 AM Sent 10000.00 To Rent Payment",
        "17 FEB, 2026 01:30 PM Paid 850.00 To Pharmacy Medicine",
        "18 FEB, 2026 06:15 PM Paid 1250.00 To Restaurant Dinner",
        "19 FEB, 2026 09:00 AM Received 5000.00 From Bonus Payment",
        "20 FEB, 2026 02:50 PM Paid 3500.00 To Insurance Premium",
    ]
    
    # Create PDF content
    pdf_content = b"""%PDF-1.4
%Test PDF - Google Pay Statement
1 0 obj
<<
/Type /Catalog
/Pages 2 0 R
>>
endobj
2 0 obj
<<
/Type /Pages
/Kids [3 0 R 4 0 R]
/Count 2
>>
endobj
3 0 obj
<<
/Type /Page
/Parent 2 0 R
/MediaBox [0 0 612 792]
/Contents 5 0 R
/Resources <</Font <</F1 6 0 R>>>>
>>
endobj
4 0 obj
<<
/Type /Page
/Parent 2 0 R
/MediaBox [0 0 612 792]
/Contents 7 0 R
/Resources <</Font <</F1 6 0 R>>>>
>>
endobj
5 0 obj
<<
/Length 1200
>>
stream
BT
/F1 24 Tf
50 750 Td
(Google Pay Statement) Tj
0 -30 Td
/F1 12 Tf
(February 2026 - Page 1 of 2) Tj
0 -25 Td
/F1 10 Tf"""
    
    # Add first 10 transactions
    for i, trans in enumerate(transactions[:10]):
        pdf_content += f"\n({trans}) Tj\n0 -15 Td"
    
    pdf_content += b"""
ET
endstream
endobj
6 0 obj
<<
/Type /Font
/Subtype /Type1
/BaseFont /Helvetica
>>
endobj
7 0 obj
<<
/Length 1200
>>
stream
BT
/F1 24 Tf
50 750 Td
(Google Pay Statement - Continued) Tj
0 -30 Td
/F1 12 Tf
(February 2026 - Page 2 of 2) Tj
0 -25 Td
/F1 10 Tf"""
    
    # Add remaining transactions
    for i, trans in enumerate(transactions[10:]):
        pdf_content += f"\n({trans}) Tj\n0 -15 Td"
    
    pdf_content += b"""
ET
endstream
endobj
xref
0 8
0000000000 65535 f
0000000009 00000 n
0000000074 00000 n
0000000150 00000 n
0000000301 00000 n
0000000452 00000 n
0000001707 00000 n
0000001806 00000 n
trailer
<<
/Size 8
/Root 1 0 R
>>
startxref
3061
%%EOF"""
    
    return pdf_content

if __name__ == "__main__":
    try:
        pdf_bytes = create_pdf()
        
        # Write to file
        with open("c:/Fintrack/test_google_pay_statement.pdf", "wb") as f:
            f.write(pdf_bytes)
        
        print("✓ PDF created successfully!")
        print(f"  Location: c:/Fintrack/test_google_pay_statement.pdf")
        print(f"  Size: {len(pdf_bytes)} bytes")
        print(f"  Pages: 2")
        print(f"  Transactions: 20")
        print("\nNow you can upload this PDF to the frontend!")
        
    except Exception as e:
        print(f"✗ Error: {e}")
        import traceback
        traceback.print_exc()
