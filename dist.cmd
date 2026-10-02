@echo off
setlocal enabledelayedexpansion

set JAR_NAME=app.jar
set DIST_DIR=dist

if "%~1"=="" goto :dist
if "%~1"=="build" goto :build
if "%~1"=="dist" goto :dist
if "%~1"=="run" goto :run
if "%~1"=="test" goto :test
if "%~1"=="clean" goto :clean
echo Unknown target: %~1
echo Usage: dist.cmd [build^|dist^|run^|test^|clean]
exit /b 1

:build
call mvnw.cmd clean package -DskipTests
exit /b %errorlevel%

:dist
call :build
if errorlevel 1 exit /b 1
if not exist "%DIST_DIR%" mkdir "%DIST_DIR%"
for %%f in (target\*.jar) do (
    echo %%~nxf | findstr /v /c:"-sources" /c:"-javadoc" /c:".original" >nul
    if not errorlevel 1 copy /y "%%f" "%DIST_DIR%\%JAR_NAME%" >nul
)
echo Built %DIST_DIR%\%JAR_NAME%
exit /b 0

:run
call :dist
if errorlevel 1 exit /b 1
java -jar "%DIST_DIR%\%JAR_NAME%"
exit /b %errorlevel%

:test
call mvnw.cmd test
exit /b %errorlevel%

:clean
call mvnw.cmd clean
if exist "%DIST_DIR%" rmdir /s /q "%DIST_DIR%"
exit /b 0
