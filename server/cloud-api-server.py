import base64
import hashlib
import hmac
import json
import os
import secrets
import sqlite3
from datetime import datetime
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse

DB_PATH = os.environ.get("EAT_RECORD_DB", "/opt/eat-record-cloud/eat_record_cloud.db")
HOST = os.environ.get("EAT_RECORD_HOST", "0.0.0.0")
PORT = int(os.environ.get("EAT_RECORD_PORT", "8787"))
PBKDF2_ROUNDS = 180000


def utc_now():
    return datetime.utcnow().isoformat(timespec="seconds") + "Z"


def conn():
    c = sqlite3.connect(DB_PATH, timeout=10)
    c.row_factory = sqlite3.Row
    return c


def id_allowed(value):
    s = str(value)
    if "4" not in s:
        return True
    return "1314" in s and "4" not in s.replace("1314", "")


def next_user_id(c):
    value = 10000
    while not id_allowed(value) or c.execute("select id from users where id=?", (value,)).fetchone():
        value += 1
    return value


def has_table(c, name):
    return c.execute("select name from sqlite_master where type='table' and name=?", (name,)).fetchone() is not None


def columns(c):
    return {row["name"] for row in c.execute("pragma table_info(users)").fetchall()}


def ensure_column(c, existing, name, sql):
    if name not in existing:
        c.execute(f"alter table users add column {name} {sql}")


def init_db():
    os.makedirs(os.path.dirname(DB_PATH), exist_ok=True)
    with conn() as c:
        if not has_table(c, "users"):
            c.execute(
                """
                create table users (
                    id integer primary key,
                    token text not null default '',
                    token2 text not null default '',
                    nickname text unique,
                    password_hash text not null default '',
                    install_id text not null default '',
                    install_id2 text not null default '',
                    token_updated_at text not null default '',
                    token2_updated_at text not null default '',
                    avatar_url text not null default '',
                    signature text not null default '',
                    gender text not null default '',
                    height_cm integer not null default 0,
                    weight_kg integer not null default 0,
                    meal_habit text not null default '',
                    dining_methods text not null default '',
                    meal_spend text not null default '',
                    cloud_data text not null default '',
                    cloud_data_hash text not null default '',
                    cloud_data_updated_at text not null default '',
                    wechat_bound integer not null default 0,
                    wechat_openid text not null default '',
                    created_at text not null,
                    updated_at text not null
                )
                """
            )
        existing = columns(c)
        ensure_column(c, existing, "token", "text not null default ''")
        ensure_column(c, existing, "token2", "text not null default ''")
        ensure_column(c, existing, "nickname", "text")
        ensure_column(c, existing, "password_hash", "text not null default ''")
        ensure_column(c, existing, "install_id", "text not null default ''")
        ensure_column(c, existing, "install_id2", "text not null default ''")
        ensure_column(c, existing, "token_updated_at", "text not null default ''")
        ensure_column(c, existing, "token2_updated_at", "text not null default ''")
        ensure_column(c, existing, "avatar_url", "text not null default ''")
        ensure_column(c, existing, "signature", "text not null default ''")
        ensure_column(c, existing, "gender", "text not null default ''")
        ensure_column(c, existing, "height_cm", "integer not null default 0")
        ensure_column(c, existing, "weight_kg", "integer not null default 0")
        ensure_column(c, existing, "meal_habit", "text not null default ''")
        ensure_column(c, existing, "dining_methods", "text not null default ''")
        ensure_column(c, existing, "meal_spend", "text not null default ''")
        ensure_column(c, existing, "cloud_data", "text not null default ''")
        ensure_column(c, existing, "cloud_data_hash", "text not null default ''")
        ensure_column(c, existing, "cloud_data_updated_at", "text not null default ''")
        ensure_column(c, existing, "wechat_bound", "integer not null default 0")
        ensure_column(c, existing, "wechat_openid", "text not null default ''")
        ensure_column(c, existing, "created_at", "text not null default ''")
        ensure_column(c, existing, "updated_at", "text not null default ''")
        c.execute("create unique index if not exists idx_users_nickname_unique on users(nickname) where nickname is not null and nickname != ''")
        c.execute("create index if not exists idx_users_token on users(token)")
        c.execute("create index if not exists idx_users_token2 on users(token2)")
        c.execute("create index if not exists idx_users_install_id on users(install_id)")
        c.execute("create index if not exists idx_users_install_id2 on users(install_id2)")


