"""多实例（ADR-11）：两个后端实例共用同一数据库与 Redis 时，会话吊销、登录计数、限流、定时任务互斥是否在实例间一致。

需要 BASE2 指向第二个实例（与 BASE 同库同 Redis）。CL-06 需要 SLOW=1 且能直连数据库（PGHOST 等）。
v3.5 增补：CL-07（SSE 跨实例，需 Redis）、CL-08（低库存补货单跨实例幂等，需 ticket 模式）。
v3.5.2 增补：CL-09（PAT 吊销跨实例即时生效，需 Redis）。
"""
import time
import uuid
from concurrent.futures import ThreadPoolExecutor

import requests as rq

from lib.framework import suite, case, setup, check, Skip
from lib.context import data, msg

S = suite("cluster", "多实例一致性", order=80)


@setup(S)
def _setup(ctx):
    if not ctx.base2:
        raise Skip("未设置 BASE2（第二个后端实例）")
    st, _ = ctx.client(base=ctx.base2).get("/healthz")
    if st != 200:
        raise Skip(f"第二个实例不可用：HTTP {st}")
    ctx.role("R34-VIEW", {"assets": "r", "workorders": "r"})


def _b(ctx, ip=None):
    return ctx.client(base=ctx.base2, ip=ip or ctx.ip())


@case(S, "CL-01", "两个实例运行同一版本", level="P0")
def _(ctx):
    v1 = ctx.version()
    st, b = _b(ctx).get("/healthz")
    v2 = (data(b) or {}).get("version")
    check(v1 == v2, f"实例 A {v1} / 实例 B {v2}")


@case(S, "CL-02", "在实例 A 退出登录，实例 B 立即拒绝旧令牌", risk="会话吊销只在本机生效", level="P0")
def _(ctx):
    u = ctx.user("t34-cl", ["R34-VIEW"])
    a = ctx.client(ip=u.ip)
    a.login("t34-cl", u.password)
    tok = a.s.cookies.get("access_token")
    b = _b(ctx, u.ip)
    b.s.cookies.set("access_token", tok)
    st, _ = b.get("/auth/me")
    check(st == 200, f"退出前实例 B 应接受令牌，HTTP {st}")
    a.post("/auth/logout")
    st, _ = b.get("/auth/me")
    check(st == 401, f"退出后实例 B HTTP {st}")


@case(S, "CL-03", "管理员在实例 A 停用账号，该账号在实例 B 的会话立即失效", level="P0")
def _(ctx):
    u = ctx.user("t34-cl2", ["R34-VIEW"], fresh=True)
    b = _b(ctx, u.ip)
    b.login("t34-cl2", u.password)
    ctx.admin.put("/users/t34-cl2", {"name": "t34-cl2", "status": 0})
    st, _ = b.get("/auth/me")
    ctx.admin.put("/users/t34-cl2", {"name": "t34-cl2", "status": 1})
    check(st == 401, f"停用后实例 B HTTP {st}")


@case(S, "CL-04", "登录失败计数跨实例累计：A、B 各失败 15 次后账号锁定", risk="多实例各算各的额度", level="P0")
def _(ctx):
    u = ctx.user("t34-cl3", ["R34-VIEW"], fresh=True)
    for i in range(15):
        ctx.client(ip=ctx.ip()).login("t34-cl3", "Wrong#Password-123")
        _b(ctx).login("t34-cl3", "Wrong#Password-123")
    st, b = _b(ctx).login("t34-cl3", u.password)
    ctx.admin.post("/users/t34-cl3/unlock")
    check(st == 429, f"跨实例累计 30 次后新 IP 登录 HTTP {st} {msg(b)}")


@case(S, "CL-05", "同一用户的限流额度跨实例共享", level="P1")
def _(ctx):
    u = ctx.user("t34-cl4", ["R34-VIEW"])
    b = _b(ctx, u.ip)
    b.s.cookies.update(u.s.cookies)

    def hit(i):
        c = u if i % 2 == 0 else b
        st, _ = c.get("/auth/me", no_retry=True)
        return st
    t0 = time.perf_counter()
    with ThreadPoolExecutor(20) as ex:
        codes = list(ex.map(hit, range(120)))
    dt = time.perf_counter() - t0
    ok = codes.count(200)
    budget = 40 + 20 * dt + 10   # 突发 40 + 每秒 20 次补充 + 余量；不共享时两个实例合计约翻倍
    check(ok <= budget, f"两实例合计放行 {ok} 次（{dt:.1f} 秒内），超出共享额度 {budget:.0f}")
    return f"放行 {ok}/120，用时 {dt:.1f} 秒"


