@echo off
REM ===========================================
REM START WORKER NODE (Windows)
REM Cach dung: start-worker.bat node-1 192.168.1.100
REM ===========================================

setlocal enabledelayedexpansion

REM Tim file JAR
set JAR_FILE=..\target\migration-0.0.1-SNAPSHOT.jar
if not exist "%JAR_FILE%" (
    set JAR_FILE=migration-0.0.1-SNAPSHOT.jar
)
if not exist "%JAR_FILE%" (
    set JAR_FILE=migration.jar
)
if not exist "%JAR_FILE%" (
    echo ERROR: Khong tim thay file JAR
    pause
    exit /b 1
)

REM Lay IP cua may nay
for /f "tokens=2 delims=:" %%a in ('ipconfig ^| findstr /c:"IPv4"') do (
    set MY_IP=%%a
    goto :found_ip
)
:found_ip
set MY_IP=%MY_IP: =%

REM Tham so
set NODE_ID=%1
set COORDINATOR_IP=%2
set WORKER_PORT=%3

if "%NODE_ID%"=="" set NODE_ID=worker-%COMPUTERNAME%
if "%COORDINATOR_IP%"=="" set COORDINATOR_IP=192.168.1.100
if "%WORKER_PORT%"=="" set WORKER_PORT=8081

echo ==========================================
echo        CODE MIGRATION - WORKER
echo ==========================================
echo.
echo   Node ID     : %NODE_ID%
echo   My IP       : %MY_IP%
echo   My Port     : %WORKER_PORT%
echo   Coordinator : http://%COORDINATOR_IP%:8080
echo.
echo ==========================================
echo.
echo Dang ket noi den Coordinator...
echo.

java -jar "%JAR_FILE%" ^
  --spring.profiles.active=worker ^
  --server.port=%WORKER_PORT% ^
  --node.id=%NODE_ID% ^
  --node.host=%MY_IP% ^
  --node.coordinator-url=http://%COORDINATOR_IP%:8080

pause
