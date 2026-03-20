@echo off
setlocal
call "%~dp0prepare-web-launch.cmd"
if errorlevel 1 exit /b %errorlevel%
call "%~dp0mvnw.cmd" -pl web-app -am org.springframework.boot:spring-boot-maven-plugin:4.0.3:run
