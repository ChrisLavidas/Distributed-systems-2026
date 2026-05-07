@echo off
cd /d "%~dp0"
title Dummy Player
echo === Dummy Player ===
set /p PLAYER_ID="Player ID (π.χ. AN1234): "
set /p BALANCE="Αρχικό balance: "
java -cp bin player.DummyPlayer localhost 5000 5003 %PLAYER_ID% %BALANCE%
pause
