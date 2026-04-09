import puppeteer from 'puppeteer';
import fs from 'fs';
import path from 'path';

const url = process.argv[2] || 'http://localhost:3000';
const label = process.argv[3] || '';
const browserCandidates = [
  'C:\\Users\\nateh\\.cache\\puppeteer\\chrome\\win64-1382411\\chrome-win64\\chrome.exe',
  'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe',
  'C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe',
  'C:\\Users\\admin\\AppData\\Local\\Google\\Chrome\\Application\\chrome.exe',
  'C:\\Users\\admin\\AppData\\Local\\Microsoft\\Edge\\Application\\msedge.exe',
];

// Ensure directory exists
const screenshotDir = './temporary screenshots';
if (!fs.existsSync(screenshotDir)) {
  fs.mkdirSync(screenshotDir, { recursive: true });
}

// Find next screenshot number
function getNextScreenshotNum() {
  let num = 1;
  const files = fs.readdirSync(screenshotDir);
  files.forEach(file => {
    const match = file.match(/^screenshot-(\d+)/);
    if (match) {
      const n = parseInt(match[1], 10);
      if (n >= num) num = n + 1;
    }
  });
  return num;
}

const num = getNextScreenshotNum();
const filename = label ? `screenshot-${num}-${label}.png` : `screenshot-${num}.png`;
const filepath = path.join(screenshotDir, filename);
const executablePath = browserCandidates.find(candidate => fs.existsSync(candidate));

(async () => {
  let browser;
  try {
    if (!executablePath) {
      throw new Error('No supported Chrome/Edge executable was found for Puppeteer.');
    }
    browser = await puppeteer.launch({
      headless: 'new',
      executablePath,
      args: ['--no-sandbox', '--disable-setuid-sandbox'],
    });
    
    const page = await browser.newPage();
    await page.setViewport({ width: 1920, height: 1080 });
    await page.goto(url, { waitUntil: 'networkidle2', timeout: 30000 });
    
    // Wait for animations to settle
    await new Promise((resolve) => setTimeout(resolve, 1500));
    
    await page.screenshot({ path: filepath, fullPage: true });
    console.log(`✓ Screenshot saved: ${filepath}`);
  } catch (error) {
    console.error('Screenshot error:', error.message);
    process.exit(1);
  } finally {
    if (browser) await browser.close();
  }
})();
