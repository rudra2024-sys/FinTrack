# API Call Reference — Copy & Paste

**Use these cURL commands to test endpoints directly**

---

## Authentication

### Login
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@fintrack.com",
    "password": "password123"
  }'
```

**Response:**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresIn": 86400,
  "user": {
    "id": 1,
    "email": "test@fintrack.com",
    "name": "Test User"
  }
}
```

---

### Register
```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "name": "New User",
    "email": "user@example.com",
    "password": "password"
  }'
```

---

## PDF Upload (REQUIRES TOKEN)

### Upload Statement → Process with ML
```bash
TOKEN="eyJhbGciOiJIUzI1NiJ9..."  # From login

curl -X POST http://localhost:8080/api/statements/upload \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@/path/to/statement.pdf" \
  -F "accountId=1" \
  -F "source=Browser Upload"
```

**Response:**
```json
{
  "success": true,
  "message": "PDF processed successfully",
  "transactionsCreated": 12,
  "statement": {
    "id": 456,
    "source": "Browser Upload",
    "uploadDate": "2026-04-08T10:30:00Z",
    "transactionCount": 12
  }
}
```

---

## Transactions (REQUIRES TOKEN)

### List All (with pagination)
```bash
TOKEN="eyJhbGciOiJIUzI1NiJ9..."

curl http://localhost:8080/api/transactions \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json"
```

**Query Parameters:**
```
?page=0&size=20&sort=date,desc
?categoryId=5
?search=swiggy
?type=EXPENSE
```

**Example with filters:**
```bash
curl "http://localhost:8080/api/transactions?page=0&size=15&type=EXPENSE&sort=date,desc" \
  -H "Authorization: Bearer $TOKEN"
```

**Response (sample):**
```json
{
  "content": [
    {
      "id": 123,
      "amount": 480,
      "description": "Swiggy - Food Delivery",
      "merchant_person": "Swiggy",
      "category": "Food & Dining",
      "categoryName": "Food & Dining",
      "date": "2026-04-08",
      "type": "EXPENSE",
      "accountName": "HDFC Savings",
      "hmmState": "normal",
      "isRecurring": false
    }
  ],
  "totalElements": 142,
  "totalPages": 10,
  "currentPage": 0,
  "pageSize": 20
}
```

### Create Transaction
```bash
TOKEN="eyJhbGciOiJIUzI1NiJ9..."

curl -X POST http://localhost:8080/api/transactions \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "amount": -500,
    "description": "Coffee",
    "category": "Food & Dining",
    "date": "2026-04-08",
    "accountId": 1,
    "type": "EXPENSE",
    "notes": "Daily coffee"
  }'
```

---

## Analytics (REQUIRES TOKEN)

### Dashboard Summary
```bash
TOKEN="eyJhbGciOiJIUzI1NiJ9..."

curl http://localhost:8080/api/analytics/dashboard \
  -H "Authorization: Bearer $TOKEN"
```

**Response:**
```json
{
  "totalIncome": 142000,
  "totalExpenses": 93500,
  "monthlyIncome": 142000,
  "monthlyExpenses": 93500,
  "monthlySavings": 48500,
  "totalNetWorth": 1245230,
  "accountBalance": 1245230,
  "categoryBreakdown": {
    "Food & Dining": 7800,
    "Shopping": 9100,
    "Transport": 2100,
    "Entertainment": 1450
  },
  "monthlyData": [
    {"month": "2026-01", "income": 130000, "expenses": 88000},
    {"month": "2026-02", "income": 138000, "expenses": 91000},
    {"month": "2026-03", "income": 140000, "expenses": 92500},
    {"month": "2026-04", "income": 142000, "expenses": 93500}
  ]
}
```

### Monthly Trend (12 months)
```bash
curl http://localhost:8080/api/analytics/monthly-trend \
  -H "Authorization: Bearer $TOKEN"
```

### Category Breakdown
```bash
curl "http://localhost:8080/api/analytics/category-breakdown?startDate=2026-01-01&endDate=2026-04-08" \
  -H "Authorization: Bearer $TOKEN"
```

