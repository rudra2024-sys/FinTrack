#!/usr/bin/env python3
"""Generate a realistic Google Pay Statement PDF for testing - Simple version."""

import sys
import os

def generate_simple_pdf():
    """Generate a PDF with Google Pay transactions using raw PDF format."""
    
    # Transaction data
    transactions = [
        ("01 FEB, 2026", "04:02 AM", "Sent", "Rahul Kumar", "400.00"),
        ("02 FEB, 2026", "08:30 AM", "Paid to", "Zomato - Food Delivery", "350.50"),
        ("03 FEB, 2026", "02:15 PM", "Paid to", "Uber - Ride", "120.00"),
        ("04 FEB, 2026", "09:45 AM", "Received", "Company Salary", "50000.00"),
        ("05 FEB, 2026", "10:20 AM", "Paid to", "Amazon Shopping", "1200.00"),
        ("06 FEB, 2026", "03:10 PM", "Paid to", "Electricity Bill", "850.00"),
        ("07 FEB, 2026", "11:00 AM", "Paid to", "Netflix Subscription", "499.00"),
        ("08 FEB, 2026", "05:30 PM", "Sent", "Priya Sharma", "500.00"),
        ("09 FEB, 2026", "12:45 PM", "Paid to", "Swiggy Food Order", "425.50"),
        ("10 FEB, 2026", "07:20 AM", "Paid to", "Gym Membership", "2000.00"),
        ("11 FEB, 2026", "02:00 PM", "Sent", "Medical Bill Payment", "2500.00"),
        ("12 FEB, 2026", "09:15 AM", "Received", "Freelance Project", "15000.00"),
        ("13 FEB, 2026", "04:50 PM", "Paid to", "Coffee Shop", "150.00"),
        ("14 FEB, 2026", "10:30 AM", "Paid to", "Phone Recharge", "499.00"),
        ("15 FEB, 2026", "03:45 PM", "Paid to", "Movie Tickets", "600.00"),
        ("16 FEB, 2026", "08:20 AM", "Sent", "Rent Payment", "10000.00"),
        ("17 FEB, 2026", "01:30 PM", "Paid to", "Pharmacy Medicine", "850.00"),
        ("18 FEB, 2026", "06:15 PM", "Paid to", "Restaurant Dinner", "1250.00"),
        ("19 FEB, 2026", "09:00 AM", "Received", "Bonus Payment", "5000.00"),
        ("20 FEB, 2026", "02:50 PM", "Paid to", "Insurance Premium", "3500.00"),
    ]
    
    # Build PDF content
    pdf_lines = [
        "%PDF-1.4",
        "1 0 obj",
        "<< /Type /Catalog /Pages 2 0 R >>",
        "endobj",
        "2 0 obj",
        "<< /Type /Pages /Kids [3 0 R 4 0 R] /Count 2 >>",
        "endobj",
        "3 0 obj",
        "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 5 0 R >> >> /Contents 6 0 R >>",
        "endobj",
        "4 0 obj",
        "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 5 0 R >> >> /Contents 7 0 R >>",
        "endobj",
        "5 0 obj",
        "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
        "endobj",
        "6 0 obj",
        "<< /Length 1500 >>",
        "stream",
        "BT",
        "/F1 24 Tf",
        "50 750 Td",
        "(Google Pay Statement) Tj",
        "0 -40 Td",
        "/F1 12 Tf",
        "(February 2026 - Page 1) Tj",
        "0 -30 Td",
        "",
    ]
    
    y_pos = 680
    line_height = 15
    lines_per_page = 30
    
    # Add first 10 transactions to page 1
    page = 1
    for idx, (date, time, txn_type, party, amount) in enumerate(transactions[:10]):
        tx_line = f"({date} {time} - {txn_type} {party}: Rs {amount}) Tj"
        pdf_lines.append(tx_line)
        pdf_lines.append("0 -15 Td")
    
    pdf_lines.append("ET")
    pdf_lines.append("endstream")
    pdf_lines.append("endobj")
    
    # Page 2 content
    pdf_lines.append("7 0 obj")
    pdf_lines.append("<< /Length 1500 >>")
    pdf_lines.append("stream")
    pdf_lines.append("BT")
    pdf_lines.append("/F1 24 Tf")
    pdf_lines.append("50 750 Td")
    pdf_lines.append("(Google Pay Statement - Continued) Tj")
    pdf_lines.append("0 -40 Td")
    pdf_lines.append("/F1 12 Tf")
    pdf_lines.append("(February 2026 - Page 2) Tj")
    pdf_lines.append("0 -30 Td")
    
    # Add remaining transactions to page 2
    for idx, (date, time, txn_type, party, amount) in enumerate(transactions[10:]):
        tx_line = f"({date} {time} - {txn_type} {party}: Rs {amount}) Tj"
        pdf_lines.append(tx_line)
        pdf_lines.append("0 -15 Td")
    
    pdf_lines.append("ET")
    pdf_lines.append("endstream")
    pdf_lines.append("endobj")
    
    # Calculate offsets
    content = "\n".join(pdf_lines[:-2])
    
    # Write PDF
    pdf_path = "c:/Fintrack/test_google_pay_statement.pdf"
    with open(pdf_path, "w", encoding="latin-1") as f:
        for line in pdf_lines:
            if line.startswith("<< /Length"):
                # Skip length placeholder, we'll calculate it
                continue
            f.write(line + "\n")
    
    return pdf_path