@case(S, "CL-06", "定时任务每周期只在一个实例上执行（job_lock.run_count）", risk="多实例重复派单 / 重复升级", level="P0")
def _(ctx):
    if not ctx.slow:
        raise Skip("耗时约 3 分钟，设置 SLOW=1 运行")
    rows = ctx.sql("SELECT name, run_count FROM job_lock WHERE name IN ('SlaEscalationService.scheduledRun','InspectionScheduler.tick')")
    if rows is None:
        raise Skip("未配置数据库直连（PGHOST 等）")
    before = {r[0]: int(r[1]) for r in rows}
    time.sleep(185)
    after = {r[0]: int(r[1]) for r in ctx.sql("SELECT name, run_count FROM job_lock WHERE name IN ('SlaEscalationService.scheduledRun','InspectionScheduler.tick')")}
    deltas = {k: after.get(k, 0) - before.get(k, 0) for k in after}
    # 两个任务的周期都是 60 秒：185 秒内单实例最多 4 次；两个实例若各跑一次会到 6~7 次
    check(all(0 < d <= 4 for d in deltas.values()), f"执行次数 {deltas}")
    return f"185 秒内执行次数 {deltas}"


@case(S, "CL-07", "SSE 跨实例：实例 A 产生的工单事件 3 秒内到达实例 B 的连接（Redis pub/sub）", risk="v3.5 跨实例推送缺失", level="P0")
def _(ctx):
    st, b = ctx.client().get("/healthz")
    if (data(b) or {}).get("redis") == "disabled":
        raise Skip("Redis 未接入（SSE 跨实例依赖 Redis pub/sub 中继）")
    tok = ctx.admin.s.cookies.get("access_token")
    check(bool(tok), "登录会话缺少 access_token cookie")
    with rq.get(f"{ctx.base2}/notifications/sse", params={"token": tok}, stream=True, timeout=(10, 15)) as s:
        check(s.status_code == 200, f"实例 B 建连 HTTP {s.status_code}")
        # iter_lines 不可重入（首个生成器被 GC 会关闭 urllib3 连接）：单生成器跨阶段消费
        lines = s.iter_lines(decode_unicode=True)
        head, t0 = "", time.time()
        for line in lines:
            if line:
                head = line
                break
            if time.time() - t0 > 8:
                break
        check("connected" in head, f"实例 B 建连首帧异常：{head!r}")
        t0 = time.time()
        st, b = ctx.admin.post("/workorders", {"woType": "巡检", "title": f"CL-07 跨实例 {ctx.run_id}"})
        check(st == 200, f"实例 A 建单 HTTP {st} {msg(b)}")
        wo = data(b)["woNo"]
        hit, elapsed = False, None
        for line in lines:
            elapsed = time.time() - t0
            if line and line.startswith("data:") and wo in line:
                hit = True
                break
            if elapsed > 10:
                break
        check(hit, "10s 内实例 B 的连接未收到该工单广播")
        check(elapsed is not None and elapsed <= 3.0, f"跨实例送达耗时 {elapsed:.1f}s，超过 3s 预算")
        return f"跨实例送达 {elapsed:.2f}s"


