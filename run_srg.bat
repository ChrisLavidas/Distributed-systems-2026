@echo off
cd /d "%~dp0"
title SRG - port 6000
echo === Secure Random Generator (port 6000) ===
java -cp bin srg.SecureRandomGenerator 6000
pause
