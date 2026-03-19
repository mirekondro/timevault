@echo off
setlocal
call "%~dp0mvnw.cmd" -f "%~dp0desktop-app\pom.xml" javafx:run
