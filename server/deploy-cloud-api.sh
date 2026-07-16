#!/usr/bin/env bash
set -euo pipefail

APP_DIR="/opt/eat-record-cloud"
APP_PATH="$APP_DIR/app.py"
BACKUP_PATH="$APP_DIR/app.py.bak.$(date +%Y%m%d%H%M%S)"
OPENLIST_API="http://47.97.215.111:5244/api/fs/get"
REMOTE_APP="/lanzou/Myapp/eat record/0.5.21 (endness)/server/cloud-api-server.py"

mkdir -p "$APP_DIR"
cd "$APP_DIR"

if [ -f "$APP_PATH" ]; then
  cp "$APP_PATH" "$BACKUP_PATH"
  echo "Backed up current app.py to $BACKUP_PATH"
fi

python3 - <<'PY'
import json
import urllib.request

api = "http://47.97.215.111:5244/api/fs/get"
remote = "/lanzou/Myapp/eat record/0.5.21 (endness)/server/cloud-api-server.py"
req = urllib.request.Request(
    api,
    data=json.dumps({"path": remote}).encode("utf-8"),
    headers={"Content-Type": "application/json"},
)
with urllib.request.urlopen(req, timeout=30) as resp:
    payload = json.loads(resp.read().decode("utf-8"))
if payload.get("code") != 200:
    raise SystemExit("OpenList get failed: " + str(payload))
raw_url = payload["data"]["raw_url"]
with urllib.request.urlopen(raw_url, timeout=60) as resp:
    data = resp.read()
with open("/opt/eat-record-cloud/app.py", "wb") as f:
    f.write(data)
print("Downloaded cloud API app.py, bytes:", len(data))
PY

python3 -m py_compile "$APP_PATH"
systemctl daemon-reload
systemctl restart eat-record-cloud
sleep 1
systemctl --no-pager --full status eat-record-cloud | head -40
curl -fsS http://127.0.0.1:8787/health && echo
curl -fsS -X POST http://127.0.0.1:8787/api/users/check-nickname \
  -H 'Content-Type: application/json' \
  -d '{"nickname":"__deploy_probe__"}' && echo

echo "eat record cloud API deployed."
