@echo off
cd /d "%~dp0"
title Worker 2 - port 5002
echo === Worker 2 (id=1, port=5002) ===
java -cp bin worker.WorkerNode 1 5002 localhost 6000 localhost 7000
pause
