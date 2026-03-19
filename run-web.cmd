@echo off
setlocal
call "%~dp0mvnw.cmd" -pl web-app -am spring-boot:run
