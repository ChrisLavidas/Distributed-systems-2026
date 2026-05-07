@echo off
cd /d "%~dp0"
echo === Compiling ===
if not exist bin mkdir bin
dir /s /b src\*.java > sources.txt
javac -d bin @sources.txt
del sources.txt
if %ERRORLEVEL% neq 0 (
    echo COMPILE FAILED
    pause
    exit /b 1
)
echo Compile OK
echo.

echo === Starting all components ===
echo Σειρα εκκινησης: SRG -> Reducer -> Worker1 -> Worker2 -> Master
echo.

start "SRG - port 6000"     cmd /k "cd /d %~dp0 && java -cp bin srg.SecureRandomGenerator 6000"
timeout /t 1 /nobreak >nul

start "Reducer - port 7000"  cmd /k "cd /d %~dp0 && java -cp bin reducer.Reducer 7000"
timeout /t 1 /nobreak >nul

start "Worker1 - port 5001"  cmd /k "cd /d %~dp0 && java -cp bin worker.WorkerNode 0 5001 localhost 6000 localhost 7000"
timeout /t 1 /nobreak >nul

start "Worker2 - port 5002"  cmd /k "cd /d %~dp0 && java -cp bin worker.WorkerNode 1 5002 localhost 6000 localhost 7000"
timeout /t 1 /nobreak >nul

start "Master - port 5000"   cmd /k "cd /d %~dp0 && java -cp bin master.Master 5000 5003 localhost 7000 localhost:5001 localhost:5002"

echo.
echo Ολα τα components εκκινησαν σε ξεχωριστα παραθυρα.
echo.
echo Android app: host=10.0.2.2  port=5000
echo.
pause
