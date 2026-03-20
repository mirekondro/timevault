@echo off
setlocal

call "%~dp0ensure-backend.cmd"
if errorlevel 1 exit /b %errorlevel%

call "%~dp0mvnw.cmd" -q -DskipTests -pl desktop-app -am compile
exit /b %errorlevel%
