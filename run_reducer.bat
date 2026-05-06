@echo off
cd /d "%~dp0"
title Reducer - port 7000
echo === Reducer (port 7000) ===
java -cp bin reducer.Reducer 7000
pause
