#!/usr/bin/env pwsh
# ============================================================================
# FinTrack Frontend - Verification & Fix Script (PowerShell)
# ============================================================================
# Usage: 
#   powershell -ExecutionPolicy Bypass -File fintrack-fix.ps1

Write-Host ""
Write-Host "================================================================================" -ForegroundColor Cyan
Write-Host " FINTRACK FRONTEND - VERIFICATION & FIX TOOL" -ForegroundColor Cyan
Write-Host "================================================================================" -ForegroundColor Cyan
Write-Host ""

# Configuration
$CORRECT_PATH = "c:\Fintrack\fintrack-frontend\index.html"
$WRONG_PATH1 = "c:\Fintrack\index.html"
$WRONG_PATH2 = "c:\Fintrack\temporary-live-index.html"

# ============================================================================
# STEP 1: CHECK FILES
# ============================================================================
Write-Host "[STEP 1] Checking for conflicting index.html files..." -ForegroundColor Green
Write-Host ""

# Check correct file
if (Test-Path $CORRECT_PATH) {
    $file = Get-Item $CORRECT_PATH
    Write-Host "✅ CORRECT:  $CORRECT_PATH" -ForegroundColor Green
    Write-Host "   Size: $($file.Length) bytes"
    Write-Host "   Modified: $($file.LastWriteTime)"
} else {
    Write-Host "❌ ERROR: $CORRECT_PATH NOT FOUND" -ForegroundColor Red
}

Write-Host ""

# Check wrong files
if (Test-Path $WRONG_PATH1) {
    Write-Host "❌ CONFLICT: $WRONG_PATH1" -ForegroundColor Red
    Write-Host "   ⚠️  This file should be DELETED" -ForegroundColor Yellow
} else {
    Write-Host "✅ CLEAN: $WRONG_PATH1 does not exist" -ForegroundColor Green
}

Write-Host ""

if (Test-Path $WRONG_PATH2) {
    Write-Host "❌ CONFLICT: $WRONG_PATH2" -ForegroundColor Red
    Write-Host "   ⚠️  This file should be DELETED" -ForegroundColor Yellow
} else {
    Write-Host "✅ CLEAN: $WRONG_PATH2 does not exist" -ForegroundColor Green
}

Write-Host ""

# ============================================================================
# STEP 2: OFFER TO DELETE CONFLICTING FILES
# ============================================================================
Write-Host "[STEP 2] Removing conflicting files..." -ForegroundColor Green
Write-Host ""

$deleteCount = 0

if (Test-Path $WRONG_PATH1) {
    Write-Host "Deleting: $WRONG_PATH1"
    try {
        Remove-Item -Path $WRONG_PATH1 -Force -ErrorAction Stop
        Write-Host "✅ Deleted successfully" -ForegroundColor Green
        $deleteCount++
    } catch {
        Write-Host "❌ Failed to delete: $_" -ForegroundColor Red
        Write-Host "   (May need administrator privileges)" -ForegroundColor Yellow
    }
}

if (Test-Path $WRONG_PATH2) {
    Write-Host "Deleting: $WRONG_PATH2"
    try {
        Remove-Item -Path $WRONG_PATH2 -Force -ErrorAction Stop
        Write-Host "✅ Deleted successfully" -ForegroundColor Green
        $deleteCount++
    } catch {
        Write-Host "❌ Failed to delete: $_" -ForegroundColor Red
        Write-Host "   (May need administrator privileges)" -ForegroundColor Yellow
    }
}

if ($deleteCount -eq 0) {
    Write-Host "✅ No conflicting files found - clean!" -ForegroundColor Green
}

Write-Host ""

# ============================================================================
# STEP 3: CHECK NODE PROCESS
# ============================================================================
Write-Host "[STEP 3] Checking for running Node processes..." -ForegroundColor Green
Write-Host ""

$nodeProcesses = Get-Process -Name node -ErrorAction SilentlyContinue

