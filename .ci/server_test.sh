#!/bin/bash
# usage: server_test.sh <gradle-project> <run-dir>   – starts a dedicated server, stops it once loaded,
# fails if it never finishes loading or if any error mentions buildminions.
P=$1; RUN=$2
mkdir -p "$RUN" && echo "eula=true" > "$RUN/eula.txt"
./gradlew ":$P:runServer" --args="nogui" > "server-$P.log" 2>&1 &
for i in $(seq 1 150); do
  grep -q "Done (" "server-$P.log" && break
  grep -qE "BUILD FAILED|Crash report|Failed to start" "server-$P.log" && break
  sleep 2
done
sleep 4; pkill -f java || true; sleep 2
echo "---- $P"; grep -E "Done \(|ERROR|Exception|Caused by" "server-$P.log" | grep -v "server.properties\|NoSuchFileException\|at java.base" | head -25
grep -q "Done (" "server-$P.log" || { echo "SERVER DID NOT START"; tail -40 "server-$P.log"; exit 1; }
if grep -E "/ERROR\]" "server-$P.log" | grep -qi buildminions; then echo "MOD DATA ERRORS"; exit 1; fi
echo "SERVER OK ($P)"
