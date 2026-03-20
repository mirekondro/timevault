@echo off
setlocal
call "%~dp0mvnw.cmd" -q -DskipTests -pl shared-core -am install
if errorlevel 1 exit /b %errorlevel%
call "%~dp0mvnw.cmd" -f "%~dp0web-app\pom.xml" spring-boot:run
