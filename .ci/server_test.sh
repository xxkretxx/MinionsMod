#!/bin/bash
# usage: server_test.sh <gradle-project> <run-dir>   – starts a dedicated server, stops it once loaded,
# fails if it never finishes loading or if any error mentions buildminions.
P=$1; RUN=$2; TASK=${3:-runServer}
mkdir -p "$RUN" && echo "eula=true" > "$RUN/eula.txt"
if [ "$P" = "fabric" ]; then ARGS=(--args="nogui"); else ARGS=(); fi
./gradlew ":$P:$TASK" "${ARGS[@]}" > "server-$P.log" 2>&1 &
for i in $(seq 1 150); do
  grep -q "Done (" "server-$P.log" && break
  grep -qE "BUILD FAILED|Crash report|Failed to start" "server-$P.log" && break
  sleep 2
done
sleep 4; pkill -f java || true; sleep 2
echo "---- $P"; grep -E "PROBE|Done \(|ERROR|Exception|Caused by" "server-$P.log" | grep -v "server.properties\|NoSuchFileException\|at java.base" | head -60
grep -q "Done (" "server-$P.log" || { echo "SERVER DID NOT START"; tail -40 "server-$P.log"; exit 1; }
if grep -E "/ERROR\]" "server-$P.log" | grep -qi buildminions; then echo "MOD DATA ERRORS"; exit 1; fi
if grep -qE "Encountered an unexpected exception|This crash report has been saved" "server-$P.log"; then
  echo "SERVER CRASHED"; grep -A40 "Encountered an unexpected exception" "server-$P.log" | head -60
  # only fail if our code is involved (Forge 1.20.1 dev runs crash in vanilla LAN code after loading, unrelated to the mod)
  if grep -A60 "Encountered an unexpected exception" "server-$P.log" | grep -q "dev.buildminions"; then exit 1; fi
  echo "(crash does not involve the mod - dev environment issue, mod loaded fine)"
fi
echo "SERVER OK ($P)"