@case(S, "CL-08", "双实例并发出库触发低库存：全库仅生成一张补货工单（跨实例幂等）", risk="v3.5 联动跨实例重复建单", level="P0")
def _(ctx):
    st, b = ctx.admin.get("/materials/meta")
    unit = (((data(b) or {}).get("units")) or ["个"])[0]
    st, b = ctx.admin.get("/warehouses", params={"page": 1, "size": 1})
    whs = (data(b) or {}).get("records") or []
    if not whs:
        raise Skip("库中无仓库")
    wh = whs[0]["warehouseCode"]
    code = f"MAT-CL35-{uuid.uuid4().hex[:8].upper()}"
    st, b = ctx.admin.post("/materials", {"materialCode": code, "name": f"集群物料 {code}", "unit": unit, "safetyStock": 5})
    check(st == 200, f"创建物料 HTTP {st} {msg(b)}")
    st, b = ctx.admin.post("/stock-logs", {"logType": "IN", "materialCode": code, "qty": 10, "warehouseCode": wh})
    check(st == 200, f"入库 HTTP {st} {msg(b)}")

    ab = _b(ctx)
    st, b = ab.login(ctx.admin_user, ctx.admin_pw)
    check(st == 200, f"实例 B 管理员登录 HTTP {st} {msg(b)}")

    def out_via(c, qty):
        return c.post("/stock-logs", {"logType": "OUT", "materialCode": code, "qty": qty, "warehouseCode": wh,
                                      "remark": "CL-08 双实例触发"})[0]
    # 两实例几乎同时出库（6+4=10，恰好清空库存）：各自触发低库存联动
    with ThreadPoolExecutor(2) as ex:
        codes = list(ex.map(lambda a: out_via(a[0], a[1]), [(ctx.admin, 6), (ab, 4)]))
    check(codes.count(200) == 2, f"两实例出库状态码 {codes}")

    def open_tickets():
        st, b = ctx.admin.get("/workorders", params={"page": 1, "size": 50, "keyword": code})
        return [r for r in (data(b) or {}).get("records") or []
                if r.get("woType") == "补货" and r.get("status") in ("待处理", "待审批", "处理中")]
    rows = []
    for _ in range(15):   # 联动为提交后异步：行锁 + 开放单查重应把并发触发收敛为一张
        rows = open_tickets()
        if rows:
            break
        time.sleep(1.0)
    if not rows:
        raise Skip("未生成补货单（被测实例非 ticket 模式：-Ddcim.linkage.low-stock.mode=ticket）")
    time.sleep(3.0)   # 等两个异步任务都落地后再计数
    rows = open_tickets()
    check(len(rows) == 1, f"双实例并发触发后应只有 1 张未完结补货单，实际 {len(rows)}：{[r['woNo'] for r in rows]}")
    return f"补货单 {rows[0]['woNo']}，跨实例幂等成立"


@case(S, "CL-09", "PAT 吊销跨实例生效：实例 A 吊销后，已进实例 B 校验缓存的令牌 3 秒内被拒（Redis pub/sub）", risk="v3.5.2 前多实例吊销窗口最长 60s（P3-7）", level="P0")
def _(ctx):
    st, b = ctx.client().get("/healthz")
    if (data(b) or {}).get("redis") == "disabled":
        raise Skip("Redis 未接入（PAT 吊销广播依赖 Redis pub/sub）")
    st, b = ctx.admin.post("/tokens", {"name": f"CL-09 跨实例吊销 {ctx.run_id}", "readOnly": False, "expireDays": 1})
    check(st == 200, f"创建 PAT HTTP {st} {msg(b)}")
    tok = (data(b) or {}).get("token")
    tid = (data(b) or {}).get("id")
    check(bool(tok) and str(tok).startswith("dcim_pat_"), f"返回的令牌异常：{tok}")
    h = {"Authorization": f"Bearer {tok}"}
    b2 = _b(ctx)
    # 先在实例 B 用一次：把令牌写进其 60s 校验缓存（否则测不出广播失效的价值）
    st, _ = b2.get("/assets", params={"page": 1, "size": 1}, headers=h)
    check(st == 200, f"吊销前实例 B 应接受 PAT，HTTP {st}")
    # 实例 A 吊销 → 广播清各实例缓存；无广播时实例 B 的缓存要等 60s 才自然过期
    st, b = ctx.admin.delete(f"/tokens/{tid}")
    check(st == 200, f"实例 A 吊销 HTTP {st} {msg(b)}")
    rejected, elapsed = False, None
    t0 = time.time()
    while time.time() - t0 < 3.0:
        st, _ = b2.get("/assets", params={"page": 1, "size": 1}, headers=h)
        if st == 401:
            rejected, elapsed = True, time.time() - t0
            break
        time.sleep(0.2)
    check(rejected, "3s 内实例 B 仍在接受已吊销的 PAT（吊销广播未生效）")
    return f"吊销后实例 B {elapsed:.1f}s 内拒绝"
