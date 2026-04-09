# FinTrack System Debug Checklist

**Use this when something isn't working**

---

## 🔴 PDF Upload Not Working

### ✓ Check 1: Account Selected
```javascript
console.log('Selected account:', selectedUploadAccount)
// Should show: "123" or similar ID
```
**Fix:** Select account from dropdown before uploading

---

### ✓ Check 2: Token Valid
```javascript
console.log('Token:', APP_STATE.token?.substring(0,20))
console.log('Is auth:', APP_STATE.isAuthenticated)
// If empty/false → login again
```
**Fix:** Log out and log back in

---

### ✓ Check 3: API Call Working
```javascript
// Enable logging
window.DEBUG_API = true

// Try upload again
// Look for: [PDF-UPLOAD] Response status: 200 ✓
```
**Fix:** Check console logs for actual error

---

### ✓ Check 4: Backend Accepting Upload
```bash
docker logs fintrack-backend | tail -50
# Look for lines mentioning "upload" or "statement"
```
**Fix:** Check if backend is running
```bash
docker ps | grep fintrack-backend
# If not running: docker start fintrack-backend
```

---

### ✓ Check 5: ML Service Categorizing
```bash
docker logs fintrack-ml-service | tail -50
# Should show PDF extraction results
```
**Fix:** If empty/errors, restart ML service
```bash
docker restart fintrack-ml-service
```

---

## 🔴 Transactions Show No Categories

### ✓ Check 1: Data Fetched
```javascript
console.log('Transactions:', txAllData)
console.log('First TX:', txAllData[0])
// Should show full transaction object
```
**Fix:** If empty, reload page

---

### ✓ Check 2: Category Field Exists
```javascript
if (txAllData.length > 0) {
  const tx = txAllData[0]
  console.log('Has category?', !!tx.category)
  console.log('Has categoryName?', !!tx.categoryName)
  console.log('Actual fields:', Object.keys(tx))
}
// Should show: true for one of them
```
**Fix:** Backend not categorizing. Check ML service logs

---

### ✓ Check 3: UI Display Bug
```javascript
// Manually render HTML
const tx = txAllData[0]
console.log(`Category display: ${tx.category ?? tx.categoryName ?? 'Uncategorized'}`)
// Should not be empty
```
**Fix:** Category exists but not displayed. Reload page

---

## 🔴 HMM States Not Showing

### ✓ Check 1: Dashboard Data Loaded
```javascript
console.log('Dashboard data:', dashAllData)
console.log('HMM states:', dashAllData.hmmStates)
// Should show date→state mapping
```
**Fix:** If empty, click Dashboard page again

---

### ✓ Check 2: API Returning States
```javascript
const intel = await api('/intelligence/analyze')
console.log('Intelligence:', intel)
console.log('HMM from API:', intel?.hmmAnalysis)
// Should have hmm_data field
```
**Fix:** ML service not computing states. Check logs:
```bash
docker logs fintrack-ml-service
```

---

### ✓ Check 3: HMM Chart Rendering
```javascript
console.log('Low:', document.getElementById('dash-stateLow')?.textContent)
console.log('Normal:', document.getElementById('dash-stateNormal')?.textContent)
console.log('High:', document.getElementById('dash-stateHigh')?.textContent)
// Should show numbers, not 0
```
**Fix:** Reload Dashboard page

---

## 🔴 API Calls Failing

### ✓ Check 1: Debug Enable
```javascript
window.DEBUG_API = true
// Now all API calls will be logged
```
**Fix:** Run API calls again, check console output

---

### ✓ Check 2: Backend Running
```bash
curl http://localhost:8080/api/transactions \
  -H "Authorization: Bearer test_token"
# Should get 401 (unauthorized) or 200
# NOT: Connection refused / timeout
```
**Fix:** Start backend
```bash
cd fintrack-backend && docker-compose up -d backend
# Or in Docker Desktop: start fintrack-backend container
```

---

### ✓ Check 3: Token Valid
```javascript
// Test with valid token
const testCall = await fetch('http://localhost:8080/api/transactions', {
  headers: { Authorization: `Bearer ${APP_STATE.token}` }
})
console.log('Status:', testCall.status)
// Should be 200, not 401/403
```
**Fix:** If 401/403, token expired → login again

---

### ✓ Check 4: CORS Issue
```javascript
// If you see CORS error in console:
// "No 'Access-Control-Allow-Origin' header"
```
**Fix:** Backend CORS not configured. SSH into container:
```bash
docker exec fintrack-backend env | grep CORS_ORIGINS
# Should show: http://localhost:3000
```

---

## 🔴 Anomalies Not Showing

### ✓ Check 1: API Returning Anomalies
```javascript
const insights = await api('/insights')
console.log('Insights:', insights)
console.log('Anomalies:', insights?.anomalies)
// Should be array with anomaly objects
```
**Fix:** ML/Analytics service not detecting anomalies (might be normal)

