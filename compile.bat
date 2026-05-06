@echo off
cd /d "%~dp0"
echo === Compiling all sources ===
if not exist bin mkdir bin
dir /s /b src\*.java > sources.txt
javac -d bin @sources.txt
if %ERRORLEVEL% neq 0 (
    echo.
    echo COMPILE FAILED
    pause
    exit /b 1
)
echo.
echo Compile OK
del sources.txt
pause
