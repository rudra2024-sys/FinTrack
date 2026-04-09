#!/usr/bin/env python3
"""Test ML service PDF extraction via HTTP."""

import requests
import sys

# Create a minimal valid PDF
minimal_pdf = b"""%PDF-1.4
1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj
2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj
3 0 obj<</Type/Page/MediaBox[0 0 612 792]/Parent 2 0 R/Resources<</Font<</F1 4 0 R>>>>>endobj
4 0 obj<</Type/Font/Subtype/Type1/BaseFont/Helvetica>>endobj
xref
0 5
0000000000 65535 f 
0000000009 00000 n 
0000000058 00000 n 
0000000115 00000 n 
0000000229 00000 n 
trailer<</Size 5/Root 1 0 R>>
startxref
262
%%EOF"""

def test_ml_service():
    """Call ML service PDF extraction endpoint."""
    try:
        print("Testing ML service PDF extraction endpoint...")
        
        url = "http://localhost:8001/ml/pdf/extract"
        files = {'file': ('test.pdf', minimal_pdf)}
        
        response = requests.post(url, files=files, timeout=10)
        
        print(f"Status: {response.status_code}")
        print(f"Response: {response.text}")
        
        if response.status_code == 200:
            print("✓ ML service responded successfully!")
            data = response.json()
            if data.get('transactions'):
                print(f"✓ Extracted {len(data['transactions'])} transactions")
            return True
        else:
            print(f"✗ ML service returned status {response.status_code}")
            return False
            
    except Exception as e:
        print(f"✗ Error: {e}")
        import traceback
        traceback.print_exc()
        return False

if __name__ == "__main__":
    success = test_ml_service()
    sys.exit(0 if success else 1)