---

## ML Intelligence (REQUIRES TOKEN)

### Analyze Transactions (Get HMM States)
```bash
TOKEN="eyJhbGciOiJIUzI1NiJ9..."

curl -X POST http://localhost:8080/api/intelligence/analyze \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "lookbackDays": 30,
    "includeProjections": true
  }'
```

**Response:**
```json
{
  "hmmAnalysis": {
    "2026-04-08": "low",
    "2026-04-07": "normal",
    "2026-04-06": "high",
    "2026-04-05": "normal",
    "2026-04-04": "low"
  },
  "spending_states": {
    "low": 12,
    "normal": 14,
    "high": 4
  },
  "riskScore": 0.34,
  "spendingTrend": "stable",
  "recommendations": [
    "Reduce dining out — currently 73% above average",
    "Monitor entertainment spending — trending upward"
  ]
}
```

### Insights (Anomalies + Patterns)
```bash
TOKEN="eyJhbGciOiJIUzI1NiJ9..."

curl http://localhost:8080/api/insights \
  -H "Authorization: Bearer $TOKEN"
```

**Response:**
```json
{
  "anomalies": [
    {
      "id": 101,
      "type": "unusual_amount",
      "description": "UnusualAmount: Transaction ₹25000 to Airline on card ending 1234",
      "severity": "medium",
      "amount": 25000,
      "date": "2026-04-03",
      "transaction_id": 789
    },
    {
      "id": 102,
      "type": "unusual_merchant",
      "description": "New merchant detected: ForeignCurrency Forex",
      "severity": "low",
      "merchant": "Forex Exchange",
      "date": "2026-04-01"
    }
  ],
  "spending_patterns": [
    {
      "category": "Food & Dining",
      "average": 450,
      "current": 780,
      "trend": "up",
      "severity": "warn"
    }
  ],
  "predictions": {
    "next_month_spending": 95000,
    "savings_rate": "34.5%",
    "confidence": 0.89
  },
  "recommendations": [
    "Reduce dining out — 73% above average",
    "You're on track for savings goal — keep it up!"
  ]
}
```

---

## Budgets (REQUIRES TOKEN)

### List Budgets
```bash
TOKEN="eyJhbGciOiJIUzI1NiJ9..."

curl http://localhost:8080/api/budgets \
  -H "Authorization: Bearer $TOKEN"
```

**Response:**
```json
{
  "content": [
    {
      "id": 1,
      "name": "Food & Dining",
      "category": "Food & Dining",
      "limit": 10000,
      "spent": 7800,
      "period": "MONTHLY",
      "createdDate": "2026-03-01"
    }
  ]
}
```

---

## Savings Goals (REQUIRES TOKEN)

### List Goals
```bash
curl http://localhost:8080/api/savings-goals \
  -H "Authorization: Bearer $TOKEN"
```

**Response:**
```json
{
  "content": [
    {
      "id": 1,
      "name": "Emergency Fund",
      "targetAmount": 500000,
      "currentAmount": 125000,
      "targetDate": "2026-12-31",
      "estimatedMonthsLeft": 9
    }
  ]
}
```

---

## Accounts (REQUIRES TOKEN)

### List All Accounts
```bash
TOKEN="eyJhbGciOiJIUzI1NiJ9..."

curl http://localhost:8080/api/accounts \
  -H "Authorization: Bearer $TOKEN"
```

**Response:**
```json
{
  "content": [
    {
      "id": 1,
      "name": "HDFC Savings",
      "type": "SAVINGS",
      "balance": 482000,
      "accountNumber": "****1234",
      "isActive": true
    },
    {
      "id": 2,
      "name": "Zerodha Investments",
      "type": "INVESTMENT",
      "balance": 620000,
      "accountNumber": "****5678",
      "isActive": true
    }
  ]
}
```

---

## Categories (REQUIRES TOKEN)

### List Categories
```bash
curl http://localhost:8080/api/categories \
  -H "Authorization: Bearer $TOKEN"
```

