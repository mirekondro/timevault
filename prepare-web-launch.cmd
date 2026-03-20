@echo off
setlocal

call "%~dp0mvnw.cmd" -q -DskipTests -pl web-app -am compile
if errorlevel 1 exit /b %errorlevel%

for /f "tokens=5" %%P in ('netstat -ano ^| findstr LISTENING ^| findstr :8081') do (
    echo Releasing port 8081 from process %%P...
    taskkill /PID %%P /F >nul 2>&1
    timeout /t 1 /nobreak >nul
    goto :done
)

:done
exit /b 0
