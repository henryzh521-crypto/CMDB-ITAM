"""性能基线：10 万资产规模下的延迟预算与并发吞吐。数据量不足时跳过（避免在空库上得出误导性结论）。

预算可整体放宽：PERF_FACTOR=2 表示所有预算 × 2（较慢的测试机）。
"""
import os, statistics, time
from concurrent.futures import ThreadPoolExecutor
from lib.framework import suite, case, setup, check, skip
from lib.context import data

S = suite("performance", "性能基线", order=60)
F = float(os.environ.get("PERF_FACTOR", "1"))
MIN_ASSETS = int(os.environ.get("PERF_MIN_ASSETS", "50000"))


@setup(S)
def _setup(ctx):
    st, b = ctx.admin.get("/assets", params={"page": 1, "size": 1})
    total = (data(b) or {}).get("total", 0)
    if total < MIN_ASSETS:
        raise __import__("lib.framework", fromlist=["Skip"]).Skip(f"资产 {total} 台，少于 {MIN_ASSETS}，性能结论无意义（可用 tests/seed_scale.sql 灌数）")
    ctx.cache["perf_assets"] = total


def _measure(ctx, path, params=None, n=10):
    """测纯服务端延迟：遇到限流（429）先按 Retry-After 等待再重测该样本，不把等待时间计入延迟；
    样本间隔 80 ms，使压测账号保持在「每秒 20 次」的限流以内。"""
    c = ctx.admin
    for _ in range(5):   # 预热 5 发：套件首项的 JIT/池冷态不应计入稳态预算
        c.get(path, params=params)
    ts = []
    while len(ts) < n:
        time.sleep(0.08)
        t = time.perf_counter()
        st, _ = c.get(path, params=params, no_retry=True)
        ms = (time.perf_counter() - t) * 1000
        if st == 429:
            time.sleep(min(5.0, float(c.last.headers.get("Retry-After") or 1)))
            continue
        check(st == 200, f"{path} HTTP {st}")
        ts.append(ms)
    ts.sort()
    return statistics.median(ts), ts[int(len(ts) * 0.9) - 1]


BUDGETS = [
    ("PERF-01", "资产列表第 1 页", "/assets", {"page": 1, "size": 20}, 60),
    ("PERF-02", "资产列表深分页（第 4000 页）", "/assets", {"page": 4000, "size": 20}, 100),
    ("PERF-03", "资产关键字搜索", "/assets", {"page": 1, "size": 20, "keyword": "H800"}, 80),
    ("PERF-04", "全局搜索（IP 片段）", "/search", {"keyword": "10.1.2"}, 120),
    ("PERF-05", "工单列表", "/workorders", {"page": 1, "size": 20}, 60),
    ("PERF-06", "运营总览（缓存）", "/report/dashboard", None, 60),
    ("PERF-07", "容量-机房（缓存）", "/capacity/rooms", None, 60),
    ("PERF-08", "监控总览", "/monitoring/overview", None, 120),
]

for cid, title, path, params, budget in BUDGETS:
    def make(path=path, params=params, budget=budget):
        def fn(ctx):
            p50, p90 = _measure(ctx, path, params)
            check(p50 <= budget * F, f"p50 {p50:.0f} ms 超出预算 {budget * F:.0f} ms")
            return f"p50 {p50:.0f} ms / p90 {p90:.0f} ms（预算 {budget * F:.0f} ms）"
        return fn
    case(S, cid, f"{title} p50 ≤ {budget} ms", level="P1")(make())


@case(S, "PERF-20", "资产列表 20 路并发（10 个用户）吞吐 ≥ 80 req/s、无错误", level="P1")
def _(ctx):
    """多用户并发：单用户受「每秒 20 次」限流，压测用 10 个账号模拟真实多人访问"""
    ctx.role("R34-PERF", {"assets": "r"})
    users = [ctx.user(f"t34-perf{i}", ["R34-PERF"]) for i in range(10)]
    n = 300

    def one(i):
        st, _ = users[i % 10].get("/assets", params={"page": 1 + i % 50, "size": 20}, no_retry=True)
        return st
    # 前置触发监控总览的 60s 计数缓存回源（正则 COUNT 全表扫描约数秒）：否则压测中途撞上回源
    # 会让数据库被占住、吞吐骤降（同机实测 287 req/s → 60 req/s），测不出服务端真实吞吐
    ctx.admin.get("/monitoring/overview")
    with ThreadPoolExecutor(20) as ex:
        list(ex.map(one, range(20)))  # 预热
        t0 = time.perf_counter()
        codes = list(ex.map(one, range(n)))
    dt = time.perf_counter() - t0
    rps = n / dt
    errs = [c for c in codes if c != 200]
    check(not errs, f"{len(errs)} 次失败：{sorted(set(errs))}")
    check(rps >= 80 / F, f"吞吐 {rps:.0f} req/s")
    return f"{rps:.0f} req/s"


@case(S, "PERF-21", "20 条活跃 SSE 连接下资产列表 p50 ≤ 60 ms（推送常开不拖累查询）", level="P1")
def _(ctx):
    """v3.5：SSE 心跳线程与长连接不应挤占业务请求的处理线程 / 连接池"""
    import requests as rq
    tok = ctx.admin.s.cookies.get("access_token")
    check(bool(tok), "登录会话缺少 access_token cookie")
    streams = []
    try:
        for _ in range(20):
            r = rq.get(f"{ctx.base}/notifications/sse", params={"token": tok}, stream=True, timeout=(10, 120))
            check(r.status_code == 200, f"SSE 建连 HTTP {r.status_code}")
            streams.append(r)
            time.sleep(0.05)   # 避开限流突发额度
        p50, p90 = _measure(ctx, "/assets", {"page": 1, "size": 20})
        check(p50 <= 60 * F, f"p50 {p50:.0f} ms 超出预算 {60 * F:.0f} ms")
        return f"SSE×20 常开下 p50 {p50:.0f} ms / p90 {p90:.0f} ms"
    finally:
        for r in streams:
            r.close()
