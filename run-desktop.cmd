@echo off
setlocal
call "%~dp0prepare-desktop-launch.cmd"
if errorlevel 1 exit /b %errorlevel%
echo Desktop launch is prepared.
echo Start the VS Code "Run Desktop App" launch configuration to open the JavaFX app.
