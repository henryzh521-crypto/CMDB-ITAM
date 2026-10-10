"""安全回归：v3.3 审查的越权 / 会话 / 枚举问题 + v3.4 的账号锁定、接口限流、审计完整性、Webhook SSRF。"""
import statistics, time, uuid
from concurrent.futures import ThreadPoolExecutor
import requests
from lib.framework import suite, case, setup, check, skip
from lib.context import data, msg

S = suite("security", "安全回归", order=40)


@setup(S)
def _setup(ctx):
    ctx.role("R34-SYS", {"system": "rw"})
    ctx.role("R34-VIEW", {"assets": "r", "workorders": "r"})
    ctx.role("R34-EXP", {"assets": "rw"})


@case(S, "SE-01", "系统管理写权限用户不能给自己授予管理员、不能重置 / 停用 admin", risk="纵向越权（v3.3 S2）", level="P0")
def _(ctx):
    hd = ctx.user("t34-sys", ["R34-SYS"])
    st, b = hd.put("/users/t34-sys", {"name": "t34-sys", "roleCodes": ["ADMIN"]})
    check(st == 403, f"自授 ADMIN HTTP {st}")
    st, b = hd.put(f"/users/{ctx.admin_user}", {"name": "系统管理员", "password": "Hacked#Pass-2026x"})
    check(st == 403, f"重置 admin 口令 HTTP {st}")
    st, b = hd.put(f"/users/{ctx.admin_user}", {"name": "系统管理员", "status": 0})
    check(st == 403, f"停用 admin HTTP {st}")


def _fresh_login(ctx, username, pw):
    c = ctx.client(ip=ctx.ip())
    st, b = c.login(username, pw)
    check(st == 200, f"登录 HTTP {st} {msg(b)}")
    return c


@case(S, "SE-02", "退出登录后旧 access token 立即失效", risk="会话无法吊销（v3.3 S4）", level="P0")
def _(ctx):
    u = ctx.user("t34-tok", ["R34-VIEW"])
    c = _fresh_login(ctx, "t34-tok", u.password)
    saved = c.s.cookies.get("access_token")
    c.post("/auth/logout")
    x = ctx.client()
    x.s.cookies.set("access_token", saved)
    st, _ = x.get("/auth/me")
    check(st == 401, f"HTTP {st}")


@case(S, "SE-03", "改密后该账号所有旧 access token 失效", level="P0")
def _(ctx):
    u = ctx.user("t34-tok2", ["R34-VIEW"], fresh=True)
    c = _fresh_login(ctx, "t34-tok2", u.password)
    saved = c.s.cookies.get("access_token")
    st, b = c.put("/users/t34-tok2/password", {"oldPassword": u.password, "newPassword": u.password + "b"})
    check(st == 200, f"改密 HTTP {st} {msg(b)}")
    x = ctx.client()
    x.s.cookies.set("access_token", saved)
    st, _ = x.get("/auth/me")
    check(st == 401, f"旧令牌 HTTP {st}")
    ctx.user("t34-tok2", ["R34-VIEW"], fresh=True)  # 复原口令


@case(S, "SE-04", "同一 refresh token 16 路并发只能换发一次", risk="令牌重放（v3.3 H1）", level="P0")
def _(ctx):
    u = ctx.user("t34-tok", ["R34-VIEW"])
    c = _fresh_login(ctx, "t34-tok", u.password)
    rt = c.s.cookies.get("refresh_token")

    def once(_):
        r = requests.post(ctx.base + "/auth/refresh", cookies={"refresh_token": rt}, headers={"X-Forwarded-For": ctx.ip()}, timeout=30)
        return 1 if r.cookies.get("refresh_token") else 0
    with ThreadPoolExecutor(16) as ex:
        issued = sum(ex.map(once, range(16)))
    check(issued <= 1, f"换发 {issued}/16 次")


@case(S, "SE-05", "登录耗时不暴露用户名是否存在", risk="用户名枚举（v3.3 H8）", level="P1")
def _(ctx):
    ip = ctx.ip()

    def t(user):
        c = ctx.client(ip=ip)
        s = time.perf_counter()
        c.login(user, "Wrong#Password-123")
        return time.perf_counter() - s
    for _ in range(2):
        t("t34-tok"), t("nouser_x")
    a = statistics.median([t("t34-tok") for _ in range(5)])
    ip = ctx.ip()
    n = statistics.median([t("nouser_" + uuid.uuid4().hex[:6]) for _ in range(5)])
    ctx.admin.post("/users/t34-tok/unlock")
    check(abs(a - n) < 0.04, f"存在 {a * 1000:.0f} ms / 不存在 {n * 1000:.0f} ms")
    return f"存在 {a * 1000:.0f} ms / 不存在 {n * 1000:.0f} ms"


