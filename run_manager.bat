@echo off
cd /d "%~dp0"
title Manager Console
echo === Manager Console ===
set /p MANAGER_ID="Manager ID (π.χ. AK1234): "
java -cp bin manager.ManagerApp localhost 5000 %MANAGER_ID%
pause