---

### ✓ Check 2: Dashboard UI Updated
```javascript
console.log('Anomaly HTML:', document.getElementById('dash-anomalies')?.innerHTML)
// Should show anomaly cards or "No anomalies detected"
```
**Fix:** Reload Dashboard page

---

## 🔴 Login Not Working

### ✓ Check 1: Backend Auth Service
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@fintrack.com","password":"password123"}'
# Should return: {accessToken: "eyJ...", ...}
```
**Fix:** If error, backend AuthController not working
```bash
docker logs fintrack-backend | grep -i "auth\|login"
```

---

### ✓ Check 2: Password Correct
```
Email: test@fintrack.com
Pass: password123
```
**Fix:** These are demo credentials. If wrong, check backend DB

---

### ✓ Check 3: Token Saved
```javascript
console.log('Saved token:', localStorage.getItem('fintrack_token'))
// Should show JWT token after login
```
**Fix:** localStorage disabled → enable in browser settings

---

## 🔴 Page Loads Slowly or Shows Static Data

### ✓ Check 1: Page Cache
```javascript
PAGE_LOADED.transactions = false
PAGE_LOADED.dashboard = false
PAGE_LOADED.analytics = false
// Force reload on next page switch
```
**Fix:** Click another page, click back

---

### ✓ Check 2: Data Stuck in Memory
```javascript
txAllData = []
dashAllData = { transactions: [], analytics: {} }
// Clear cache
```
**Fix:** Reload page: `location.reload()`

---

### ✓ Check 3: API Slow
```javascript
window.DEBUG_API = true
// Enable logging to see response times
// Example: [API] ✓ GET /transactions (1250ms)
```
**Fix:** If >1000ms, backend might be slow
```bash
docker stats fintrack-backend
# Check CPU, memory usage
```

---

## 🟢 Quick Self-Diagnostic

Run this in browser console to check everything:

```javascript
(async () => {
  console.log('=== FINTRACK DIAGNOSTIC ===')
  
  // 1. Auth
  console.log('Auth Status:', {
    token: !!APP_STATE.token,
    user: APP_STATE.user,
    authenticated: APP_STATE.isAuthenticated
  })
  
  // 2. API Connectivity
  try {
    const txs = await api('/transactions')
    console.log('✓ /transactions:', txs?.length || 'Error')
  } catch(e) { console.log('✗ /transactions:', e.message) }
  
  try {
    const dash = await api('/analytics/dashboard')
    console.log('✓ /analytics/dashboard:', !!dash)
  } catch(e) { console.log('✗ /analytics/dashboard:', e.message) }
  
  try {
    const intel = await api('/intelligence/analyze')
    console.log('✓ /intelligence/analyze:', !!intel?.hmmAnalysis)
  } catch(e) { console.log('✗ /intelligence/analyze:', e.message) }
  
  try {
    const insights = await api('/insights')
    console.log('✓ /insights:', insights?.anomalies?.length || 0, 'anomalies')
  } catch(e) { console.log('✗ /insights:', e.message) }
  
  // 3. ML Data
  console.log('ML Data:', {
    transactions_with_category: txAllData.filter(t => t.category).length,
    total_transactions: txAllData.length,
    hmm_states_count: Object.keys(dashAllData.hmmStates || {}).length
  })
  
  // 4. Page state
  console.log('Page State:', {
    pages_loaded: Object.keys(PAGE_LOADED).filter(k => PAGE_LOADED[k]),
    current_page: document.querySelector('.page.active')?.id
  })
  
  console.log('=== END DIAGNOSTIC ===')
})();
```

**Output should show:**
- ✓ Authenticated
- ✓ All API endpoints OK
- ✓ Transaction count > 0
- ✓ Pages loaded list

---

## Service Status Commands

```bash
# Check all services
docker ps

# Check specific service
docker logs fintrack-backend --tail 50
docker logs fintrack-ml-service --tail 50
docker logs fintrack-postgres --tail 50

# Restart service if stuck
docker restart fintrack-backend
docker restart fintrack-ml-service

# Full restart
docker-compose down
docker-compose up -d
```

---

## Browser DevTools Tips

### Console Shortcuts
```javascript
// Quick API test
await api('/transactions').then(d => table(d))

// Copy response to clipboard
const res = await api('/intelligence/analyze')
copy(res)  // Then paste in text editor

// Pretty print JSON
console.log(JSON.stringify(dashAllData, null, 2))
```

### Network Tab
1. Open DevTools → Network tab
2. Perform action (upload, page load, etc.)
3. Look for failed requests (red)
4. Check Response tab for error details

### Storage Tab
1. DevTools → Application → Storage
2. **Local Storage** → see `fintrack_token`
3. Clear to test re-login flow
4. **IndexedDB** → check for caches

---

**🟢 If all checks pass → system is working correctly!**

**If any check fails → note the failing step and check corresponding backend logs**
