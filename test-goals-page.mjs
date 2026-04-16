import puppeteer from 'puppeteer';
import fs from 'fs';
import path from 'path';

const COLOR_RESET = '\x1b[0m';
const COLOR_YELLOW = '\x1b[33m';
const COLOR_GREEN = '\x1b[32m';
const COLOR_RED = '\x1b[31m';

(async () => {
  let browser;
  try {
    console.log(`${COLOR_YELLOW}🧪 Starting FinTrack Goals Page Test${COLOR_RESET}`);
    
    browser = await puppeteer.launch({
      headless: true,
      args: ['--no-sandbox', '--disable-setuid-sandbox'],
    });

    const page = await browser.newPage();
    page.setDefaultTimeout(30000);

    console.log(`${COLOR_YELLOW}📍 Navigating to http://localhost:3000${COLOR_RESET}`);
    await page.goto('http://localhost:3000', { waitUntil: 'networkidle2' });
    
    // Wait for the app to render
    console.log(`${COLOR_YELLOW}⏳ Waiting for app to render...${COLOR_RESET}`);
    await new Promise(resolve => setTimeout(resolve, 2000));

    // Set authentication token in localStorage to bypass login
    console.log(`${COLOR_YELLOW}🔐 Setting authentication token...${COLOR_RESET}`);
    
    const authSuccess = await page.evaluate(() => {
      try {
        localStorage.setItem("token", "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ0ZXN0QGZpbnRyYWNrLmNvbSIsImV4cCI6OTk5OTk5OTk5OX0.test");
        localStorage.setItem("fintrack_user", JSON.stringify({
          id: 1,
          email: "test@fintrack.com",
          name: "Test User",
          role: "USER"
        }));
        return true;
      } catch (e) {
        return false;
      }
    });

    console.log(`${COLOR_GREEN}✓ Authentication token set${COLOR_RESET}`);

    // Navigate to goals page
    console.log(`${COLOR_YELLOW}📍 Navigating to goals page...${COLOR_RESET}`);
    await page.goto('http://localhost:3000?page=goals', { waitUntil: 'networkidle2' });
    await new Promise(resolve => setTimeout(resolve, 3000));

    // Take screenshot
    const screenshotPath = path.join(process.cwd(), 'temporary screenshots', 'test-goals-page-screenshot.png');
    const dir = path.dirname(screenshotPath);
    
    if (!fs.existsSync(dir)) {
      fs.mkdirSync(dir, { recursive: true });
    }

    console.log(`${COLOR_YELLOW}📸 Taking screenshot...${COLOR_RESET}`);
    await page.screenshot({ path: screenshotPath, fullPage: true });
    
    console.log(`${COLOR_GREEN}✓ Screenshot saved to ${screenshotPath}${COLOR_RESET}`);

    // Try to find the goal and budget forms in the DOM
    const formInfo = await page.evaluate(() => {
      const goalNameInput =document.body.innerHTML.includes('goal-name-input');
      const budgetNameInput = document.body.innerHTML.includes('budget-name-input');
      const goalBtn = document.body.innerHTML.includes('goal-create-btn');
      const budgetBtn = document.body.innerHTML.includes('budget-create-btn');

      return {
        goalFormExists: goalNameInput,
        budgetFormExists: budgetNameInput,
        goalButtonExists: goalBtn,
        budgetButtonExists: budgetBtn,
      };
    });

    console.log(`${COLOR_YELLOW}📋 Form Detection (HTML check):${COLOR_RESET}`);
    console.log(`   Goal Form: ${formInfo.goalFormExists ? COLOR_GREEN + '✓' + COLOR_RESET : COLOR_RED + '✗' + COLOR_RESET}`);
    console.log(`   Budget Form: ${formInfo.budgetFormExists ? COLOR_GREEN + '✓' + COLOR_RESET : COLOR_RED + '✗' + COLOR_RESET}`);
    console.log(`   Goal Button: ${formInfo.goalButtonExists ? COLOR_GREEN + '✓' + COLOR_RESET : COLOR_RED + '✗' + COLOR_RESET}`);
    console.log(`   Budget Button: ${formInfo.budgetButtonExists ? COLOR_GREEN + '✓' + COLOR_RESET : COLOR_RED + '✗' + COLOR_RESET}`);

    console.log(`${COLOR_GREEN}✓ Test complete!${COLOR_RESET}`);

  } catch (error) {
    console.error(`${COLOR_RED}❌ Error:${COLOR_RESET}`, error.message);
  } finally {
    if (browser) {
      await browser.close();
    }
  }
})();
