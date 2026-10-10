"""测试上下文：被测地址、管理员会话、测试角色 / 用户 / 资产 / 工单的创建，以及可选的数据库访问。

环境变量：
  BASE        后端 API 地址，默认 http://127.0.0.1:8081/api
  BASE2       第二个后端实例（集群套件用），可选
  UI_BASE     前端地址（浏览器套件用），默认 http://127.0.0.1:4173
  ADMIN_USER / ADMIN_PW   管理员账号口令（默认 admin / 环境变量必填）
  PGHOST PGPORT PGUSER PGPASSWORD PGDATABASE   可选：直连数据库做数据核对（缺省时相关用例跳过）
  PKG_DIR     安装包目录（部署检查套件用），可选
  SLOW=1      运行耗时较长的用例（集群定时任务等）
"""
import hashlib, itertools, json, os, random, shutil, subprocess, time, uuid
import requests

TEST_PREFIX = "t34"


def _pw(username):
    return "Pw#9" + hashlib.sha1(("dcim-test:" + username).encode()).hexdigest()[:10] + "Xy"


class Client:
    def __init__(self, base, ip=None):
        self.base = base.rstrip("/")
        self.s = requests.Session()
        self.ip = ip
        self.username = None

    def headers(self, extra=None):
        h = {}
        if self.ip:
            h["X-Forwarded-For"] = self.ip
        if extra:
            h.update(extra)
        return h

    def req(self, method, path, **kw):
        headers = self.headers(kw.pop("headers", None))
        url = path if path.startswith("http") else self.base + path
        no_retry = kw.pop("no_retry", False)
        timeout = kw.pop("timeout", 60)
        for attempt in range(4):
            r = self.s.request(method, url, headers=headers, timeout=timeout, **kw)
            # 像规范的调用方一样遵守限流：带 Retry-After 的 429 等待后重试（账号锁定等业务 429 不带该头，不重试）
            ra = r.headers.get("Retry-After")
            if r.status_code != 429 or not ra or no_retry or attempt == 3:
                break
            time.sleep(min(5.0, float(ra)))
        try:
            body = r.json()
        except ValueError:
            body = None
        self.last = r
        return r.status_code, body

    def get(self, p, **kw):
        return self.req("GET", p, **kw)

    def post(self, p, json=None, **kw):
        return self.req("POST", p, json=json, **kw)

    def put(self, p, json=None, **kw):
        return self.req("PUT", p, json=json, **kw)

    def delete(self, p, **kw):
        return self.req("DELETE", p, **kw)

    def login(self, username, password):
        self.username = username
        return self.post("/auth/login", {"username": username, "password": password})


def data(body):
    return (body or {}).get("data") if isinstance(body, dict) else None


def msg(body):
    return (body or {}).get("msg", "") if isinstance(body, dict) else ""


