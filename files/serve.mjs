import express from 'express';
import path from 'path';
import { fileURLToPath } from 'url';
import fs from 'fs';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const app = express();
const PORT = 3000;

// ============================================================================
// CONFIGURATION: EXPLICIT FILE PATH RESOLUTION
// ============================================================================
// Define the SINGLE SOURCE OF TRUTH for frontend files
// ONLY serves from this directory - no ambiguity
const FRONTEND_DIR = path.resolve('c:/Fintrack/fintrack-frontend');
const INDEX_HTML_PATH = path.join(FRONTEND_DIR, 'index.html');

console.log('='.repeat(80));
console.log('🚀 FINTRACK FRONTEND SERVER - DEBUG MODE');
console.log('='.repeat(80));
console.log(`Frontend Directory: ${FRONTEND_DIR}`);
console.log(`Index.html Path:   ${INDEX_HTML_PATH}`);
console.log(`File Exists:       ${fs.existsSync(INDEX_HTML_PATH) ? '✅ YES' : '❌ NO'}`);

// Verify file exists - FAIL FAST if not found
if (!fs.existsSync(INDEX_HTML_PATH)) {
  console.error(`\n❌ CRITICAL ERROR: index.html not found at ${INDEX_HTML_PATH}`);
  console.error('Check paths and ensure file exists before starting server.\n');
  process.exit(1);
}

// Get file size and last modified time for verification
const stats = fs.statSync(INDEX_HTML_PATH);
console.log(`File Size:        ${stats.size} bytes`);
console.log(`Last Modified:    ${stats.mtime.toISOString()}`);
console.log('='.repeat(80));
console.log('');

// ============================================================================
// MIDDLEWARE: DISABLE ALL CACHING
// ============================================================================
app.use((req, res, next) => {
  // Aggressive cache-busting headers
  res.setHeader('Cache-Control', 'no-store, no-cache, must-revalidate, max-age=0, proxy-revalidate');
  res.setHeader('Pragma', 'no-cache');
  res.setHeader('Expires', '0');
  res.setHeader('ETag', 'W/"' + Date.now() + '"'); // Change ETag on every request
  res.setHeader('Last-Modified', new Date().toUTCString()); // Always report as fresh
  res.setHeader('Surrogate-Control', 'no-store');
  res.setHeader('X-Content-Type-Options', 'nosniff');
  next();
});

// ============================================================================
// MIDDLEWARE: REQUEST LOGGING
// ============================================================================
app.use((req, res, next) => {
  const timestamp = new Date().toISOString();
  console.log(`[${timestamp}] ${req.method} ${req.path}`);
  next();
});

// ============================================================================
// ROUTE: SERVE index.html FOR ROOT AND SPA ROUTES
// ============================================================================
app.get('/', (req, res) => {
  console.log('📄 Serving index.html (root route)');
  serveIndexHtml(res);
});

// Catch-all for SPA routes (any undefined route serves index.html)
app.get(/^\/(?!api\/).*/, (req, res) => {
  if (req.path === '/favicon.ico') {
    return res.status(404).send('Not Found');
  }
  console.log(`📄 Serving index.html (SPA fallback for: ${req.path})`);
  serveIndexHtml(res);
});

// ============================================================================
// FUNCTION: SERVE index.html WITH VERIFICATION
// ============================================================================
function serveIndexHtml(res) {
  try {
    // Read file FRESH on every request - never cached
    const htmlContent = fs.readFileSync(INDEX_HTML_PATH, 'utf-8');
    
    // Verification checks
    const hasLogin = htmlContent.includes('handleLogin');
    const hasPdfUpload = htmlContent.includes('handlePdfUpload');
    const hasDashboard = htmlContent.includes('dashboard');
    
    console.log(`   ✓ Content Verification:`);
    console.log(`     - handleLogin:     ${hasLogin ? '✅' : '❌'}`);
    console.log(`     - handlePdfUpload: ${hasPdfUpload ? '✅' : '❌'}`);
    console.log(`     - dashboard:       ${hasDashboard ? '✅' : '❌'}`);
    console.log(`   ✓ File Size: ${htmlContent.length} bytes`);
    console.log(`   ✓ LOADED NEW FRONTEND - Ready to serve\n`);
    
    // Send with explicit headers
    res.type('text/html; charset=utf-8');
    res.send(htmlContent);
    
  } catch (error) {
    console.error(`❌ ERROR reading index.html: ${error.message}`);
    res.status(500).send('Error loading frontend');
  }
}

// ============================================================================
// ROUTE: API PROXY (if needed for local development)
// ============================================================================
// If your frontend needs to hit the backend, uncomment this:
/*
app.use('/api', (req, res) => {
  console.log(`🔗 API proxy: ${req.method} ${req.path}`);
  // Forward to http://localhost:8080
  // (Requires setting up proper proxy middleware)
});
*/

// ============================================================================
// ERROR HANDLING
// ============================================================================
app.use((err, req, res, next) => {
  console.error(`❌ Server Error: ${err.message}`);
  res.status(500).send('Internal Server Error');
});

// ============================================================================
// START SERVER
// ============================================================================
app.listen(PORT, () => {
  console.log(`\n✅ Server running on http://localhost:${PORT}`);
  console.log('📌 Press Ctrl+C to stop\n');
  console.log('🔧 DEBUG CHECKLIST:');
  console.log('   1. Browser: Open DevTools (F12)');
  console.log('   2. Network: Disable cache in DevTools settings');
  console.log('   3. Console: Look for "LOADED NEW FRONTEND" message');
  console.log('   4. Hard Reload: Ctrl+Shift+R (Win/Linux) or Cmd+Shift+R (Mac)');
  console.log('   5. Check response headers for Cache-Control directives\n');
});

// ============================================================================
// PROCESS HANDLERS
// ============================================================================
process.on('SIGINT', () => {
  console.log('\n\n👋 Server shutting down gracefully...');
  process.exit(0);
});

process.on('uncaughtException', (error) => {
  console.error('💥 Uncaught Exception:', error);
  process.exit(1);
});