def hash_password(password):
    salt = secrets.token_hex(16)
    digest = hashlib.pbkdf2_hmac("sha256", password.encode("utf-8"), salt.encode("utf-8"), PBKDF2_ROUNDS)
    return f"pbkdf2_sha256${PBKDF2_ROUNDS}${salt}${digest.hex()}"


def verify_password(password, encoded):
    try:
        algo, rounds, salt, digest = (encoded or "").split("$", 3)
        if algo != "pbkdf2_sha256":
            return False
        check = hashlib.pbkdf2_hmac("sha256", password.encode("utf-8"), salt.encode("utf-8"), int(rounds)).hex()
        return hmac.compare_digest(check, digest)
    except Exception:
        return False


def user_json(row, token=None):
    methods = [item for item in (row["dining_methods"] or "").split(",") if item]
    return {
        "id": row["id"],
        "nickname": row["nickname"] or "",
        "token": token or row["token"] or row["token2"] or "",
        "avatarUrl": row["avatar_url"] or "",
        "signature": row["signature"] or "",
        "gender": row["gender"] or "",
        "heightCm": int(row["height_cm"] or 0),
        "weightKg": int(row["weight_kg"] or 0),
        "mealHabit": row["meal_habit"] or "",
        "diningMethods": methods,
        "mealSpend": row["meal_spend"] or "",
        "wechatBound": bool(row["wechat_bound"]),
        "createdAt": row["created_at"] or "",
        "updatedAt": row["updated_at"] or "",
        "cloudDataUpdatedAt": row["cloud_data_updated_at"] or "",
        "cloudDataHash": row["cloud_data_hash"] or "",
        "hasPassword": bool(row["password_hash"]),
    }


def request_token(headers):
    auth = headers.get("Authorization", "")
    if not auth.startswith("Bearer "):
        return ""
    token = auth[7:].strip()
    return token


def token_user(headers):
    token = request_token(headers)
    if not token:
        return None
    with conn() as c:
        return c.execute("select * from users where token=? or token2=?", (token, token)).fetchone()


def issue_session(c, row, install_id):
    install_id = str(install_id or "").strip()
    now = utc_now()
    token = secrets.token_urlsafe(24)
    slot = 1
    if install_id and row["install_id"] == install_id:
        slot = 1
        token = row["token"] or token
    elif install_id and row["install_id2"] == install_id:
        slot = 2
        token = row["token2"] or token
    elif not row["token"] or not row["install_id"]:
        slot = 1
    elif not row["token2"] or not row["install_id2"]:
        slot = 2
    else:
        first = row["token_updated_at"] or row["updated_at"] or row["created_at"] or ""
        second = row["token2_updated_at"] or row["updated_at"] or row["created_at"] or ""
        slot = 1 if first <= second else 2
    if slot == 1:
        c.execute(
            "update users set token=?, install_id=?, token_updated_at=?, updated_at=? where id=?",
            (token, install_id, now, now, row["id"]),
        )
    else:
        c.execute(
            "update users set token2=?, install_id2=?, token2_updated_at=?, updated_at=? where id=?",
            (token, install_id, now, now, row["id"]),
        )
    return token


def body_methods(value):
    if isinstance(value, list):
        return ",".join(str(v) for v in value if v)
    return str(value or "")


def duplicate_nickname(c, nickname, exclude_id=0):
    if not nickname:
        return None
    if exclude_id:
        return c.execute("select id from users where nickname=? and id!=?", (nickname, exclude_id)).fetchone()
    return c.execute("select id from users where nickname=?", (nickname,)).fetchone()


def ok(**kwargs):
    data = {"ok": True}
    data.update(kwargs)
    return data


def fail(message):
    return {"ok": False, "error": message}


