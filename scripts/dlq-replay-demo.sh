#!/usr/bin/env bash
# Reproducible failure -> DLQ -> replay demo (specs/004-observability/spec.md).
#
# Registers a Source/Event, a Target A that always succeeds and a Target B that fails until
# told to recover, ingresses one event, polls until Target A SUCCEEDED / Target B DEAD, replays
# Target B, and confirms it recovers. Exits 0 only if every step actually happened as expected.
#
# Requires: a running RelayHub instance (BASE_URL, default http://localhost:8080) and `node`
# (used only to run a tiny local echo/toggle target — no new project dependency; the same
# pattern used for manual verification throughout this project's development).
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
TARGET_PORT="${DEMO_TARGET_PORT:-9099}"
RUN_ID="$(date +%s)"
SOURCE_KEY="demo-source-${RUN_ID}"
TARGET_A_KEY="demo-target-a-${RUN_ID}"
TARGET_B_KEY="demo-target-b-${RUN_ID}"

TMP_DIR="$(mktemp -d)"
# Node (a native Windows binary under Git Bash) can't resolve bash's POSIX-style temp paths
# (e.g. /tmp/tmp.XXXX) the way bash itself does — cygpath converts to a path Node can actually
# open. On non-Windows this conversion is a no-op (cygpath absent, fall back to TMP_DIR as-is).
TMP_DIR_FOR_NODE="$TMP_DIR"
# tr '\' '/': Node accepts forward slashes in Windows paths too, which avoids having to
# JS-escape backslashes when this gets embedded as a string literal in the heredoc below.
command -v cygpath >/dev/null 2>&1 && TMP_DIR_FOR_NODE="$(cygpath -w "$TMP_DIR" | tr '\\' '/')"
TARGET_PID=""
cleanup() {
  [ -n "$TARGET_PID" ] && kill "$TARGET_PID" 2>/dev/null || true
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT

log() { echo "[dlq-replay-demo] $*"; }
fail() { echo "[dlq-replay-demo] FAIL: $*" >&2; exit 1; }

json_get() {
  # json_get '<json>' '<dotted.path>' — minimal JSON field extraction via node, no jq dependency.
  node -e '
    const data = JSON.parse(process.argv[1]);
    const path = process.argv[2].split(".");
    let value = data;
    for (const key of path) { value = value[key]; }
    process.stdout.write(String(value));
  ' "$1" "$2"
}

post_json() {
  curl -sS -X POST "$1" -H "Content-Type: application/json" -d "$2"
}

cat > "$TMP_DIR/target.js" <<EOF
const http = require('http');
const fs = require('fs');
const flagFile = '${TMP_DIR_FOR_NODE}/target-b-fixed.flag';
const server = http.createServer((req, res) => {
  let body = '';
  req.on('data', c => body += c);
  req.on('end', () => {
    if (req.url === '/webhook-a') {
      res.writeHead(200, {'Content-Type':'application/json'}); res.end('{"ok":true}');
    } else if (req.url === '/webhook-b') {
      if (fs.existsSync(flagFile)) {
        res.writeHead(200, {'Content-Type':'application/json'}); res.end('{"ok":true}');
      } else {
        res.writeHead(500, {'Content-Type':'application/json'}); res.end('{"error":"boom"}');
      }
    } else {
      res.writeHead(404); res.end();
    }
  });
});
server.listen(${TARGET_PORT}, () => console.log('demo target listening on ${TARGET_PORT}'));
EOF

node "$TMP_DIR/target.js" &
TARGET_PID=$!
sleep 1

log "registering Source/Event/Targets/Subscriptions (run ${RUN_ID})"
post_json "$BASE_URL/api/sources" "{\"key\":\"$SOURCE_KEY\",\"name\":\"Demo Source\",\"description\":\"dlq-replay-demo\"}" > /dev/null
post_json "$BASE_URL/api/sources/$SOURCE_KEY/events" '{
  "key":"customer-created","name":"Customer Created","description":"Customer created event",
  "resourceType":"customer","operation":"CREATED","resourceIdPath":"$.customerNo"
}' > /dev/null
post_json "$BASE_URL/api/targets" "{\"key\":\"$TARGET_A_KEY\",\"name\":\"Demo Target A\",\"description\":\"Always succeeds\",\"baseUrl\":\"http://localhost:${TARGET_PORT}\"}" > /dev/null
post_json "$BASE_URL/api/targets" "{\"key\":\"$TARGET_B_KEY\",\"name\":\"Demo Target B\",\"description\":\"Fails then recovers\",\"baseUrl\":\"http://localhost:${TARGET_PORT}\"}" > /dev/null
post_json "$BASE_URL/api/subscriptions" "{
  \"sourceKey\":\"$SOURCE_KEY\",\"sourceEventKey\":\"customer-created\",\"targetKey\":\"$TARGET_A_KEY\",
  \"name\":\"Sub A\",\"description\":\"Routes to A\",\"targetMethod\":\"POST\",\"targetPath\":\"/webhook-a\",
  \"targetPayloadTemplate\":\"{\\\"dealerId\\\":\\\"\${\$.customerNo}\\\"}\"
}" > /dev/null
post_json "$BASE_URL/api/subscriptions" "{
  \"sourceKey\":\"$SOURCE_KEY\",\"sourceEventKey\":\"customer-created\",\"targetKey\":\"$TARGET_B_KEY\",
  \"name\":\"Sub B\",\"description\":\"Routes to B\",\"targetMethod\":\"POST\",\"targetPath\":\"/webhook-b\",
  \"targetPayloadTemplate\":\"{\\\"dealerId\\\":\\\"\${\$.customerNo}\\\"}\"
}" > /dev/null

