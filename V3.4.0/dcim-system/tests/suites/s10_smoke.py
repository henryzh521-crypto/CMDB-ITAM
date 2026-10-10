"""冒烟：服务可用、版本可识别、全部无参 GET 接口不出现 5xx。"""
from lib.framework import suite, case, check, skip
from lib.context import data, msg

S = suite("smoke", "冒烟", order=10)


@case(S, "SM-01", "健康检查返回 up 且报告后端版本", risk="部署后跑的不是目标版本", level="P0")
def _(ctx):
    st, b = ctx.client().get("/healthz")
    d = data(b) or {}
    check(st == 200 and d.get("status") in ("up", "degraded"), f"HTTP {st} status={d.get('status')}")
    check(bool(d.get("version")), "healthz 未返回 version（v3.3.0 及以前的后端）")
    expect = __import__("os").environ.get("EXPECT_VERSION")
    if expect:
        check(d["version"] == expect, f"运行版本 {d['version']}，期望 {expect}")
    return f"版本 {d['version']}"


@case(S, "SM-02", "匿名访问健康检查不返回 JVM / 连接池明细", risk="信息泄露", level="P1")
def _(ctx):
    c = ctx.client(ip=ctx.ip())
    st, b = c.get("/healthz")
    d = data(b) or {}
    check("jvmMemoryUsedMB" not in d or c.base.startswith("http://127.") or c.base.startswith("http://localhost"),
          "非本机匿名请求拿到了 JVM 明细")


@case(S, "SM-03", "管理员能登录并取得当前用户、模块、操作与字段权限", risk="登录链路", level="P0")
def _(ctx):
    st, b = ctx.admin.get("/auth/me")
    d = data(b) or {}
    check(st == 200 and d.get("isAdmin") is True, f"HTTP {st}")
    for k in ("modules", "actions", "fields", "dataScopes"):
        check(k in d, f"/auth/me 缺少 {k}")


@case(S, "SM-04", "OpenAPI 中全部无路径参数的 GET 接口不返回 5xx", risk="接口崩溃", level="P0")
def _(ctx):
    st, doc = ctx.admin.get("/openapi.json")
    check(st == 200 and isinstance(doc, dict), f"openapi.json HTTP {st}")
    ctx.cache["openapi"] = doc
    bad, degraded, n = [], [], 0
    skip_paths = ("/api/openapi.json", "/api/auth/logout", "/api/healthz")
    for path, ops in sorted(doc["paths"].items()):
        if "get" not in ops or "{" in path or path in skip_paths or path.endswith("/export") or path.endswith("/template") \
                or "download" in path or "/stream" in path or "/sse" in path:
            continue
        n += 1
        st, b = ctx.admin.get(path[len("/api"):])
        if st == 503:
            degraded.append(f"{path}（{msg(b)[:30]}）")   # 依赖未配置（如未接 Prometheus）属预期的降级
        elif st >= 500:
            bad.append(f"{path} → {st} {msg(b)[:60]}")
    check(not bad, f"{len(bad)}/{n} 个接口 5xx", "; ".join(bad[:8]))
    return f"{n} 个接口" + (f"；依赖未配置而降级 {len(degraded)} 个：{'、'.join(degraded[:3])}" if degraded else "")


@case(S, "SM-05", "未登录访问业务接口返回 401", risk="鉴权绕过", level="P0")
def _(ctx):
    c = ctx.client(ip=ctx.ip())
    for p in ("/assets", "/workorders", "/users", "/openapi.json"):
        st, _ = c.get(p)
        check(st == 401, f"{p} 未登录返回 {st}")