class Handler(BaseHTTPRequestHandler):
        server_version = "EatRecordCloud/0.5.26"

    def log_message(self, fmt, *args):
        print(f"[{utc_now()}] {fmt % args}")

    def _send(self, payload, code=200):
        raw = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(raw)))
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Headers",
                         "Content-Type, Authorization, X-User-Id, X-Data-Hash, X-Client-Known-Data-Hash")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.end_headers()
        self.wfile.write(raw)

    def _read_json(self):
        length = int(self.headers.get("Content-Length", "0") or "0")
        if length <= 0:
            return {}
        raw = self.rfile.read(length).decode("utf-8")
        return json.loads(raw or "{}")

    def do_OPTIONS(self):
        self._send(ok())

    def do_GET(self):
        path = urlparse(self.path).path
        if path in ("/health", "/api/health"):
            self._send(ok(service="eat-record-cloud", time=utc_now()))
            return
        self._send(fail("not found"), 404)

    def do_POST(self):
        path = urlparse(self.path).path
        try:
            if path == "/api/users/data/upload-raw":
                self.data_upload_raw()
                return
            if path == "/api/users/data/download-raw":
                self.data_download_raw()
                return
            body = self._read_json()
            if path == "/api/users/check-nickname":
                self.check_nickname(body)
            elif path == "/api/users/allocate-id":
                self.allocate_id(body)
            elif path == "/api/users/profile":
                self.profile(body)
            elif path == "/api/users/set-password":
                self.set_password(body)
            elif path == "/api/users/login":
                self.login(body)
            elif path == "/api/users/delete":
                self.delete_account(body)
            elif path == "/api/users/data/upload":
                self.data_upload(body)
            elif path == "/api/users/data/download":
                self.data_download(body)
            else:
                self._send(fail("not found"), 404)
        except Exception as e:
            self._send(fail(str(e)))

    def check_nickname(self, body):
        nickname = str(body.get("nickname", "")).strip()
        exclude_id = int(body.get("excludeId") or 0)
        if not nickname:
            self._send(fail("昵称不能为空"))
            return
        with conn() as c:
            exists = duplicate_nickname(c, nickname, exclude_id)
        self._send(ok(available=exists is None, message="该用户名已被其他用户使用" if exists else "用户名可用"))

    def allocate_id(self, body):
        install_id = str(body.get("installId", "")).strip()
        nickname = str(body.get("nickname", "")).strip()
        if not nickname or nickname == "给自己起个名字":
            self._send(fail("请先设置昵称"))
            return
        with conn() as c:
            user = c.execute("select * from users where install_id=? or install_id2=?", (install_id, install_id)).fetchone() if install_id else None
            if user:
                if user["nickname"] != nickname:
                    if duplicate_nickname(c, nickname, user["id"]):
                        self._send(fail("该用户名已被其他用户使用"))
                        return
                    c.execute("update users set nickname=?, updated_at=? where id=?", (nickname, utc_now(), user["id"]))
                token = issue_session(c, user, install_id)
                user = c.execute("select * from users where id=?", (user["id"],)).fetchone()
                self._send(ok(user=user_json(user, token)))
                return
            if duplicate_nickname(c, nickname, 0):
                self._send(fail("该用户名已被其他用户使用"))
                return
            user_id = next_user_id(c)
            token = secrets.token_urlsafe(24)
            created = utc_now()
            c.execute(
                "insert into users (id,nickname,token,install_id,token_updated_at,created_at,updated_at) values (?,?,?,?,?,?,?)",
                (user_id, nickname, token, install_id, created, created, created),
            )
            user = c.execute("select * from users where id=?", (user_id,)).fetchone()
            self._send(ok(user=user_json(user, token)))

    def profile(self, body):
        user = token_user(self.headers)
        if not user:
            self._send(fail("未登录或登录已过期"))
            return
        if body.get("id") and str(body.get("id")) != str(user["id"]):
            self._send(fail("无权限操作"))
            return
        nickname = str(body.get("nickname", "")).strip()
        with conn() as c:
            if nickname and nickname != (user["nickname"] or "") and duplicate_nickname(c, nickname, user["id"]):
                self._send(fail("该用户名已被其他用户使用"))
                return
            c.execute(
                """
                update users set
                    nickname = case when ? != '' then ? else nickname end,
                    avatar_url=?,
                    signature=?,
                    gender=?,
                    height_cm=?,
                    weight_kg=?,
                    meal_habit=?,
                    dining_methods=?,
                    meal_spend=?,
                    updated_at=?
                where id=?
                """,
                (
                    nickname,
                    nickname,
                    str(body.get("avatarUrl", "")),
                    str(body.get("signature", "")),
                    str(body.get("gender", "")),
                    int(body.get("heightCm") or 0),
                    int(body.get("weightKg") or 0),
                    str(body.get("mealHabit", "")),
                    body_methods(body.get("diningMethods")),
                    str(body.get("mealSpend", "")),
                    utc_now(),
                    user["id"],
                ),
            )
            updated = c.execute("select * from users where id=?", (user["id"],)).fetchone()
            self._send(ok(user=user_json(updated, request_token(self.headers))))

    def set_password(self, body):
        user = token_user(self.headers)
        if not user:
            self._send(fail("未登录或登录已过期"))
            return
        if body.get("id") and str(body.get("id")) != str(user["id"]):
            self._send(fail("无权限操作"))
            return
        password = str(body.get("password", ""))
        old_password = str(body.get("oldPassword", ""))
        if len(password) < 4:
            self._send(fail("密码至少需要4个字符"))
            return
        with conn() as c:
            current = c.execute("select * from users where id=?", (user["id"],)).fetchone()
            if current["password_hash"]:
                if not old_password:
                    self._send(fail("请输入旧密码"))
                    return
                if not verify_password(old_password, current["password_hash"]):
                    self._send(fail("旧密码不正确"))
                    return
            c.execute("update users set password_hash=?, updated_at=? where id=?", (hash_password(password), utc_now(), user["id"]))
            updated = c.execute("select * from users where id=?", (user["id"],)).fetchone()
            self._send(ok(message="密码修改成功" if current["password_hash"] else "密码设置成功", user=user_json(updated, request_token(self.headers))))

    def login(self, body):
        account = str(body.get("account", "")).strip()
        password = str(body.get("password", ""))
        install_id = str(body.get("installId", "")).strip()
        if not account or not password:
            self._send(fail("请输入账号和密码"))
            return
        with conn() as c:
            user = c.execute("select * from users where id=?", (int(account),)).fetchone() if account.isdigit() else None
            if not user:
                user = c.execute("select * from users where nickname=?", (account,)).fetchone()
            if not user:
                self._send(fail("账号不存在"))
                return
            if not user["password_hash"]:
                self._send(fail("该账号尚未设置密码，请先使用原设备登录后设置密码"))
                return
            if not verify_password(password, user["password_hash"]):
                self._send(fail("密码错误"))
                return
            token = issue_session(c, user, install_id)
            user = c.execute("select * from users where id=?", (user["id"],)).fetchone()
            self._send(ok(user=user_json(user, token)))

    def delete_account(self, body):
        user = token_user(self.headers)
        if not user:
            self._send(fail("未登录或登录已过期"))
            return
        if body.get("id") and str(body.get("id")) != str(user["id"]):
            self._send(fail("无权限操作"))
            return
        with conn() as c:
            c.execute("delete from users where id=?", (user["id"],))
        self._send(ok(message="账号已注销"))

    def data_upload(self, body):
        user = token_user(self.headers)
        if not user:
            self._send(fail("未登录或登录已过期"))
            return
        if body.get("id") and str(body.get("id")) != str(user["id"]):
            self._send(fail("无权限操作"))
            return
        backup_data = str(body.get("backupData", "")).strip()
        if not backup_data:
            self._send(fail("同步数据为空"))
            return
        if len(backup_data) > 80 * 1024 * 1024:
            self._send(fail("同步数据太大，请先清理无用图片后再试"))
            return
        data_hash = str(body.get("dataHash", "")).strip().lower()
        if not data_hash:
            try:
                data_hash = hashlib.sha256(base64.b64decode(backup_data.encode("utf-8"), validate=True)).hexdigest()
            except Exception:
                data_hash = hashlib.sha256(backup_data.encode("utf-8")).hexdigest()
        client_known_hash = str(body.get("clientKnownDataHash", "")).strip().lower()
        self._store_cloud_backup(user, backup_data, data_hash, client_known_hash)

    def _store_cloud_backup(self, user, backup_data, data_hash, client_known_hash):
        updated = utc_now()
        with conn() as c:
            current = c.execute("select * from users where id=?", (user["id"],)).fetchone()
            current_hash = current["cloud_data_hash"] or ""
            if current_hash and current_hash == data_hash:
                self._send(ok(
                    message="云端已是最新，无需重复同步",
                    skipped=True,
                    dataHash=current_hash,
                    dataUpdatedAt=current["cloud_data_updated_at"] or "",
                    user=user_json(current, request_token(self.headers)),
                ))
                return
            if current_hash and client_known_hash and client_known_hash != current_hash:
                self._send(fail("云端已有其他设备的新数据，请先同步云端数据到本地"))
                return
            c.execute(
                "update users set cloud_data=?, cloud_data_hash=?, cloud_data_updated_at=?, updated_at=? where id=?",
                (backup_data, data_hash, updated, updated, user["id"]),
            )
            row = c.execute("select * from users where id=?", (user["id"],)).fetchone()
        self._send(ok(
            message="数据已同步到云端",
            dataHash=data_hash,
            dataUpdatedAt=updated,
            user=user_json(row, request_token(self.headers)),
        ))

    def data_upload_raw(self):
        user = token_user(self.headers)
        if not user:
            self._send(fail("未登录或登录已过期"), 401)
            return
        header_id = str(self.headers.get("X-User-Id", "")).strip()
        if header_id and header_id != str(user["id"]):
            self._send(fail("无权限操作"), 403)
            return
        length = int(self.headers.get("Content-Length", "0") or "0")
        if length <= 0:
            self._send(fail("同步数据为空"), 400)
            return
        if length > 72 * 1024 * 1024:
            self._send(fail("同步数据太大，请先清理无用图片后再试"), 413)
            return
        raw = self.rfile.read(length)
        if len(raw) != length:
            self._send(fail("同步数据接收不完整"), 400)
            return
        data_hash = str(self.headers.get("X-Data-Hash", "")).strip().lower()
        if not data_hash:
            data_hash = hashlib.sha256(raw).hexdigest()
        client_known_hash = str(self.headers.get("X-Client-Known-Data-Hash", "")).strip().lower()
        backup_data = base64.b64encode(raw).decode("ascii")
        self._store_cloud_backup(user, backup_data, data_hash, client_known_hash)

    def data_download(self, body):
        user = token_user(self.headers)
        if not user:
            self._send(fail("未登录或登录已过期"))
            return
        if body.get("id") and str(body.get("id")) != str(user["id"]):
            self._send(fail("无权限操作"))
            return
        with conn() as c:
            row = c.execute("select * from users where id=?", (user["id"],)).fetchone()
        data = row["cloud_data"] or ""
        metadata_only = bool(body.get("metadataOnly"))
        self._send(ok(
            backupData="" if metadata_only else data,
            hasData=bool(data),
            dataHash=row["cloud_data_hash"] or "",
            dataUpdatedAt=row["cloud_data_updated_at"] or "",
            message="云端暂时没有可同步的数据" if not data else "云端数据已读取",
            user=user_json(row, request_token(self.headers)),
        ))

    def data_download_raw(self):
        user = token_user(self.headers)
        if not user:
            self._send(fail("未登录或登录已过期"), 401)
            return
        header_id = str(self.headers.get("X-User-Id", "")).strip()
        if header_id and header_id != str(user["id"]):
            self._send(fail("无权限操作"), 403)
            return
        with conn() as c:
            row = c.execute("select * from users where id=?", (user["id"],)).fetchone()
        data = row["cloud_data"] or ""
        self.send_response(200 if data else 204)
        self.send_header("Content-Type", "application/octet-stream")
        self.send_header("X-Data-Hash", row["cloud_data_hash"] or "")
        self.send_header("X-Data-Updated-At", row["cloud_data_updated_at"] or "")
        self.send_header("X-Has-Data", "1" if data else "0")
        self.send_header("Access-Control-Allow-Origin", "*")
        if data:
            padding = 2 if data.endswith("==") else (1 if data.endswith("=") else 0)
            decoded_length = len(data) * 3 // 4 - padding
            self.send_header("Content-Length", str(decoded_length))
        else:
            self.send_header("Content-Length", "0")
        self.end_headers()
        if not data:
            return
        chunk_size = 1024 * 1024
        chunk_size -= chunk_size % 4
        for offset in range(0, len(data), chunk_size):
            chunk = data[offset:offset + chunk_size]
            self.wfile.write(base64.b64decode(chunk.encode("ascii")))


if __name__ == "__main__":
    init_db()
    print(f"eat-record-cloud listening on {HOST}:{PORT}")
    ThreadingHTTPServer((HOST, PORT), Handler).serve_forever()
