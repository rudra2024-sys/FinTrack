# FINTRACK FRONTEND — Production-Ready Implementation

## 🚀 Overview

A fully-functional, intelligent financial analytics dashboard with:
- ✅ JWT authentication system
- ✅ Real API integration  
- ✅ PDF bank statement upload
- ✅ ML-based transaction categorization
- ✅ HMM spending state visualization
- ✅ Real-time chart updates
- ✅ Comprehensive dashboard with analytics
- ✅ Budget, Goals, and Transactions management

## ⚡ Quick Start

### Prerequisites
- Node.js (v14+)
- Running backend at `http://localhost:8080`
- Running ML service (optional, for advanced features)

### Start Frontend Server
```bash
cd /c/Fintrack
node serve.mjs
```

Frontend will be available at: **http://localhost:3000**

## 🔐 Authentication

### Login Flow
1. Frontend loads → checks localStorage for saved JWT token
2. If no token → shows login page
3. User enters credentials → API calls `/auth/login`
4. Backend returns `accessToken` → saved to localStorage
5. Token automatically attached to all subsequent API requests
6. If token expires (401) → auto-redirect to login

### Credentials for Testing
- **Email:** test@fintrack.com
- **Password:** password123

### Demo Accounts (if available)
- Create new account via signup page

## 🏗️ Architecture

### Core Components

```
Frontend (localhost:3000)
├── Login/Register pages
├── Pages
│   ├── Overview (Dashboard + Net Worth)
│   ├── Transactions (with PDF upload)
│   ├── Budgets (spending limits)
│   ├── Goals (savings tracking)
│   ├── Analytics (trends & insights)
│   └── Dashboard (ML + HMM visualization)
├── API Layer (auth + data)
└── Charts (Chart.js + SVG)
     └── Real-time updates
```

### Tech Stack
- **HTML5** for structure
- **CSS3** (custom + no framework) for styling
- **Vanilla JavaScript** for logic
- **Chart.js** for visualizations
- **GSAP** for animations
- **SVG** for custom charts

## 📊 Features Implemented

### 1. **Authentication System**
```javascript
// Automatic token management
API_STATE = {
  token: (saved in localStorage),
  user: { email, name },
  isAuthenticated: boolean
}

// Every API call includes:
headers['Authorization'] = `Bearer ${token}`

// 401 responses trigger auto-redirect to login
```

### 2. **Transaction Management**
- ✅ Load transactions from `/transactions` endpoint
- ✅ Full-text search by merchant/category
- ✅ Filter by type (income/expense)
- ✅ Filter by category
- ✅ Pagination (15 items per page)
- ✅ Real-time updates after PDF upload

### 3. **PDF Upload Feature**
```javascript
// Drag & drop or click upload
// Sends to: POST /api/upload
// Automatically refreshes transaction list
// Shows success notification
```

### 4. **API Integration**

| Endpoint | Purpose | Page |
|----------|---------|------|
| `/auth/login` | User authentication | Login |
| `/auth/register` | New user signup | Register |
| `/transactions` | Get all transactions | Transactions, Dashboard |
| `/analytics/dashboard` | Dashboard summary | Overview, Dashboard |
| `/budgets` | Budget limits | Budgets |
| `/savings-goals` | Savings goals | Goals |
| `/categories` | Category list | Analytics |
| `/recurring-transactions` | Recurring payments | Analytics |
| `/accounts` | Bank accounts | Overview |
| `/api/hmm-states` | HMM state analysis | Dashboard |
| `/api/upload` | PDF statement upload | Transactions |

### 5. **Charts & Visualizations**

#### Income vs Expense Chart (SVG)
- 12-month trend
- Animated line drawing
- Area fill gradients
- Current value labels

#### Category Breakdown (Chart.js)
- Doughnut chart
- Top 6 categories
- Color-coded
- Spending percentages

#### HMM State Analysis
- Bar chart: Low/Normal/High spending states
- Percentage breakdown
- State timeline visualization

#### Donut Chart
- Account breakdown by balance
- Total net worth
- Legend with amounts

### 6. **Dashboard Features**
- **KPI Cards**: Income, Expenses, Savings, Rate, Balance
- **Charts**: Category distribution, spending trends
- **HMM Analysis**: Spending states over time
- **Transaction Table**: Filterable, paginated history
- **Anomaly Detection**: Unusual spending alerts
- **Insights**: Top category, top merchant, recurring items

### 7. **Notifications**
Real-time toast notifications for:
- ✅ Successful login
- ✅ API errors
- ✅ PDF upload success/failure
- ✅ Session expiration (401 errors)

### 8. **Error Handling**
- 401 errors → auto-redirect to login
- Network failures → user-friendly error messages
- Missing data → fallback UI with demo data
- Null safety throughout with optional chaining (??)

## 🎨 Design System

### Color Palette
```css
--bg:       #0a0a08    (Pure black background)
--card:     #131310    (Card background)
--acid:     #c8f000    (Neon green - positive)
--ember:    #ff4d00    (Orange - negative)
--ice:      #00e5ff    (Cyan - insights)
--offwhite: #e8e4dc    (Text)
--muted:    #5a5a52    (Secondary text)
```

### Typography
- **Display**: Barlow Condensed (headings, numbers)
- **Body**: Barlow (text)
- **Mono**: IBM Plex Mono (labels, technical text)

