#!/usr/bin/env python3
"""Discover available endpoints on FinTrack backend."""

import requests
import json

def discover_endpoints():
    """Discover available API endpoints."""
    
    print("\n" + "="*70)
    print("  DISCOVERING BACKEND ENDPOINTS")
    print("="*70 + "\n")
    
    backend_url = "http://localhost:8080"
    
    # Common Spring Boot endpoints to check
    common_paths = [
        "/",
        "/api",
        "/api/health",
        "/api/swagger-ui.html",
        "/api/v3/api-docs",
        "/v3/api-docs",
        "/swagger-ui.html",
        "/auth",
        "/auth/login",
        "/auth/register",
        "/authenticate",
        "/login",
        "/register",
        "/api/auth",
        "/api/auth/login",
        "/api/auth/register",
        "/actuator",
        "/actuator/health",
        "/health",
    ]
    
    print("Testing common endpoints:\n")
    
    found = []
    for path in common_paths:
        try:
            url = backend_url + path
            response = requests.get(url, timeout=3)
            status = response.status_code
            
            # Mark as "found" if it's not 404 or 405
            if status not in [404]:
                found.append((path, status))
                print(f"  GET {path:35s} → Status {status}")
        except:
            pass
    
    print("\n" + "="*70)
    print("  Testing POST endpoints (common patterns)")
    print("="*70 + "\n")
    
    post_paths = [
        "/auth/register",
        "/auth/login",
        "/api/auth/register",
        "/api/auth/login",
        "/authenticate",
        "/login",
    ]
    
    for path in post_paths:
        try:
            url = backend_url + path
            # Send empty JSON to check endpoint existence
            response = requests.post(url, json={}, timeout=3)
            status = response.status_code
            
            if status not in [404]:
                print(f"  POST {path:35s} → Status {status}")
                found.append((path, status))
        except:
            pass
    
    print("\n" + "="*70)
    print(f"  FOUND {len(found)} AVAILABLE ENDPOINTS")
    print("="*70 + "\n")
    
    if found:
        print("Available endpoints:\n")
        for path, status in found:
            print(f"  • {path} (HTTP {status})")

if __name__ == "__main__":
    discover_endpoints()
