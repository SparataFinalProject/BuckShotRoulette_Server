@echo off
rem Local test server launcher: Docker (MySQL + Redis) + Spring Boot server.
rem Needs a .env file in this folder (copy .env.example and fill DB_PASSWORD / JWT_SECRET).
title BuckShot Roulette Server

rem Korean log text: make the console and the Java server both use UTF-8
chcp 65001 >nul
set "JAVA_TOOL_OPTIONS=-Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8"

rem run from the folder this file is in
cd /d "%~dp0"

if not exist ".env" (
    echo [ERROR] .env not found. Copy .env.example to .env and fill DB_PASSWORD and JWT_SECRET.
    pause
    exit /b 1
)

rem ===== is the server already running? (port 8080) =====
netstat -ano | findstr ":8080 " | findstr "LISTENING" >nul
if not errorlevel 1 (
    echo [INFO] Port 8080 is already in use. The server seems to be running already.
    pause
    exit /b 1
)

rem ===== Docker Desktop =====
docker info >nul 2>&1
if errorlevel 1 (
    echo Starting Docker Desktop...
    if exist "%LOCALAPPDATA%\Programs\DockerDesktop\Docker Desktop.exe" start "" "%LOCALAPPDATA%\Programs\DockerDesktop\Docker Desktop.exe"
    if exist "%ProgramFiles%\Docker\Docker\Docker Desktop.exe" start "" "%ProgramFiles%\Docker\Docker\Docker Desktop.exe"
    set /a TRIES=0
    goto wait_docker
)
goto docker_ready

:wait_docker
docker info >nul 2>&1
if not errorlevel 1 goto docker_ready
set /a TRIES+=1
if %TRIES% GEQ 60 (
    echo [ERROR] Docker did not become ready within 3 minutes. Start Docker Desktop yourself and try again.
    pause
    exit /b 1
)
timeout /t 3 /nobreak >nul
goto wait_docker

:docker_ready
echo Starting MySQL / Redis containers...
docker compose up -d
if errorlevel 1 (
    echo [ERROR] docker compose failed.
    pause
    exit /b 1
)

echo Waiting for MySQL...
set /a TRIES=0

:wait_mysql
docker exec buckshot-mysql sh -c "mysqladmin ping -uroot -p$MYSQL_ROOT_PASSWORD --silent" >nul 2>&1
if not errorlevel 1 goto mysql_ready
set /a TRIES+=1
if %TRIES% GEQ 40 (
    echo [ERROR] MySQL is not ready. Check: docker logs buckshot-mysql
    pause
    exit /b 1
)
timeout /t 2 /nobreak >nul
goto wait_mysql

:mysql_ready
echo.
echo Starting the server. It is ready when you see "Started BuckShotRouletteServerApplication".
echo To stop it, press Ctrl+C in this window or close the window.
echo.
call "%~dp0gradlew.bat" bootRun --console=plain

echo.
echo The server has stopped.
pause
