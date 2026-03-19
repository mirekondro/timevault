@echo off
setlocal
call "%~dp0mvnw.cmd" -pl desktop-app -am javafx:run
