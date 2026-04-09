@echo off
REM ============================================================================
REM FinTrack Frontend - Quick Verification & Cleanup Script
REM ============================================================================
REM This script helps debug and fix the frontend serving issue

setlocal enabledelayedexpansion

echo.
echo ================================================================================
echo  FINTRACK FRONTEND - VERIFICATION & CLEANUP TOOL
echo ================================================================================
echo.

REM ============================================================================
REM 1. CHECK FOR CONFLICTING FILES
REM ============================================================================
echo [STEP 1] Checking for conflicting index.html files...
echo.

set "CORRECT_PATH=c:\Fintrack\fintrack-frontend\index.html"
set "WRONG_PATH1=c:\Fintrack\index.html"
set "WRONG_PATH2=c:\Fintrack\temporary-live-index.html"

if exist "!CORRECT_PATH!" (
    echo ✅ CORRECT:  Found at !CORRECT_PATH!
    for %%A in ("!CORRECT_PATH!") do (
        echo    Size: %%~zA bytes
        echo    Modified: %%~tA
    )
) else (
    echo ❌ ERROR: !CORRECT_PATH! NOT FOUND
)

echo.

if exist "!WRONG_PATH1!" (
    echo ❌ CONFLICT: Found at !WRONG_PATH1!
    echo    This file should be DELETED
    echo    Type: DELETE_1
) else (
    echo ✅ CLEAN:  !WRONG_PATH1! does not exist
)

echo.

if exist "!WRONG_PATH2!" (
    echo ❌ CONFLICT: Found at !WRONG_PATH2!
    echo    This file should be DELETED
    echo    Type: DELETE_2
) else (
    echo ✅ CLEAN:  !WRONG_PATH2! does not exist
)

echo.

REM ============================================================================
REM 2. OFFER TO DELETE CONFLICTING FILES
REM ============================================================================
echo [STEP 2] Removing conflicting files...
echo.

set "DELETE_NEEDED=0"

if exist "!WRONG_PATH1!" (
    set "DELETE_NEEDED=1"
    echo Deleting: !WRONG_PATH1!
    del /Q "!WRONG_PATH1!" 2>nul
    if !errorlevel! equ 0 (
        echo ✅ Deleted successfully
    ) else (
        echo ❌ Failed to delete (may need admin)
    )
)

if exist "!WRONG_PATH2!" (
    set "DELETE_NEEDED=1"
    echo Deleting: !WRONG_PATH2!
    del /Q "!WRONG_PATH2!" 2>nul
    if !errorlevel! equ 0 (
        echo ✅ Deleted successfully
    ) else (
        echo ❌ Failed to delete (may need admin)
    )
)

if !DELETE_NEEDED! equ 0 (
    echo ✅ No conflicting files found - clean!
)

echo.

REM ============================================================================
REM 3. CHECK NODE PROCESS
REM ============================================================================
echo [STEP 3] Checking for running Node processes...
echo.

tasklist | findstr /I "node.exe" >nul
if !errorlevel! equ 0 (
    echo ⚠️  Node processes found:
    tasklist | findstr /I "node.exe"
    echo.
    echo Close the Node server terminal first, then run:
    echo   taskkill /F /IM node.exe
) else (
    echo ✅ No Node processes running
)

echo.

REM ============================================================================
REM 4. CHECK PORT 3000
REM ============================================================================
echo [STEP 4] Checking if port 3000 is available...
echo.

netstat -ano | findstr ":3000" >nul
if !errorlevel! equ 0 (
    echo ⚠️  Port 3000 is already in use
    echo Processes using port 3000:
    for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":3000"') do (
        echo   PID: %%a
        tasklist | findstr "%%a"
    )
) else (
    echo ✅ Port 3000 is available
)

echo.

REM ============================================================================
REM 5. VERIFY INDEX.HTML CONTENT
REM ============================================================================
echo [STEP 5] Verifying index.html content...
echo.

if exist "!CORRECT_PATH!" (
    findstr /C:"handleLogin" "!CORRECT_PATH!" >nul
    if !errorlevel! equ 0 (
        echo ✅ Found: handleLogin function
    ) else (
        echo ❌ Missing: handleLogin function
    )
    
    findstr /C:"handlePdfUpload" "!CORRECT_PATH!" >nul
    if !errorlevel! equ 0 (
        echo ✅ Found: handlePdfUpload function
    ) else (
        echo ❌ Missing: handlePdfUpload function
    )
    
    findstr /C:"dashboard" "!CORRECT_PATH!" >nul
    if !errorlevel! equ 0 (
        echo ✅ Found: dashboard function
    ) else (
        echo ❌ Missing: dashboard function
    )
) else (
    echo ❌ Cannot verify - index.html not found at !CORRECT_PATH!
)

echo.

REM ============================================================================
REM 6. SUMMARY
REM ============================================================================
echo [SUMMARY] Next Steps:
echo.
echo 1. If conflicts were deleted, continue to step 2
echo    Otherwise, they may need admin privileges - run as Administrator
echo.
echo 2. Kill Node process:
echo    taskkill /F /IM node.exe
echo.
echo 3. Start fresh server:
echo    cd c:\Fintrack
echo    node serve.mjs
echo.
echo 4. In browser:
echo    - Hard reload: Ctrl+Shift+R
echo    - Open DevTools: F12
echo    - Go to Network tab, disable cache checkbox
echo    - Check console for "✓ LOADED NEW FRONTEND"
echo.
echo ================================================================================
echo.

pause