**Response:**
```json
{
  "content": [
    {
      "id": 1,
      "name": "Food & Dining",
      "icon": "🍔",
      "isSystem": true
    },
    {
      "id": 2,
      "name": "Shopping",
      "icon": "🛍️",
      "isSystem": true
    },
    {
      "id": 3,
      "name": "Transport",
      "icon": "🚗",
      "isSystem": true
    }
  ]
}
```

---

## Recurring Transactions (REQUIRES TOKEN)

### List Recurring
```bash
curl http://localhost:8080/api/recurring-transactions \
  -H "Authorization: Bearer $TOKEN"
```

**Response:**
```json
{
  "content": [
    {
      "id": 1,
      "description": "Netflix Subscription",
      "amount": 649,
      "frequency": "MONTHLY",
      "category": "Subscriptions",
      "nextDueDate": "2026-05-08",
      "isActive": true
    }
  ]
}
```

---

## Raw Browser Testing

### In JavaScript Console:

```javascript
// Step 1: Get token from login
const loginRes = await fetch('http://localhost:8080/api/auth/login', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ 
email: 'test@fintrack.com',
password: 'password123'
  })
})
const login = await loginRes.json()
const TOKEN = login.accessToken

// Step 2: Use token for other calls
const txRes = await fetch('http://localhost:8080/api/transactions', {
  headers: { 'Authorization': `Bearer ${TOKEN}` }
})
const txs = await txRes.json()
console.log('Transactions:', txs)

// Step 3: Get intelligence
const intelRes = await fetch('http://localhost:8080/api/intelligence/analyze', {
  method: 'POST',
  headers: { 
    'Authorization': `Bearer ${TOKEN}`,
    'Content-Type': 'application/json'
  },
  body: '{}'
})
const intel = await intelRes.json()
console.log('HMM States:', intel.hmmAnalysis)

// Step 4: Get insights
const insightsRes = await fetch('http://localhost:8080/api/insights', {
  headers: { 'Authorization': `Bearer ${TOKEN}` }
})
const insights = await insightsRes.json()
console.log('Anomalies:', insights.anomalies)
```

---

## Postman Collection (JSON)

```json
{
  "info": {
    "name": "FinTrack API",
    "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"
  },
  "auth": {
    "type": "bearer",
    "bearer": [{
      "key": "token",
      "value": "{{token}}",
      "type": "string"
    }]
  },
  "item": [
    {
      "name": "Login",
      "request": {
        "method": "POST",
        "url": "http://localhost:8080/api/auth/login",
        "body": {
          "mode": "raw",
"raw": "{ \"email\": \"test@fintrack.com\", \"password\": \"password123\" }"
        }
      }
    },
    {
      "name": "Get Transactions",
      "request": {
        "method": "GET",
        "url": "http://localhost:8080/api/transactions?page=0&size=20"
      }
    },
    {
      "name": "Get Dashboard",
      "request": {
        "method": "GET",
        "url": "http://localhost:8080/api/analytics/dashboard"
      }
    },
    {
      "name": "Analyze (HMM)",
      "request": {
        "method": "POST",
        "url": "http://localhost:8080/api/intelligence/analyze",
        "body": { "mode": "raw", "raw": "{}" }
      }
    },
    {
      "name": "Get Insights",
      "request": {
        "method": "GET",
        "url": "http://localhost:8080/api/insights"
      }
    }
  ]
}
```

---

## HTTP Status Codes

| Code | Meaning | Action |
|------|---------|--------|
| 200 | OK | Success ✓ |
| 201 | Created | Resource created ✓ |
| 204 | No Content | Success (empty response) ✓ |
| 400 | Bad Request | Check request format |
| 401 | Unauthorized | Invalid/expired token → re-login |
| 403 | Forbidden | Access denied → check account |
| 404 | Not Found | Endpoint doesn't exist |
| 500 | Server Error | Backend issue → check logs |

---

## Common Headers

```
Authorization: Bearer <token>
Content-Type: application/json
Accept: application/json
```

---

**💡 Copy-paste any example above to test API integration**
