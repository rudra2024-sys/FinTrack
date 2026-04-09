import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import url from 'node:url';

const __dirname = path.dirname(url.fileURLToPath(import.meta.url));

// ============================================================================
// CONFIGURATION: SINGLE SOURCE OF TRUTH
// ============================================================================
const FRONTEND_DIR = path.join(__dirname, 'fintrack-frontend');
const INDEX_HTML_PATH = path.join(FRONTEND_DIR, 'index.html');
const DEFAULT_PORT = Number.parseInt(process.env.PORT || '3000', 10);
const MAX_PORT_TRIES = 10;

const mimeTypes = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.mjs': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.gif': 'image/gif',
  '.ico': 'image/x-icon',
  '.txt': 'text/plain; charset=utf-8',
};

function noCacheHeaders() {
  return {
    'Cache-Control': 'no-store, no-cache, must-revalidate, max-age=0, proxy-revalidate',
    Pragma: 'no-cache',
    Expires: '0',
    'Surrogate-Control': 'no-store',
    'X-Content-Type-Options': 'nosniff',
    // Per-request ETag ensures the browser never "Not Modified" (304) this HTML.
    ETag: `W/"${Date.now()}"`,
    'Last-Modified': new Date().toUTCString(),
  };
}

function sendText(res, status, text) {
  res.writeHead(status, { 'Content-Type': 'text/plain; charset=utf-8', ...noCacheHeaders() });
  res.end(text);
}

function sendBuffer(res, status, buf, contentType) {
  res.writeHead(status, { 'Content-Type': contentType, ...noCacheHeaders() });
  res.end(buf);
}

function serveIndexHtml(res, logPrefix = '') {
  try {
    const htmlContent = fs.readFileSync(INDEX_HTML_PATH, 'utf-8'); // fresh read every request

    const hasLogin = htmlContent.includes('handleLogin');
    const hasPdfUpload = htmlContent.includes('handlePdfUpload');
    const hasDashboard = htmlContent.includes('dashboard');

    console.log(`${logPrefix}📄 index.html verification:`);
    console.log(`${logPrefix}   - handleLogin:     ${hasLogin ? '✅' : '❌'}`);
    console.log(`${logPrefix}   - handlePdfUpload: ${hasPdfUpload ? '✅' : '❌'}`);
    console.log(`${logPrefix}   - dashboard:       ${hasDashboard ? '✅' : '❌'}`);
    console.log(`${logPrefix}   ✓ LOADED NEW FRONTEND\n`);

    sendBuffer(res, 200, Buffer.from(htmlContent, 'utf-8'), mimeTypes['.html']);
  } catch (err) {
    console.error('❌ ERROR reading index.html:', err?.message ?? err);
    sendText(res, 500, 'Error loading frontend');
  }
}

function looksLikeFileRequest(p) {
  return path.posix.basename(p).includes('.') && !p.endsWith('.');
}

function sendFileFromFrontendDir(requestPath, res) {
  const normalizedPath = path.normalize(path.join(FRONTEND_DIR, requestPath));
  if (!normalizedPath.startsWith(FRONTEND_DIR)) {
    sendText(res, 403, 'Forbidden');
    return;
  }

  fs.stat(normalizedPath, (err, stats) => {
    if (err) {
      sendText(res, 404, 'Not found');
      return;
    }
    if (stats.isDirectory()) {
      // Directory request -> serve its index if present
      const idx = path.join(normalizedPath, 'index.html');
      fs.readFile(idx, (readErr, data) => {
        if (readErr) {
          sendText(res, 404, 'Not found');
          return;
        }
        sendBuffer(res, 200, data, mimeTypes['.html']);
      });
      return;
    }

    fs.readFile(normalizedPath, (readErr, data) => {
      if (readErr) {
        sendText(res, 404, 'Not found');
        return;
      }
      const ext = path.extname(normalizedPath).toLowerCase();
      sendBuffer(res, 200, data, mimeTypes[ext] || 'application/octet-stream');
    });
  });
}

// Startup validation + banner
console.log('='.repeat(80));
console.log('🚀 FINTRACK FRONTEND SERVER - DEBUG MODE');
console.log('='.repeat(80));
console.log(`Frontend Directory: ${FRONTEND_DIR}`);
console.log(`Index.html Path:   ${INDEX_HTML_PATH}`);
console.log(`File Exists:       ${fs.existsSync(INDEX_HTML_PATH) ? '✅ YES' : '❌ NO'}`);
if (!fs.existsSync(INDEX_HTML_PATH)) {
  console.error(`\n❌ CRITICAL ERROR: index.html not found at ${INDEX_HTML_PATH}\n`);
  process.exit(1);
}
const stats = fs.statSync(INDEX_HTML_PATH);
console.log(`File Size:        ${stats.size} bytes`);
console.log(`Last Modified:    ${stats.mtime.toISOString()}`);
console.log('='.repeat(80));
console.log('');

const server = http.createServer((req, res) => {
    const parsed = new URL(req.url || '/', `http://${req.headers.host}`);
    const requestPath = decodeURIComponent(parsed.pathname || '/');

    const timestamp = new Date().toISOString();
    console.log(`[${timestamp}] ${req.method} ${requestPath}`);

    if (requestPath === '/' || (!looksLikeFileRequest(requestPath) && !requestPath.startsWith('/api/'))) {
      serveIndexHtml(res, '   ');
      return;
    }

    // Serve assets ONLY from fintrack-frontend/ (no ambiguity with root index.html)
    sendFileFromFrontendDir(requestPath, res);
  });

function startServer(server, port) {
  server.listen(port, '0.0.0.0', () => {
    console.log(`✅ Server running on http://localhost:${port}`);
    console.log('📌 Press Ctrl+C to stop\n');
    console.log('🔧 DEBUG CHECKLIST:');
    console.log('   1. Browser: Open DevTools (F12)');
    console.log('   2. Network: Disable cache in DevTools');
    console.log('   3. Hard Reload: Ctrl+Shift+R');
    console.log('   4. Console: Look for "✓ LOADED NEW FRONTEND"\n');
  });
}

let currentPort = DEFAULT_PORT;
let attempts = 0;
server.on('error', (err) => {
  if (err?.code === 'EADDRINUSE' && attempts < MAX_PORT_TRIES) {
    attempts += 1;
    const nextPort = DEFAULT_PORT + attempts;
    console.error(`⚠️  Port ${currentPort} is in use. Trying ${nextPort}...`);
    currentPort = nextPort;
    startServer(server, currentPort);
    return;
  }
  console.error('❌ Server failed to start:', err?.message ?? err);
  process.exit(1);
});

startServer(server, currentPort);
