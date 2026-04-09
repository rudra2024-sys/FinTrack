#!/usr/bin/env python3
"""Generate a realistic Google Pay Statement PDF for testing."""

import sys
from datetime import datetime, timedelta
from reportlab.lib.pagesizes import letter, A4
from reportlab.lib import colors
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.lib.enums import TA_LEFT, TA_CENTER, TA_RIGHT
from reportlab.platypus import SimpleDocTemplate, Table, TableStyle, Paragraph, Spacer, PageBreak
from reportlab.lib.units import inch

def generate_test_pdf(filename="google_pay_statement.pdf"):
    """Generate a realistic Google Pay statement PDF."""
    
    doc = SimpleDocTemplate(filename, pagesize=letter, topMargin=0.5*inch, bottomMargin=0.5*inch)
    styles = getSampleStyleSheet()
    story = []
    
    # Title
    title_style = ParagraphStyle(
        'CustomTitle',
        parent=styles['Heading1'],
        fontSize=18,
        textColor=colors.HexColor('#1F2937'),
        spaceAfter=6,
        alignment=TA_CENTER,
        fontName='Helvetica-Bold'
    )
    story.append(Paragraph("Google Pay Statement", title_style))
    
    # Period
    period_style = ParagraphStyle(
        'Period',
        parent=styles['Normal'],
        fontSize=11,
        textColor=colors.HexColor('#6B7280'),
        alignment=TA_CENTER,
        spaceAfter=12
    )
    story.append(Paragraph("February 2026", period_style))
    
    # Transactions data
    transactions = [
        ("01 FEB, 2026", "04:02 AM", "Sent", "Rahul Kumar", "₹400.00"),
        ("02 FEB, 2026", "08:30 AM", "Paid to", "Zomato - Food Delivery", "₹350.50"),
        ("03 FEB, 2026", "02:15 PM", "Paid to", "Uber - Ride", "₹120.00"),
        ("04 FEB, 2026", "09:45 AM", "Received", "Company Salary", "₹50000.00"),
        ("05 FEB, 2026", "10:20 AM", "Paid to", "Amazon Shopping", "₹1200.00"),
        ("06 FEB, 2026", "03:10 PM", "Paid to", "Electricity Bill", "₹850.00"),
        ("07 FEB, 2026", "11:00 AM", "Paid to", "Netflix Subscription", "₹499.00"),
        ("08 FEB, 2026", "05:30 PM", "Sent", "Priya Sharma", "₹500.00"),
        ("09 FEB, 2026", "12:45 PM", "Paid to", "Swiggy Food Order", "₹425.50"),
        ("10 FEB, 2026", "07:20 AM", "Paid to", "Gym Membership", "₹2000.00"),
        ("11 FEB, 2026", "02:00 PM", "Sent", "Medical Bill Payment", "₹2500.00"),
        ("12 FEB, 2026", "09:15 AM", "Received", "Freelance Project", "₹15000.00"),
        ("13 FEB, 2026", "04:50 PM", "Paid to", "Coffee Shop", "₹150.00"),
        ("14 FEB, 2026", "10:30 AM", "Paid to", "Phone Recharge", "₹499.00"),
        ("15 FEB, 2026", "03:45 PM", "Paid to", "Movie Tickets", "₹600.00"),
        ("16 FEB, 2026", "08:20 AM", "Sent", "Rent Payment", "₹10000.00"),
        ("17 FEB, 2026", "01:30 PM", "Paid to", "Pharmacy Medicine", "₹850.00"),
        ("18 FEB, 2026", "06:15 PM", "Paid to", "Restaurant Dinner", "₹1250.00"),
        ("19 FEB, 2026", "09:00 AM", "Received", "Bonus Payment", "₹5000.00"),
        ("20 FEB, 2026", "02:50 PM", "Paid to", "Insurance Premium", "₹3500.00"),
        ("21 FEB, 2026", "11:10 AM", "Paid to", "Grocery Shopping", "₹2100.00"),
        ("22 FEB, 2026", "04:30 PM", "Sent", "Birthday Gift", "₹1000.00"),
        ("23 FEB, 2026", "10:45 AM", "Paid to", "Internet Bill", "₹799.00"),
        ("24 FEB, 2026", "03:20 PM", "Paid to", "Car Maintenance", "₹5000.00"),
        ("25 FEB, 2026", "07:15 AM", "Received", "Refund - Cancelled Order", "₹300.00"),
        ("26 FEB, 2026", "12:30 PM", "Paid to", "Salon Services", "₹800.00"),
        ("27 FEB, 2026", "05:00 PM", "Sent", "Charity Donation", "₹500.00"),
        ("28 FEB, 2026", "09:30 AM", "Paid to", "Gaming Subscription", "₹299.00"),
    ]
    
    # Create transaction tables for each page
    page_size = 10  # Transactions per page
    
    for page_num in range(0, len(transactions), page_size):
        if page_num > 0:
            story.append(PageBreak())
            story.append(Paragraph("Google Pay Statement (Continued)", title_style))
            story.append(Spacer(1, 0.2*inch))
        
        # Get transactions for this page
        page_transactions = transactions[page_num:page_num + page_size]
        
        # Create table data
        table_data = [["DATE", "TIME", "TYPE", "TO/FROM", "AMOUNT"]]
        
        for date, time, txn_type, party, amount in page_transactions:
            table_data.append([date, time, txn_type, party, amount])
        
        # Create table
        table = Table(table_data, colWidths=[1.2*inch, 0.9*inch, 0.9*inch, 2.5*inch, 1*inch])
        
        # Style table
        table.setStyle(TableStyle([
            # Header row
            ('BACKGROUND', (0, 0), (-1, 0), colors.HexColor('#E5E7EB')),
            ('TEXTCOLOR', (0, 0), (-1, 0), colors.HexColor('#1F2937')),
            ('ALIGN', (0, 0), (-1, 0), 'CENTER'),
            ('FONTNAME', (0, 0), (-1, 0), 'Helvetica-Bold'),
            ('FONTSIZE', (0, 0), (-1, 0), 11),
            ('BOTTOMPADDING', (0, 0), (-1, 0), 8),
            ('TOPPADDING', (0, 0), (-1, 0), 8),
            
            # Data rows
            ('ALIGN', (0, 1), (-1, -1), 'LEFT'),
            ('ALIGN', (4, 1), (4, -1), 'RIGHT'),
            ('FONTNAME', (0, 1), (-1, -1), 'Helvetica'),
            ('FONTSIZE', (0, 1), (-1, -1), 10),
            ('TOPPADDING', (0, 1), (-1, -1), 6),
            ('BOTTOMPADDING', (0, 1), (-1, -1), 6),
            
            # Alternate row colors
            ('ROWBACKGROUNDS', (0, 1), (-1, -1), [colors.white, colors.HexColor('#F9FAFB')]),
            
            # Grid
            ('GRID', (0, 0), (-1, -1), 0.5, colors.HexColor('#D1D5DB')),
        ]))
        
        story.append(table)
        story.append(Spacer(1, 0.3*inch))
        
        # Add summary for credit transactions
        credits = [float(amount.replace("₹", "").replace(",", "")) for _, _, txn_type, _, amount in page_transactions if txn_type == "Received"]
        debits = [float(amount.replace("₹", "").replace(",", "")) for _, _, txn_type, _, amount in page_transactions if txn_type != "Received"]
        
        summary_text = f"<b>Page Summary:</b> Total Income: ₹{sum(credits):,.2f} | Total Expenses: ₹{sum(debits):,.2f}"
        story.append(Paragraph(summary_text, styles['Normal']))
    
    # Add final summary
    story.append(PageBreak())
    story.append(Paragraph("Monthly Summary", title_style))
    story.append(Spacer(1, 0.2*inch))
    
    all_credits = [float(amount.replace("₹", "").replace(",", "")) for _, _, txn_type, _, amount in transactions if txn_type == "Received"]
    all_debits = [float(amount.replace("₹", "").replace(",", "")) for _, _, txn_type, _, amount in transactions if txn_type != "Received"]
    
    summary_data = [
        ["Metric", "Amount"],
        ["Total Income", f"₹{sum(all_credits):,.2f}"],
        ["Total Expenses", f"₹{sum(all_debits):,.2f}"],
        ["Net", f"₹{sum(all_credits) - sum(all_debits):,.2f}"],
        ["Transaction Count", f"{len(transactions)}"],
    ]
    
    summary_table = Table(summary_data, colWidths=[2*inch, 2*inch])
    summary_table.setStyle(TableStyle([
        ('BACKGROUND', (0, 0), (-1, 0), colors.HexColor('#1F2937')),
        ('TEXTCOLOR', (0, 0), (-1, 0), colors.white),
        ('ALIGN', (0, 0), (-1, -1), 'CENTER'),
        ('FONTNAME', (0, 0), (-1, 0), 'Helvetica-Bold'),
        ('FONTSIZE', (0, 0), (-1, 0), 12),
        ('BOTTOMPADDING', (0, 0), (-1, 0), 10),
        ('TOPPADDING', (0, 0), (-1, 0), 10),
        ('GRID', (0, 0), (-1, -1), 1, colors.HexColor('#D1D5DB')),
        ('ROWBACKGROUNDS', (0, 1), (-1, -1), [colors.HexColor('#F3F4F6'), colors.HexColor('#FFFFFF')]),
    ]))
    
    story.append(summary_table)
    story.append(Spacer(1, 0.3*inch))
    
    # Footer
    footer_style = ParagraphStyle(
        'Footer',
        parent=styles['Normal'],
        fontSize=9,
        textColor=colors.HexColor('#9CA3AF'),
        alignment=TA_CENTER
    )
    story.append(Paragraph("This is a test statement for the FinTrack Application", footer_style))
    story.append(Paragraph(f"Generated: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}", footer_style))
    
    # Build PDF
    doc.build(story)
    print(f"✓ PDF generated: {filename}")
    print(f"  Transactions: {len(transactions)}")
    print(f"  Total Income: ₹{sum(all_credits):,.2f}")
    print(f"  Total Expenses: ₹{sum(all_debits):,.2f}")
    print(f"  File size: {len(open(filename, 'rb').read())} bytes")
    
    return filename

if __name__ == "__main__":
    try:
        pdf_file = generate_test_pdf("c:/Fintrack/test_google_pay_statement.pdf")
        print(f"\n✓✓✓ PDF ready at: {pdf_file}")
        print("You can now upload this PDF to the frontend!")
        sys.exit(0)
    except Exception as e:
        print(f"✗ Error generating PDF: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)