if ($nodeProcesses) {
    Write-Host "⚠️  Node processes found:" -ForegroundColor Yellow
    $nodeProcesses | Format-Table Name, Id, PM -AutoSize
    Write-Host ""
    Write-Host "To kill all Node processes, run:" -ForegroundColor Cyan
    Write-Host '  taskkill /F /IM node.exe' -ForegroundColor White
} else {
    Write-Host "✅ No Node processes running" -ForegroundColor Green
}

Write-Host ""

# ============================================================================
# STEP 4: CHECK PORT 3000
# ============================================================================
Write-Host "[STEP 4] Checking if port 3000 is available..." -ForegroundColor Green
Write-Host ""

$portCheck = Get-NetTCPConnection -LocalPort 3000 -ErrorAction SilentlyContinue

if ($portCheck) {
    Write-Host "⚠️  Port 3000 is already in use" -ForegroundColor Yellow
    $portCheck | Format-Table LocalAddress, LocalPort, State, OwningProcess -AutoSize
} else {
    Write-Host "✅ Port 3000 is available" -ForegroundColor Green
}

Write-Host ""

# ============================================================================
# STEP 5: VERIFY INDEX.HTML CONTENT
# ============================================================================
Write-Host "[STEP 5] Verifying index.html content..." -ForegroundColor Green
Write-Host ""

if (Test-Path $CORRECT_PATH) {
    $content = Get-Content $CORRECT_PATH -Raw
    
    if ($content -match "handleLogin") {
        Write-Host "✅ Found: handleLogin function" -ForegroundColor Green
    } else {
        Write-Host "❌ Missing: handleLogin function" -ForegroundColor Red
    }
    
    if ($content -match "handlePdfUpload") {
        Write-Host "✅ Found: handlePdfUpload function" -ForegroundColor Green
    } else {
        Write-Host "❌ Missing: handlePdfUpload function" -ForegroundColor Red
    }
    
    if ($content -match "dashboard") {
        Write-Host "✅ Found: dashboard function" -ForegroundColor Green
    } else {
        Write-Host "❌ Missing: dashboard function" -ForegroundColor Red
    }
} else {
    Write-Host "❌ Cannot verify - index.html not found at $CORRECT_PATH" -ForegroundColor Red
}

Write-Host ""

# ============================================================================
# STEP 6: SUMMARY
# ============================================================================
Write-Host "[SUMMARY] Next Steps:" -ForegroundColor Cyan
Write-Host ""
Write-Host "1. Kill all Node processes:" -ForegroundColor White
Write-Host "   taskkill /F /IM node.exe" -ForegroundColor Gray
Write-Host ""
Write-Host "2. Replace serve.mjs with the fixed version (provided separately)" -ForegroundColor White
Write-Host ""
Write-Host "3. Start fresh server:" -ForegroundColor White
Write-Host "   cd c:\Fintrack" -ForegroundColor Gray
Write-Host "   node serve.mjs" -ForegroundColor Gray
Write-Host ""
Write-Host "4. In browser:" -ForegroundColor White
Write-Host "   - Hard reload: Ctrl+Shift+R" -ForegroundColor Gray
Write-Host "   - Open DevTools: F12" -ForegroundColor Gray
Write-Host "   - Network tab → Disable cache checkbox" -ForegroundColor Gray
Write-Host "   - Console → Look for '✓ LOADED NEW FRONTEND'" -ForegroundColor Gray
Write-Host ""
Write-Host "5. Verify in DevTools:" -ForegroundColor White
Write-Host "   - Network tab shows Cache-Control headers" -ForegroundColor Gray
Write-Host "   - Status is 200, not 304" -ForegroundColor Gray
Write-Host "   - File size matches server logs" -ForegroundColor Gray
Write-Host ""
Write-Host "================================================================================" -ForegroundColor Cyan
Write-Host ""

# Pause at end
Write-Host "Press any key to exit..." -ForegroundColor Gray
$null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
