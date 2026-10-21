#!/usr/bin/env bash
# Starts the dev server, runs the RCON setup commands, joins with the dev client, which saves
# run/screenshots/<shot>-1.png, <shot>-2.png, ... at the view points (see DevClientHooks), then stops the server.
#
# Usage: tools/dev-screenshot.sh <shot> "<x,y,z,yaw,pitch>[;<x,y,z,yaw,pitch>...]" <setup-file> [after-join-file]
# Env:   ACTIONS="tick:command;..." runs client actions instead of one shot per view point (see DevClientHooks);
#        their shots are run/screenshots/<name>.png.
# The setup file holds one server command per line (without leading slash).
set -u
cd "$(dirname "$0")/.."
export MSYS_NO_PATHCONV=1
if [ "${OS:-}" = Windows_NT ]; then export JAVA_HOME="${JAVA_HOME:-C:\\Program Files\\Eclipse Adoptium\\jdk-25.0.4.101-hotspot}"; fi
if [ "${OS:-}" = Windows_NT ]; then export TEMP='C:\jtmp' TMP='C:\jtmp'; fi

SHOT="${1:-shot}"
VIEWS="${2:-0.5,-60,6.5,180,15}"
SETUP="${3:-/dev/null}"
AFTER="${4:-/dev/null}"
rcon() { "$JAVA_HOME/bin/java" tools/Rcon.java 127.0.0.1 25599 ss "$@"; }

mkdir -p run/screenshots
rm -f run/screenshots/"$SHOT"-*.png
EXTRA=()
[ -n "${ACTIONS:-}" ] && EXTRA+=(-PdevActions="$ACTIONS")

( ./gradlew runServer -PdevScreenshot -PdevViews="$VIEWS" --no-daemon -q > run/runServer.out 2>&1; echo "EXIT=$?" >> run/runServer.out ) &
sleep 20
for _ in $(seq 1 100); do
  grep -q 'RCON running\|EXIT=' run/runServer.out 2>/dev/null && break
  sleep 3
done
if grep -q 'EXIT=' run/runServer.out; then echo "server did not start"; tail -40 run/runServer.out; exit 1; fi

mapfile -t SETUP_CMDS < <(grep -v '^\s*$' "$SETUP" | grep -v '^#')
if [ "${#SETUP_CMDS[@]}" -gt 0 ]; then rcon "${SETUP_CMDS[@]}" > run/rcon.out; fi

JOINS_BEFORE=$(grep -c 'joined the game' run/runServer.out)
( ./gradlew runClient -PdevScreenshot -PdevViews="$VIEWS" -PdevShot="$SHOT" "${EXTRA[@]}" --no-daemon -q > run/runClient.out 2>&1; echo "EXIT=$?" >> run/runClient.out ) &
for _ in $(seq 1 100); do
  [ "$(grep -c 'joined the game' run/runServer.out)" -gt "$JOINS_BEFORE" ] && break
  grep -q 'EXIT=' run/runClient.out 2>/dev/null && break
  sleep 2
done
mapfile -t AFTER_CMDS < <(grep -v '^\s*$' "$AFTER" | grep -v '^#')
if [ "${#AFTER_CMDS[@]}" -gt 0 ]; then rcon "${AFTER_CMDS[@]}" >> run/rcon.out; fi
for _ in $(seq 1 300); do
  grep -q 'EXIT=' run/runClient.out 2>/dev/null && break
  sleep 3
done
echo "client: $(grep 'EXIT=' run/runClient.out)"
ls -la run/screenshots | grep "$SHOT"
grep -E "Dev action|Dev: |Screenshot:" run/runClient.out | sed 's/^.*(skullsanctum) //' 
rcon "stop" | tail -1
wait
