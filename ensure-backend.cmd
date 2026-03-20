@echo off
setlocal

call "%~dp0mvnw.cmd" -q -DskipTests -pl web-app -am compile
if errorlevel 1 exit /b %errorlevel%

call :healthcheck
if not errorlevel 1 (
    echo TimeVault backend is already running on http://localhost:8081.
    exit /b 0
)

for /f "tokens=5" %%P in ('netstat -ano ^| findstr LISTENING ^| findstr :8081') do (
    echo Port 8081 is busy but the backend health check failed. Stopping process %%P...
    taskkill /PID %%P /F >nul 2>&1
    timeout /t 1 /nobreak >nul
    goto :start_backend
)

:start_backend
echo Starting TimeVault backend in a new window...
start "TimeVault Web" cmd /k call "%~dp0run-web.cmd"

set /a TIMEVAULT_WAIT_ATTEMPTS=0
:wait_for_backend
call :healthcheck
if not errorlevel 1 (
echo TimeVault backend is ready.
    exit /b 0
)

set /a TIMEVAULT_WAIT_ATTEMPTS+=1
if %TIMEVAULT_WAIT_ATTEMPTS% geq 45 (
    echo TimeVault backend did not become ready on http://localhost:8081.
    exit /b 1
)

timeout /t 2 /nobreak >nul
goto :wait_for_backend

:healthcheck
powershell -NoProfile -ExecutionPolicy Bypass -Command "$ProgressPreference='SilentlyContinue'; try { $response = Invoke-WebRequest -UseBasicParsing -Uri 'http://localhost:8081/api/vault/health' -TimeoutSec 3; if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 300) { exit 0 } } catch { }; exit 1"
exit /b %errorlevel%