### Interactive States
- ✅ Hover effects (3D panel tilt)
- ✅ Focus states (keyboard navigation)
- ✅ Active states (buttons, tab indicators)
- ✅ Loading states (animations)

## 📱 Responsive Design
- Mobile-first approach (breaks at 720px, 900px, 1100px)
- Sidebar hides on mobile
- Grid adapts from 5 columns → 2 columns
- Touch-friendly inputs

## 🔄 Data Flow

```
User Login
  ↓
Get JWT Token (stored in localStorage)
  ↓
Show Main Dashboard
  ↓ 
  ├─→ Load Overview (accounts, net worth, charts)
  ├─→ Load Transactions (paginated)
  ├─→ Load Budgets
  ├─→ Load Goals
  ├─→ Load Analytics (monthly trends)
  └─→ Load Dashboard (ML + HMM analysis)
  ↓
Real-time Updates (on page navigation)
  ↓
Upload PDF → Auto-refresh transactions
```

## 🛠️ Development Notes

### Adding New Features
1. **New API Endpoint**: Update `api()` function if needed
2. **New Page**: 
   - Add `<div class="page" id="page-name">`
   - Create `loadNamePage()` function
   - Add nav icon + router
3. **New Chart**:
   - Use Chart.js or SVG renderer
   - Responsive container sizing
4. **Error Handling**:
   - Always check API response with `if (res && res.data)`
   - Show toast notifications
   - Log errors to console

### Testing
- **Login**: Use test@fintrack.com / password123
- **PDF Upload**: Try bank statements in PDF format
- **Filters**: Test category/date/type filters
- **Navigation**: Test all 6 pages (use keyboard Esc too)

### Browser DevTools
- Check **localStorage** for token
- Monitor **Network** tab for API calls
- Check **Console** for errors/logs
- Use **Lighthouse** for performance audit

## 🚀 Deployment

### Production Build
1. Minimize CSS in `<style>` tag
2. Bundle/minify JavaScript
3. Use CDN for GSAP, Chart.js, fonts
4. Set `BASE_URL` to production backend
5. Enable CORS in backend
6. Use HTTPS for auth tokens

### Environment Variables
```javascript
// In production, inject these:
const BASE_URL = process.env.REACT_APP_API_URL || 'http://localhost:8080';
```

## 🔗 API Response Formats

### Login Response
```json
{
  "accessToken": "eyJhbGc...",
  "user": { "id": 1, "email": "user@example.com", "name": "User" }
}
```

### Transactions Response
```json
[
  {
    "id": 1,
    "description": "Swiggy",
    "amount": -480,
    "category": "Food & Dining",
    "date": "2026-04-08",
    "hmm_state": "normal",
    "accountName": "HDFC Savings"
  }
]
```

### Dashboard Response
```json
{
  "monthlyIncome": 142000,
  "monthlyExpenses": 93500,
  "totalNetWorth": 1245230,
  "monthlyData": [
    { "month": "APR", "income": 95000, "expenses": 72000 }
  ],
  "categoryBreakdown": {
    "Food & Dining": 7800,
    "Shopping": 9100
  },
  "hmmStates": {
    "2026-04-08": "normal",
    "2026-04-07": "low"
  }
}
```

## 🐛 Troubleshooting

| Issue | Solution |
|-------|----------|
| Blank page | Check browser DevTools Console for errors |
| Login loop | Clear localStorage, refresh |
| 401 errors | Token expired → auto-redirect works |
| No transactions | Check backend `/transactions` endpoint |
| Charts not rendering | Verify data format, check Chart.js version |
| PDF upload fails | Ensure `/api/upload` endpoint exists |
| Animations choppy | Check GPU acceleration in browser settings |

## 📞 Support

For issues:
1. Check browser console (F12)
2. Check Network tab for failed API calls
3. Verify backend is running on 8080
4. Check JWT token in localStorage
5. Restart frontend server

## 📈 Performance Metrics (Target)

- **Page Load**: < 2 seconds
- **API Response**: < 500ms (average)
- **Chart Render**: < 1 second
- **Lighthouse Score**: > 80

## ✅ Checklist

- [x] Login/Register pages
- [x] JWT token management
- [x] API integration with auth headers
- [x] 6 main pages (Overview, Transactions, Budgets, Goals, Analytics, Dashboard)
- [x] PDF upload with drag & drop
- [x] Multiple chart types (SVG, Chart.js)
- [x] HMM state visualization
- [x] Real-time data fetching
- [x] Error handling + toasts
- [x] Responsive design
- [x] Dark theme (neon accent)
- [x] Animations (GSAP)
- [x] Keyboard navigation
- [x] Fallback demo data

## 🎉 Success!

Your Fintrack frontend is **production-ready**!

- ✅ Fully functional dashboard
- ✅ Real API integration
- ✅ ML-powered insights
- ✅ Beautiful, modern UI
- ✅ Professional animations
- ✅ Enterprise-grade error handling

**Start using it now:**
```bash
node serve.mjs  # Terminal 1
# Visit http://localhost:3000 in browser
```

---

**Built with:**
- HTML5, CSS3, Vanilla JavaScript
- Chart.js, GSAP, SVG
- Backend: Spring Boot 3.x + PostgreSQL
- ML Service: Python FastAPI

**Last Updated:** April 8, 2026
**Version:** 2.1