@case(S, "SE-06", "账号跨 IP 失败 30 次即锁定：新 IP 被拒、可信 IP 不受影响、管理员可解锁", risk="分布式撞库（v3.4）", level="P0")
def _(ctx):
    u = ctx.user("t34-lock", ["R34-VIEW"], fresh=True)   # 本账号的固定 IP 登录成功过 → 可信 IP
    for i in range(30):
        ctx.client(ip=ctx.ip()).login("t34-lock", "Wrong#Password-123")
    st, b = ctx.client(ip=ctx.ip()).login("t34-lock", u.password)
    check(st == 429, f"锁定后新 IP 正确口令应被拒，实际 HTTP {st} {msg(b)}")
    st, b = ctx.client(ip=u.ip).login("t34-lock", u.password)
    check(st == 200, f"可信 IP 应能登录，实际 HTTP {st} {msg(b)}")
    st, b = ctx.admin.get("/users", params={"keyword": "t34-lock"})
    row = [r for r in data(b)["records"] if r["username"] == "t34-lock"][0]
    check(row.get("loginLocked") is True, f"用户列表 loginLocked={row.get('loginLocked')}")
    st, b = ctx.admin.post("/users/t34-lock/unlock")
    check(st == 200, f"解锁 HTTP {st}")
    st, b = ctx.client(ip=ctx.ip()).login("t34-lock", u.password)
    check(st == 200, f"解锁后新 IP 登录 HTTP {st} {msg(b)}")
    st, b = ctx.admin.get("/audit-logs", params={"page": 1, "size": 20, "action": "ACCOUNT_LOCKED", "objId": "t34-lock"})
    recs = (data(b) or {}).get("records") or []
    check(any(r.get("objId") == "t34-lock" or r.get("actor") == "t34-lock" for r in recs), "审计日志里没有 ACCOUNT_LOCKED 记录")


@case(S, "SE-07", "接口限流：同一用户瞬时请求超出突发上限返回 429 + Retry-After，稍后恢复", risk="接口被刷（v3.4）", level="P1")
def _(ctx):
    u = ctx.user("t34-rl", ["R34-VIEW"])

    def hit(_):
        r = u.s.get(ctx.base + "/auth/me", headers={"X-Forwarded-For": u.ip}, timeout=30)
        return r.status_code, r.headers.get("Retry-After")
    with ThreadPoolExecutor(20) as ex:
        res = list(ex.map(hit, range(120)))
    limited = [r for r in res if r[0] == 429]
    check(limited, f"120 次并发请求未触发限流（{[r[0] for r in res][:10]}…）")
    check(all(ra for _, ra in limited), "429 响应缺少 Retry-After")
    time.sleep(2.5)
    st, _ = u.get("/auth/me")
    check(st == 200, f"等待后仍被限流 HTTP {st}")
    return f"{len(limited)}/120 被限流"


@case(S, "SE-08", "重操作限流：同一用户每分钟导出超过 10 次返回 429", level="P2")
def _(ctx):
    u = ctx.user("t34-heavy", ["R34-EXP"], fresh=True)
    codes = []
    for _ in range(12):
        u.get("/assets/export", params={"assetNos": "NOT-EXIST"}, no_retry=True)
        codes.append(u.last.status_code)
    check(429 in codes, f"导出状态码 {codes}")
    check(codes[:10].count(200) == 10, f"前 10 次应成功：{codes}")


@case(S, "SE-09", "审计：新建类操作记录对象号，返回值能序列化", risk="审计缺对象号（v3.3）", level="P1")
def _(ctx):
    a = ctx.asset()
    st, b = ctx.admin.get("/audit-logs", params={"page": 1, "size": 20, "objId": a})
    recs = (data(b) or {}).get("records") or []
    mine = [r for r in recs if r.get("action") == "CREATE"]
    check(mine, f"审计里找不到 objId={a} 的 CREATE 记录")
    detail = str(mine[0].get("detail"))
    check("无法序列化" not in detail, f"审计 detail 含「无法序列化」：{detail[:120]}")


@case(S, "SE-10", "Webhook 不能指向平台内部的监控栈", risk="SSRF（v3.3 S5）", level="P0")
def _(ctx):
    st, meta = ctx.admin.get("/webhooks/meta")
    m = data(meta) or {}
    events = m.get("eventTypes") or m.get("events") or []
    ev = (events[0].get("code") or events[0].get("value") or events[0].get("key")) if events and isinstance(events[0], dict) else (events[0] if events else "workorder.created")
    for url in ("http://prometheus:9090/-/quit", "http://alertmanager:9093/api/v2/silences", "http://127.0.0.1:8081/api/healthz"):
        st, b = ctx.admin.post("/webhooks", {"name": "t34-ssrf", "url": url, "eventTypes": [ev], "enabled": False})
        check(st == 400, f"{url} HTTP {st} {msg(b)}")
