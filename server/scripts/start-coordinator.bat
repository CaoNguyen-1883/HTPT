@echo off
REM ===========================================
REM START COORDINATOR (Windows)
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
    echo Vui long build truoc: mvn clean package -DskipTests
    pause
    exit /b 1
)

REM Lay IP
for /f "tokens=2 delims=:" %%a in ('ipconfig ^| findstr /c:"IPv4"') do (
    set MY_IP=%%a
    goto :found_ip
)
:found_ip
set MY_IP=%MY_IP: =%

set PORT=%1
if "%PORT%"=="" set PORT=8080

echo ==========================================
echo      CODE MIGRATION - COORDINATOR
echo ==========================================
echo.
echo   IP Address : %MY_IP%
echo   Port       : %PORT%
echo   Frontend   : http://%MY_IP%:%PORT%
echo.
echo ==========================================
echo.
echo Workers ket noi bang lenh:
echo   java -jar migration.jar --spring.profiles.active=worker ^
echo     --node.id=node-1 ^
echo     --node.host=^<WORKER_IP^> ^
echo     --node.coordinator-url=http://%MY_IP%:%PORT%
echo.
echo Dang khoi dong Coordinator...
echo.

java -jar "%JAR_FILE%" ^
  --spring.profiles.active=coordinator ^
  --server.port=%PORT%

pause