log "ingressing one event"
INGRESS_RESP=$(post_json "$BASE_URL/ingress/v1/$SOURCE_KEY/customer-created" '{"customerNo":"C90001","name":"Demo Dealer"}')
EVENT_ID=$(json_get "$INGRESS_RESP" "eventId")
[ -n "$EVENT_ID" ] && [ "$EVENT_ID" != "undefined" ] || fail "ingress did not return an eventId: $INGRESS_RESP"
log "event $EVENT_ID created, waiting for deliveries to reach a terminal state"

TARGET_A_ID=$(json_get "$(curl -sS "$BASE_URL/api/targets/$TARGET_A_KEY")" "id")
DELIVERY_B_ID=""
for i in $(seq 1 20); do
  DELIVERIES=$(curl -sS "$BASE_URL/api/deliveries?eventId=$EVENT_ID")
  COUNT=$(node -e "console.log(JSON.parse(process.argv[1]).length)" "$DELIVERIES")
  if [ "$COUNT" = "2" ]; then
    STATE_A=$(node -e "const d=JSON.parse(process.argv[1]); const a=d.find(x=>x.targetId==='$TARGET_A_ID'); console.log(a?a.state:'')" "$DELIVERIES")
    STATE_B_JSON=$(node -e "const d=JSON.parse(process.argv[1]); const b=d.find(x=>x.targetId!=='$TARGET_A_ID'); console.log(JSON.stringify(b||{}))" "$DELIVERIES")
    STATE_B=$(json_get "$STATE_B_JSON" "state")
    if [ "$STATE_A" = "SUCCEEDED" ] && [ "$STATE_B" = "DEAD" ]; then
      DELIVERY_B_ID=$(json_get "$STATE_B_JSON" "id")
      break
    fi
  fi
  sleep 1
done
[ -n "$DELIVERY_B_ID" ] || fail "Target A did not SUCCEED and/or Target B did not reach DEAD within timeout: $DELIVERIES"
log "confirmed: Target A SUCCEEDED, Target B DEAD (delivery $DELIVERY_B_ID)"

log "fixing Target B and replaying"
touch "$TMP_DIR/target-b-fixed.flag"
REPLAY_RESP=$(curl -sS -X POST "$BASE_URL/api/deliveries/$DELIVERY_B_ID/replay")
REPLAY_STATE=$(json_get "$REPLAY_RESP" "state")
[ "$REPLAY_STATE" = "SUCCEEDED" ] || fail "replay did not result in SUCCEEDED: $REPLAY_RESP"

log "PASS: failure -> DLQ -> replay demo completed successfully"
