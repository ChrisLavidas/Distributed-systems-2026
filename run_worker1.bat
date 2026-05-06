@echo off
cd /d "%~dp0"
title Worker 1 - port 5001
echo === Worker 1 (id=0, port=5001) ===
java -cp bin worker.WorkerNode 0 5001 localhost 6000 localhost 7000
pause
