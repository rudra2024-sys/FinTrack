#!/usr/bin/env python3
"""Debug 500 errors on analytics and profile endpoints."""

import requests
import json

def test_endpoints():
    """Test problematic endpoints with detailed error output."""
    
    backend_url = "http://localhost:8080"
    
    # First, register and get a token
    print("=" * 70)
    print("STEP 1: User Registration & Authentication")
    print("=" * 70)
    
    registration_data = {
        "email": "debug_test@fintrack.local",
        "password": "DebugPassword123!",
        "fullName": "Debug User",
        "currency": "USD"
    }
    
    try:
        resp = requests.post(
            f"{backend_url}/api/auth/register",
            json=registration_data,
            timeout=10
        )
        print(f"Register Status: {resp.status_code}")
        result = resp.json()
        token = result.get('accessToken')
        print(f"Token obtained: {token[:30]}...")
        
        headers = {"Authorization": f"Bearer {token}"}
        
        print("\n" + "=" * 70)
        print("STEP 2: Testing Problematic Endpoints")
        print("=" * 70)
        
        # Test /api/analytics
        print("\n1. Testing /api/analytics:")
        try:
            resp = requests.get(
                f"{backend_url}/api/analytics",
                headers=headers,
                timeout=10
            )
            print(f"   Status: {resp.status_code}")
            if resp.status_code != 200:
                print(f"   Response: {resp.text[:500]}")
            else:
                print(f"   Success: {json.dumps(resp.json(), indent=2)[:500]}")
        except Exception as e:
            print(f"   Error: {e}")
        
        # Test /api/profile
        print("\n2. Testing /api/profile:")
        try:
            resp = requests.get(
                f"{backend_url}/api/profile",
                headers=headers,
                timeout=10
            )
            print(f"   Status: {resp.status_code}")
            if resp.status_code != 200:
                print(f"   Response: {resp.text[:500]}")
            else:
                print(f"   Success: {json.dumps(resp.json(), indent=2)[:500]}")
        except Exception as e:
            print(f"   Error: {e}")
        
        # Test /api/analytics/dashboard
        print("\n3. Testing /api/analytics/dashboard:")
        try:
            resp = requests.get(
                f"{backend_url}/api/analytics/dashboard",
                headers=headers,
                timeout=10
            )
            print(f"   Status: {resp.status_code}")
            if resp.status_code != 200:
                print(f"   Response: {resp.text[:500]}")
            else:
                print(f"   Success: {json.dumps(resp.json(), indent=2)[:500]}")
        except Exception as e:
            print(f"   Error: {e}")
        
        # Test /api/goals (to see if there's a profile there)
        print("\n4. Testing /api/goals:")
        try:
            resp = requests.get(
                f"{backend_url}/api/goals",
                headers=headers,
                timeout=10
            )
            print(f"   Status: {resp.status_code}")
            if resp.status_code != 200:
                print(f"   Response: {resp.text[:500]}")
            else:
                print(f"   Success: {json.dumps(resp.json(), indent=2)[:500]}")
        except Exception as e:
            print(f"   Error: {e}")
            
    except Exception as e:
        print(f"Fatal error: {e}")

if __name__ == "__main__":
    test_endpoints()