if __name__ == "__main__":
    try:
        # Try reportlab first
        try:
            from reportlab.lib.pagesizes import letter
            from reportlab.pdfgen import canvas
            from io import BytesIO
            
            # Create PDF with reportlab
            buffer = BytesIO()
            c = canvas.Canvas(buffer, pagesize=letter)
            
            transactions = [
                ("01 FEB, 2026", "04:02 AM", "Sent", "Rahul Kumar", "400.00"),
                ("02 FEB, 2026", "08:30 AM", "Paid to", "Zomato Food", "350.50"),
                ("03 FEB, 2026", "02:15 PM", "Paid to", "Uber Ride", "120.00"),
                ("04 FEB, 2026", "09:45 AM", "Received", "Company Salary", "50000.00"),
                ("05 FEB, 2026", "10:20 AM", "Paid to", "Amazon", "1200.00"),
                ("06 FEB, 2026", "03:10 PM", "Paid to", "Electricity", "850.00"),
                ("07 FEB, 2026", "11:00 AM", "Paid to", "Netflix", "499.00"),
                ("08 FEB, 2026", "05:30 PM", "Sent", "Priya Sharma", "500.00"),
                ("09 FEB, 2026", "12:45 PM", "Paid to", "Swiggy", "425.50"),
                ("10 FEB, 2026", "07:20 AM", "Paid to", "Gym", "2000.00"),
                ("11 FEB, 2026", "02:00 PM", "Sent", "Medical Bill", "2500.00"),
                ("12 FEB, 2026", "09:15 AM", "Received", "Freelance", "15000.00"),
                ("13 FEB, 2026", "04:50 PM", "Paid to", "Coffee", "150.00"),
                ("14 FEB, 2026", "10:30 AM", "Paid to", "Phone", "499.00"),
                ("15 FEB, 2026", "03:45 PM", "Paid to", "Movies", "600.00"),
                ("16 FEB, 2026", "08:20 AM", "Sent", "Rent", "10000.00"),
                ("17 FEB, 2026", "01:30 PM", "Paid to", "Pharmacy", "850.00"),
                ("18 FEB, 2026", "06:15 PM", "Paid to", "Restaurant", "1250.00"),
                ("19 FEB, 2026", "09:00 AM", "Received", "Bonus", "5000.00"),
                ("20 FEB, 2026", "02:50 PM", "Paid to", "Insurance", "3500.00"),
            ]
            
            c.setFont("Helvetica-Bold", 20)
            c.drawString(50, 750, "Google Pay Statement")
            c.setFont("Helvetica", 10)
            c.drawString(50, 730, "February 2026")
            
            y = 700
            for date, time, txn_type, party, amount in transactions:
                line = f"{date} {time} - {txn_type} {party}: Rs {amount}"
                c.drawString(50, y, line)
                y -= 15
                if y < 50:
                    c.showPage()
                    y = 750
            
            c.save()
            buffer.seek(0)
            
            with open("c:/Fintrack/test_google_pay_statement.pdf", "wb") as f:
                f.write(buffer.getvalue())
            
            print("✓ PDF generated successfully!")
            print(f"  File: c:/Fintrack/test_google_pay_statement.pdf")
            print(f"  Transactions: {len(transactions)}")
            
        except ImportError:
            print("reportlab not available, generating PDF directly...")
            generate_simple_pdf()
            print("✓ PDF generated!")
        
        sys.exit(0)
        
    except Exception as e:
        print(f"✗ Error: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)
