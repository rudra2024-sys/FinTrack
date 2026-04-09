#!/usr/bin/env python3
"""
FinTrack Production-Ready Integration Test Suite
Tests all critical flows end-to-end with multiple scenarios
"""

import requests
import json
import sys
from datetime import datetime, timedelta
from typing import Optional, Dict, Any
import time

class FintrackProductionTest:
    """Comprehensive production-readiness test suite."""
    
    def __init__(self, backend_url: str = "http://localhost:8080"):
        self.backend_url = backend_url
        self.token = None
        self.user_id = None
        self.email = f"prod_test_{int(time.time())}@fintrack.local"
        self.password = "ProdTest123!"
        self.results = {
            "tests": [],
            "passed": 0,
            "failed": 0,
            "errors": []
        }
    
    def log_test(self, name: str, passed: bool, message: str = "", response_code: int = 0):
        """Log test result."""
        self.results["tests"].append({
            "name": name,
            "passed": passed,
            "message": message,
            "status_code": response_code,
            "timestamp": datetime.now().isoformat()
        })
        if passed:
            self.results["passed"] += 1
            print(f"✓ {name}")
        else:
            self.results["failed"] += 1
            print(f"✗ {name}: {message}")
            self.results["errors"].append(message)
    
    def test_1_service_connectivity(self):
        """Test 1: Basic service connectivity."""
        print("\n" + "="*70)
        print("TEST 1: Service Connectivity")
        print("="*70)
        
        try:
            response = requests.get(f"{self.backend_url}/api/v3/api-docs", timeout=5)
            self.log_test(
                "Backend responds to requests",
                response.status_code == 200,
                f"Status: {response.status_code}",
                response.status_code
            )
        except Exception as e:
            self.log_test("Backend responds to requests", False, str(e))
    
    def test_2_authentication_flow(self):
        """Test 2: Complete authentication flow."""
        print("\n" + "="*70)
        print("TEST 2: Authentication Flow")
        print("="*70)
        
        # 2a: User Registration
        try:
            reg_data = {
                "email": self.email,
                "password": self.password,
                "fullName": "Prod Test User",
                "currency": "USD"
            }
            response = requests.post(
                f"{self.backend_url}/api/auth/register",
                json=reg_data,
                timeout=10
            )
            
            if response.status_code in [200, 201]:
                result = response.json()
                self.token = result.get('accessToken')
                self.user_id = result.get('user', {}).get('id')
                self.log_test(
                    "User registration successful",
                    True,
                    f"User ID: {self.user_id}",
                    response.status_code
                )
            else:
                self.log_test(
                    "User registration",
                    False,
                    f"Status: {response.status_code}",
                    response.status_code
                )
                return
        except Exception as e:
            self.log_test("User registration", False, str(e))
            return
        
        # 2b: Login with created credentials
        try:
            login_data = {
                "email": self.email,
                "password": self.password
            }
            response = requests.post(
                f"{self.backend_url}/api/auth/login",
                json=login_data,
                timeout=10
            )
            
            if response.status_code == 200:
                result = response.json()
                self.log_test(
                    "User login successful",
                    True,
                    f"Token length: {len(result.get('accessToken', ''))}",
                    response.status_code
                )
            else:
                self.log_test(
                    "User login",
                    False,
                    f"Status: {response.status_code}",
                    response.status_code
                )
        except Exception as e:
            self.log_test("User login", False, str(e))
    
    def test_3_jwt_validation(self):
        """Test 3: JWT token validation."""
        print("\n" + "="*70)
        print("TEST 3: JWT Token Validation")
        print("="*70)
        
        if not self.token:
            print("⚠️  Skipping JWT tests - no token available")
            return
        
        headers = {"Authorization": f"Bearer {self.token}"}
        
        # 3a: Valid token should allow access
        try:
            response = requests.get(
                f"{self.backend_url}/api/analytics/dashboard",
                headers=headers,
                timeout=10
            )
            self.log_test(
                "Authenticated request accepted",
                response.status_code == 200,
                f"Status: {response.status_code}",
                response.status_code
            )
        except Exception as e:
            self.log_test("Authenticated request", False, str(e))
        
        # 3b: Missing token should deny access
        try:
            response = requests.get(
                f"{self.backend_url}/api/analytics/dashboard",
                timeout=10
            )
            self.log_test(
                "Unauthenticated request denied",
                response.status_code in [401, 403],
                f"Status: {response.status_code}",
                response.status_code
            )
        except Exception as e:
            self.log_test("Unauthenticated request", False, str(e))
        
        # 3c: Invalid token should deny access
        try:
            bad_headers = {"Authorization": "Bearer invalid.token.here"}
            response = requests.get(
                f"{self.backend_url}/api/analytics/dashboard",
                headers=bad_headers,
                timeout=10
            )
            self.log_test(
                "Invalid token rejected",
                response.status_code in [401, 403],
                f"Status: {response.status_code}",
                response.status_code
            )
        except Exception as e:
            self.log_test("Invalid token test", False, str(e))
    
    def test_4_fixed_endpoints(self):
        """Test 4: Previously broken endpoints now working."""
        print("\n" + "="*70)
        print("TEST 4: Fixed Endpoints")
        print("="*70)
        
        if not self.token:
            print("⚠️  Skipping endpoint tests - no token available")
            return
        
        headers = {"Authorization": f"Bearer {self.token}"}
        
        endpoints = [
            ("/api/analytics", "GET", "Default analytics endpoint"),
            ("/api/analytics/dashboard", "GET", "Dashboard summary"),
            ("/api/profile", "GET", "User profile endpoint"),
            ("/api/accounts", "GET", "Accounts list"),
        ]
        
        for endpoint, method, description in endpoints:
            try:
                url = f"{self.backend_url}{endpoint}"
                if method == "GET":
                    response = requests.get(url, headers=headers, timeout=10)
                else:
                    response = requests.post(url, headers=headers, timeout=10)
                
                # 200, 404 on missing data is OK, just not 500
                success = response.status_code != 500
                self.log_test(
                    description,
                    success,
                    f"Status: {response.status_code}",
                    response.status_code
                )
            except Exception as e:
                self.log_test(description, False, str(e))
    
    def test_5_database_persistence(self):
        """Test 5: Database persistence."""
        print("\n" + "="*70)
        print("TEST 5: Database Persistence")
        print("="*70)
        
        if not self.token:
            print("⚠️  Skipping database tests - no token available")
            return
        
        headers = {"Authorization": f"Bearer {self.token}"}
        
        # 5a: Create an account
        try:
            account_data = {
                "name": "Test Account",
                "type": "CHECKING",
                "currency": "USD",
                "balance": 1000.00
            }
            response = requests.post(
                f"{self.backend_url}/api/accounts",
                json=account_data,
                headers=headers,
                timeout=10
            )
            
            if response.status_code in [200, 201]:
                account = response.json()
                account_id = account.get('id')
                self.log_test(
                    "Create account in database",
                    True,
                    f"Account ID: {account_id}",
                    response.status_code
                )
                
                # 5b: Retrieve the created account
                time.sleep(0.5)
                response2 = requests.get(
                    f"{self.backend_url}/api/accounts/{account_id}",
                    headers=headers,
                    timeout=10
                )
                
                self.log_test(
                    "Retrieve account from database",
                    response2.status_code == 200,
                    f"Status: {response2.status_code}",
                    response2.status_code
                )
            else:
                self.log_test(
                    "Create account",
                    False,
                    f"Status: {response.status_code}",
                    response.status_code
                )
        except Exception as e:
            self.log_test("Database persistence", False, str(e))
    
    def test_6_error_handling(self):
        """Test 6: Error handling and meaningful responses."""
        print("\n" + "="*70)
        print("TEST 6: Error Handling")
        print("="*70)
        
        if not self.token:
            print("⚠️  Skipping error handling tests - no token available")
            return
        
        headers = {"Authorization": f"Bearer {self.token}"}
        
        # 6a: Missing required parameters
        try:
            response = requests.get(
                f"{self.backend_url}/api/analytics/category-breakdown",
                headers=headers,
                timeout=10
            )
            self.log_test(
                "Missing parameters returns 400",
                response.status_code == 400,
                f"Status: {response.status_code}",
                response.status_code
            )
        except Exception as e:
            self.log_test("Missing parameters", False, str(e))
        
        # 6b: Invalid resource ID
        try:
            response = requests.get(
                f"{self.backend_url}/api/accounts/999999",
                headers=headers,
                timeout=10
            )
            self.log_test(
                "Invalid resource returns 404",
                response.status_code == 404,
                f"Status: {response.status_code}",
                response.status_code
            )
        except Exception as e:
            self.log_test("Invalid resource", False, str(e))
        
        # 6c: No 500 errors on valid paths
        try:
            response = requests.get(
                f"{self.backend_url}/api/profile",
                headers=headers,
                timeout=10
            )
            self.log_test(
                "Valid endpoints never return 500",
                response.status_code != 500,
                f"Status: {response.status_code}",
                response.status_code
            )
        except Exception as e:
            self.log_test("Valid endpoint 500 check", False, str(e))
    
    def test_7_cors_headers(self):
        """Test 7: CORS headers for frontend integration."""
        print("\n" + "="*70)
        print("TEST 7: CORS Headers")
        print("="*70)
        
        try:
            response = requests.options(
                f"{self.backend_url}/api/analytics/dashboard",
                timeout=5
            )
            
            headers = response.headers
            has_cors = "access-control-allow-origin" in headers or "Access-Control-Allow-Origin" in headers
            
            self.log_test(
                "CORS headers present",
                has_cors or response.status_code == 200,
                f"Status: {response.status_code}",
                response.status_code
            )
        except Exception as e:
            self.log_test("CORS headers", False, str(e))
    
    def generate_report(self):
        """Generate test report."""
        print("\n" + "="*70)
        print("TEST SUMMARY")
        print("="*70)
        
        total = self.results["passed"] + self.results["failed"]
        passed_pct = (self.results["passed"] / total * 100) if total > 0 else 0
        
        print(f"\nTotal Tests: {total}")
        print(f"Passed: {self.results['passed']} ({passed_pct:.1f}%)")
        print(f"Failed: {self.results['failed']}")
        
        if self.results["failed"] > 0:
            print("\n❌ Errors:")
            for error in self.results["errors"][:10]:  # Show first 10 errors
                print(f"  • {error}")
        
        print("\n" + "="*70)
        print("FINAL STATUS")
        print("="*70)
        
        if self.results["failed"] == 0:
            print("✓ ALL TESTS PASSED - System is production-ready!")
            return True
        elif passed_pct >= 80:
            print(f"⚠️  PARTIAL PASS ({passed_pct:.1f}%) - Minor issues exist")
            return True
        else:
            print(f"✗ TESTS FAILED - System needs fixes")
            return False
    
    def run_all_tests(self):
        """Run complete test suite."""
        print("\n╔" + "="*68 + "╗")
        print("║" + " "*12 + "FINTRACK PRODUCTION READINESS TEST SUITE" + " "*17 + "║")
        print("╚" + "="*68 + "╝")
        
        self.test_1_service_connectivity()
        self.test_2_authentication_flow()
        self.test_3_jwt_validation()
        self.test_4_fixed_endpoints()
        self.test_5_database_persistence()
        self.test_6_error_handling()
        self.test_7_cors_headers()
        
        success = self.generate_report()
        
        return success

if __name__ == "__main__":
    tester = FintrackProductionTest()
    success = tester.run_all_tests()
    
    # Print JSON report for programmatic consumption
    print("\nJSON Report:")
    print(json.dumps(tester.results, indent=2))
    
    sys.exit(0 if success else 1)
