#!/usr/bin/env bash
# Modest concurrent-ingress load/reliability check (specs/004-observability/spec.md).
#
# Fires N concurrent ingress requests against a single always-succeeding Target and confirms
# every one eventually reaches a SUCCEEDED delivery. This is a reproducibility/regression check,
# not a throughput benchmark — see spec.md ("Deliberately out of scope").
#
# Requires: a running RelayHub instance (BASE_URL, default http://localhost:8080) and `node`
# (a tiny local echo target — no new project dependency).
# Env: LOAD_CONCURRENCY (default 20), LOAD_TIMEOUT_SECONDS (default 30).
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
TARGET_PORT="${LOAD_TARGET_PORT:-9098}"
CONCURRENCY="${LOAD_CONCURRENCY:-20}"
TIMEOUT_S="${LOAD_TIMEOUT_SECONDS:-30}"
RUN_ID="$(date +%s)"
SOURCE_KEY="load-source-${RUN_ID}"
TARGET_KEY="load-target-${RUN_ID}"

TMP_DIR="$(mktemp -d)"
TARGET_PID=""
cleanup() {
  [ -n "$TARGET_PID" ] && kill "$TARGET_PID" 2>/dev/null || true
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT

log() { echo "[load-check] $*"; }
fail() { echo "[load-check] FAIL: $*" >&2; exit 1; }

post_json() {
  curl -sS -X POST "$1" -H "Content-Type: application/json" -d "$2"
}

cat > "$TMP_DIR/target.js" <<EOF
const http = require('http');
http.createServer((req, res) => {
  let body = '';
  req.on('data', c => body += c);
  req.on('end', () => { res.writeHead(200, {'Content-Type':'application/json'}); res.end('{"ok":true}'); });
}).listen(${TARGET_PORT}, () => console.log('load target listening on ${TARGET_PORT}'));
EOF
node "$TMP_DIR/target.js" &
TARGET_PID=$!
sleep 1

log "registering Source/Event/Target/Subscription (run ${RUN_ID})"
post_json "$BASE_URL/api/sources" "{\"key\":\"$SOURCE_KEY\",\"name\":\"Load Source\",\"description\":\"load-check\"}" > /dev/null
post_json "$BASE_URL/api/sources/$SOURCE_KEY/events" '{
  "key":"customer-created","name":"Customer Created","description":"Customer created event",
  "resourceType":"customer","operation":"CREATED","resourceIdPath":"$.customerNo"
}' > /dev/null
post_json "$BASE_URL/api/targets" "{\"key\":\"$TARGET_KEY\",\"name\":\"Load Target\",\"description\":\"Always succeeds\",\"baseUrl\":\"http://localhost:${TARGET_PORT}\"}" > /dev/null
post_json "$BASE_URL/api/subscriptions" "{
  \"sourceKey\":\"$SOURCE_KEY\",\"sourceEventKey\":\"customer-created\",\"targetKey\":\"$TARGET_KEY\",
  \"name\":\"Load Sub\",\"description\":\"Routes to load target\",\"targetMethod\":\"POST\",\"targetPath\":\"/webhook\",
  \"targetPayloadTemplate\":\"{\\\"dealerId\\\":\\\"\${\$.customerNo}\\\"}\"
}" > /dev/null

log "firing $CONCURRENCY concurrent ingress requests"
START=$(date +%s%3N)
EVENT_IDS_FILE="$TMP_DIR/event_ids.txt"
# xargs -P (not a `&`/`wait` loop): plain background-job loops were observed to hang
# indefinitely under Git Bash on Windows with more than a handful of concurrent subshells —
# xargs's own process-pool management is more portable across shells/platforms.
seq 1 "$CONCURRENCY" | xargs -P "$CONCURRENCY" -I{} bash -c '
  RESP=$(curl -sS -X POST "$1/ingress/v1/$2/customer-created" -H "Content-Type: application/json" -d "{\"customerNo\":\"C-LOAD-$3\",\"name\":\"Load Dealer $3\"}")
  node -e "console.log(JSON.parse(process.argv[1]).eventId)" "$RESP"
' _ "$BASE_URL" "$SOURCE_KEY" {} > "$EVENT_IDS_FILE"
INGRESS_MS=$(( $(date +%s%3N) - START ))
FIRED=$(wc -l < "$EVENT_IDS_FILE" | tr -d ' ')
[ "$FIRED" = "$CONCURRENCY" ] || fail "expected $CONCURRENCY ingress responses, got $FIRED"
log "all $CONCURRENCY requests accepted in ${INGRESS_MS}ms"

log "polling until every event's delivery reaches SUCCEEDED (timeout ${TIMEOUT_S}s)"
DEADLINE=$(( $(date +%s) + TIMEOUT_S ))
PENDING_COUNT=$CONCURRENCY
while [ "$(date +%s)" -lt "$DEADLINE" ]; do
  PENDING_COUNT=0
  while read -r EID; do
    [ -z "$EID" ] && continue
    STATE=$(curl -sS "$BASE_URL/api/deliveries?eventId=$EID" | node -e "
      const d = JSON.parse(require('fs').readFileSync(0, 'utf8'));
      console.log(d.length > 0 && d.every(x => x.state === 'SUCCEEDED') ? 'DONE' : 'PENDING');
    ")
    [ "$STATE" = "PENDING" ] && PENDING_COUNT=$((PENDING_COUNT + 1))
  done < "$EVENT_IDS_FILE"
  [ "$PENDING_COUNT" -eq 0 ] && break
  sleep 1
done

[ "$PENDING_COUNT" -eq 0 ] || fail "$PENDING_COUNT/$CONCURRENCY events still not SUCCEEDED after ${TIMEOUT_S}s"

TOTAL_MS=$(( $(date +%s%3N) - START ))
log "PASS: all $CONCURRENCY concurrent ingress requests reached SUCCEEDED delivery within ${TOTAL_MS}ms total"
