#!/usr/bin/env python3
"""Test the generated PDF with ML service."""

import requests
import json

def test_pdf_with_ml():
    """Test the generated PDF."""
    try:
        print("=" * 70)
        print("Testing Generated PDF with ML Service")
        print("=" * 70)
        
        # Read the PDF file
        with open("c:/Fintrack/test_google_pay_statement.pdf", "rb") as f:
            pdf_bytes = f.read()
        
        print(f"\n✓ PDF loaded: {len(pdf_bytes)} bytes")
        
        # Send to ML service
        print("\nSending to ML service endpoint...")
        url = "http://localhost:8001/ml/pdf/extract"
        files = {'file': ('test_google_pay_statement.pdf', pdf_bytes)}
        
        response = requests.post(url, files=files, timeout=30)
        
        print(f"Status: {response.status_code}")
        
        if response.status_code == 200:
            data = response.json()
            transaction_count = data.get('transaction_count', 0)
            transactions = data.get('transactions', [])
            
            print(f"\n✓✓✓ SUCCESS! ✓✓✓")
            print(f"Transactions extracted: {transaction_count}")
            
            if transactions:
                print(f"\nTransactions (showing first 10):")
                for i, trans in enumerate(transactions[:10], 1):
                    date = trans.get('date', 'N/A')
                    merchant = trans.get('merchant_person', 'N/A')
                    amount = trans.get('amount', 'N/A')
                    txn_type = trans.get('transaction_type', 'N/A')
                    print(f"  {i}. {date} - {merchant}: Rs {amount} ({txn_type})")
                
                if len(transactions) > 10:
                    print(f"  ... and {len(transactions) - 10} more")
                
                # Calculate totals
                income = sum(float(t['amount']) for t in transactions if t.get('transaction_type') == 'credit')
                expenses = sum(float(t['amount']) for t in transactions if t.get('transaction_type') == 'debit')
                
                print(f"\nSummary:")
                print(f"  Total Income: Rs {income:,.2f}")
                print(f"  Total Expenses: Rs {expenses:,.2f}")
                print(f"  Net: Rs {income - expenses:,.2f}")
                
                return True
            else:
                print("✗ No transactions extracted (empty list)")
                return False
        else:
            print(f"\n✗ ML service error: {response.status_code}")
            print(f"Response: {response.text[:500]}")
            return False
            
    except requests.exceptions.ConnectionError:
        print("\n✗ Cannot connect to ML service at http://localhost:8001")
        print("   Make sure the ML service is running:")
        print("   docker compose up -d ml-service")
        return False
    except Exception as e:
        print(f"\n✗ Error: {e}")
        import traceback
        traceback.print_exc()
        return False

if __name__ == "__main__":
    success = test_pdf_with_ml()
    exit(0 if success else 1)
