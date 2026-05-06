#!/bin/bash
# ============================================================
# Compile & Run scripts for Παραδοτέο Β (πλήρες σύστημα)
# Run from: project root (the folder that contains src/ and android/)
#
# Backend components (Java):  Master, Workers, Reducer, SRG, Manager
# Frontend (Android):         see android/ folder — open in Android Studio
#
# The DummyPlayer is kept for testing/debugging, but the actual
# player-side frontend for Παραδοτέο Β is the Android app.
# ============================================================

SRC=src
BIN=bin
mkdir -p $BIN

echo "=== Compiling all sources ==="
find $SRC -name "*.java" > sources.txt
javac -d $BIN @sources.txt
if [ $? -ne 0 ]; then
  echo "COMPILE FAILED"
  exit 1
fi
echo "Compile OK"
echo ""
echo "=== Start components in this order ==="
echo ""
echo "1) Secure Random Generator:"
echo "   java -cp $BIN srg.SecureRandomGenerator 6000"
echo ""
echo "2) Reducer:"
echo "   java -cp $BIN reducer.Reducer 7000"
echo ""
echo "3) Worker 1 (id=0, port=5001):"
echo "   java -cp $BIN worker.WorkerNode 0 5001 localhost 6000 localhost 7000"
echo ""
echo "4) Worker 2 (id=1, port=5002):"
echo "   java -cp $BIN worker.WorkerNode 1 5002 localhost 6000 localhost 7000"
echo ""
echo "5) Master (port=5000, 2 workers):"
echo "   java -cp $BIN master.Master 5000 localhost 7000 localhost:5001 localhost:5002"
echo ""
echo "6) Manager console:"
echo "   java -cp $BIN manager.ManagerApp localhost 5000"
echo ""
echo "7) Dummy Player (για testing — το πραγματικό frontend είναι το Android app):"
echo "   java -cp $BIN player.DummyPlayer localhost 5000 AN1234 100"
echo ""
echo "8) Android app:"
echo "   Άνοιγμα του φακέλου ./android/ στο Android Studio,"
echo "   Gradle sync, και Run στον emulator (host=10.0.2.2, port=5000)."