class Context:
    def __init__(self):
        self.base = os.environ.get("BASE", "http://127.0.0.1:8081/api").rstrip("/")
        self.base2 = os.environ.get("BASE2", "").rstrip("/")
        self.ui_base = os.environ.get("UI_BASE", "http://127.0.0.1:4173").rstrip("/")
        self.admin_user = os.environ.get("ADMIN_USER", "admin")
        self.admin_pw = os.environ.get("ADMIN_PW", "")
        self.pkg_dir = os.environ.get("PKG_DIR", "")
        self.slow = os.environ.get("SLOW", "") == "1"
        self.run_id = time.strftime("%m%d%H%M%S") + uuid.uuid4().hex[:4]
        self._ip_seq = itertools.count(random.randint(1, 200))
        self._admin = None
        self._users = {}
        self.cache = {}

    # ───────── 会话 ─────────
    def client(self, base=None, ip=None):
        return Client(base or self.base, ip)

    def ip(self):
        """取一个测试专用的公网段地址作为 X-Forwarded-For（后端只信任来自内网代理的 XFF，本机直连可用）"""
        n = next(self._ip_seq)
        net = ("203.0.113", "198.51.100")[(n // 250) % 2]   # RFC 5737 文档专用网段，不会与真实来源混淆
        return f"{net}.{n % 250 + 1}"

    @property
    def admin(self):
        if self._admin is None:
            if not self.admin_pw:
                raise RuntimeError("请设置环境变量 ADMIN_PW（管理员口令）")
            c = self.client(ip=self.ip())
            st, b = c.login(self.admin_user, self.admin_pw)
            if st != 200:
                raise RuntimeError(f"管理员登录失败：HTTP {st} {msg(b)}")
            self._admin = c
        return self._admin

    def version(self):
        st, b = self.client().get("/healthz")
        return (data(b) or {}).get("version", "")

    # ───────── 角色 / 用户 ─────────
    def role(self, code, modules, actions=None, scopes=None, fields=None, name=None):
        """创建或覆盖测试角色。modules: {"assets": "rw" | "r"}"""
        body = {
            "roleCode": code, "name": name or code, "description": "自动化测试角色",
            "modulePerms": {m: {"read": True, "write": "w" in v} for m, v in modules.items()},
            "dataScopes": {m: {"type": (scopes or {}).get(m, "ALL")} for m in modules},
            "actionPerms": {m: {"delete": False, "export": True, **((actions or {}).get(m, {}))} for m in modules},
            "fieldPerms": fields or {},
        }
        st, b = self.admin.put(f"/roles/{code}", body)
        if st == 404 or (st >= 400 and "不存在" in msg(b)):
            st, b = self.admin.post("/roles", body)
        if st != 200:
            raise RuntimeError(f"保存角色 {code} 失败：HTTP {st} {msg(b)}")
        return code

    def user(self, username, roles, department=None, fresh=False):
        """确保测试用户存在且只持有 roles，返回已登录的会话（每个用户固定一个来源 IP）"""
        key = (username, tuple(roles))
        if not fresh and key in self._users:
            return self._users[key]
        pw = _pw(username)
        tmp = _pw(username + ":tmp")
        st, b = self.admin.post("/users", {"username": username, "name": username, "password": tmp, "roleCodes": list(roles),
                                           "department": department})
        if st == 409 or "已存在" in msg(b):
            self.admin.put(f"/users/{username}", {"name": username, "password": tmp, "roleCodes": list(roles), "status": 1,
                                                  "department": department})
        self.admin.post(f"/users/{username}/unlock")
        c = self.client(ip=self.ip())
        st, b = c.login(username, tmp)
        if st != 200:
            raise RuntimeError(f"测试用户 {username} 登录失败：HTTP {st} {msg(b)}")
        st, b = c.put(f"/users/{username}/password", {"oldPassword": tmp, "newPassword": pw})
        if st != 200:
            raise RuntimeError(f"测试用户 {username} 改密失败：HTTP {st} {msg(b)}")
        c = self.client(ip=c.ip)
        st, b = c.login(username, pw)
        if st != 200:
            raise RuntimeError(f"测试用户 {username} 重新登录失败：HTTP {st} {msg(b)}")
        c.password = pw
        self._users[key] = c
        return c

    # ───────── 业务数据 ─────────
    def asset(self, client=None, **kw):
        c = client or self.admin
        body = {"name": f"{TEST_PREFIX}-asset", "sn": f"SN-{TEST_PREFIX}-{uuid.uuid4().hex[:10]}", "categoryCode": "SRV", **kw}
        st, b = c.post("/assets", body)
        if st != 200:
            raise RuntimeError(f"创建资产失败：HTTP {st} {msg(b)}")
        return data(b)["assetNo"]

    def workorder(self, client=None, wo_type="巡检", **kw):
        c = client or self.admin
        st, b = c.post("/workorders", {"woType": wo_type, "title": f"{TEST_PREFIX} 自动化测试工单", **kw})
        if st != 200:
            raise RuntimeError(f"创建工单失败：HTTP {st} {msg(b)}")
        return data(b)["woNo"]

    # ───────── 数据库（可选） ─────────
    def sql(self, query):
        """返回行列表（每行为字符串列表）；无 psql 或未配置 PGHOST 时返回 None"""
        if not os.environ.get("PGHOST") or not shutil.which("psql"):
            return None
        r = subprocess.run(["psql", "-At", "-F", "\t", "-c", query], capture_output=True, text=True, timeout=60)
        if r.returncode != 0:
            raise RuntimeError(r.stderr.strip())
        return [line.split("\t") for line in r.stdout.splitlines() if line]
