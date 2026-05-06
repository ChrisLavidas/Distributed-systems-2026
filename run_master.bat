@echo off
cd /d "%~dp0"
title Master - port 5000
echo === Master (port=5000, workers: 5001 5002) ===
java -cp bin master.Master 5000 localhost 7000 localhost:5001 localhost:5002
pause
