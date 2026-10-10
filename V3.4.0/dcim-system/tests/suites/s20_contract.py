"""接口契约：OpenAPI 完整性、统一响应结构、入参校验与字段别名（含 v3.3.1 assetNos 问题的回归）。

v3.5 增补：CT-11~15 覆盖新事件目录（workorder.sla_escalated / inventory.diff）、
盘点差异联动工单、低库存联动（补货单 / 站内信）、SLA 升级事件投递与 SSE 端点帧契约。
"""
import json
import time
import uuid
from concurrent.futures import ThreadPoolExecutor

import requests as rq

from lib.framework import suite, case, check, skip
from lib.context import data, msg

S = suite("contract", "接口契约", order=20)

PUBLIC_OK = {"/api/healthz", "/api/auth/login", "/api/auth/refresh", "/api/auth/logout", "/api/auth/me", "/api/openapi.json"}


def _doc(ctx):
    if "openapi" not in ctx.cache:
        st, doc = ctx.admin.get("/openapi.json")
        check(st == 200, f"openapi.json HTTP {st}")
        ctx.cache["openapi"] = doc
    return ctx.cache["openapi"]


@case(S, "CT-01", "OpenAPI 文档结构有效、版本与后端一致、operationId 不重复", level="P1")
def _(ctx):
    doc = _doc(ctx)
    check(doc.get("openapi", "").startswith("3."), "openapi 版本字段")
    check(doc["info"]["version"] == ctx.version(), f"文档版本 {doc['info']['version']} 与后端 {ctx.version()} 不一致")
    ids = [op.get("operationId") for ops in doc["paths"].values() for op in ops.values()]
    dup = {i for i in ids if ids.count(i) > 1}
    check(not dup, f"重复 operationId：{sorted(dup)[:5]}")
    return f"{len(ids)} 个操作"


@case(S, "CT-02", "写接口都声明了所需权限（x-permission），例外须在白名单", risk="漏加权限注解", level="P0")
def _(ctx):
    doc = _doc(ctx)
    missing = []
    for path, ops in doc["paths"].items():
        for m, op in ops.items():
            if m in ("post", "put", "delete", "patch") and "x-permission" not in op and path not in PUBLIC_OK:
                missing.append(f"{m.upper()} {path}")
    # 这些接口在实现里自行判断归属（本人令牌、本人通知、企业微信回调等），记录在案
    allowed_prefix = ("/api/tokens", "/api/notifications", "/api/wecom", "/api/auth/", "/api/users/", "/api/catalog", "/api/collector",
                      "/api/monitoring/ingest", "/api/me", "/api/alerts/",
                      "/api/system/ldap",     # 方法内校验管理员
                      "/api/kb/")             # 知识库「有用 / 没用」反馈：登录即可
    unexplained = [x for x in missing if not x.split(" ", 1)[1].startswith(allowed_prefix)]
    check(not unexplained, f"{len(unexplained)} 个写接口未声明权限", "; ".join(unexplained[:10]))
    return f"白名单内 {len(missing) - len(unexplained)} 个"


@case(S, "CT-03", "工单建单入参别名都在文档中声明（relatedAssets 的 x-aliases）", level="P1")
def _(ctx):
    doc = _doc(ctx)
    schema = doc["components"]["schemas"].get("WorkorderController.CreateWoReq", {})
    aliases = schema.get("properties", {}).get("relatedAssets", {}).get("x-aliases", [])
    for a in ("assetNos", "assetNoList", "assetNo"):
        check(a in aliases, f"缺少别名 {a}")


@case(S, "CT-04", "统一响应结构 {code, msg, data}：成功 code=0，失败 code=HTTP 状态", level="P0")
def _(ctx):
    st, b = ctx.admin.get("/assets", params={"page": 1, "size": 1})
    check(st == 200 and b.get("code") == 0 and "data" in b and "msg" in b, f"成功响应 {st} {b and list(b.keys())}")
    st, b = ctx.admin.get("/assets/NOT-EXIST-ASSET-00000")
    check(st == 404 and b.get("code") == 404 and msg(b), f"404 响应 {st} {b}")


@case(S, "CT-05", "缺必填字段返回 400 且提示字段", level="P1")
def _(ctx):
    st, b = ctx.admin.post("/workorders", {"title": "缺类型"})
    check(st == 400 and ("类型" in msg(b) or "woType" in msg(b)), f"HTTP {st} {msg(b)}")
    st, b = ctx.admin.post("/assets", {"sn": "x"})
    check(st == 400, f"资产缺名称 HTTP {st} {msg(b)}")


WO_SHAPES = [
    ("relatedAssets 数组", lambda a, b: {"relatedAssets": [a, b]}, 2),
    ("assets 对象数组（网页端）", lambda a, b: {"assets": [{"assetNo": a}, {"assetNo": b}]}, 2),
    ("assetNos 数组", lambda a, b: {"assetNos": [a, b]}, 2),
    ("assetNoList 数组", lambda a, b: {"assetNoList": [a, b]}, 2),
    ("assetNos 逗号分隔字符串", lambda a, b: {"assetNos": f"{a},{b}"}, 2),
    ("assetNos 中文逗号", lambda a, b: {"assetNos": f"{a}，{b}"}, 2),
    ("assetNos 单个字符串", lambda a, b: {"assetNos": a}, 1),
    ("assetNos 对象数组", lambda a, b: {"assetNos": [{"assetNo": a}, {"assetNo": b}]}, 2),
    ("assets 字符串数组", lambda a, b: {"assets": [a, b]}, 2),
    ("assetNo 单数", lambda a, b: {"assetNo": a}, 1),
    ("asset_nos 下划线", lambda a, b: {"asset_nos": [a, b]}, 2),
]


@case(S, "CT-06", "工单关联资产：11 种常见写法全部落库（assetNos 回归）", risk="关联资产静默丢弃（v3.2 P0）", level="P0")
def _(ctx):
    a, b = ctx.asset(), ctx.asset()
    bad = []
    for name, body, n in WO_SHAPES:
        st, r = ctx.admin.post("/workorders", {"woType": "巡检", "title": f"契约 {name}", **body(a, b)})
        if st != 200:
            bad.append(f"{name}: HTTP {st} {msg(r)}")
            continue
        wo = data(r)["woNo"]
        st, d = ctx.admin.get(f"/workorders/{wo}")
        items = (data(d) or {}).get("items") or []
        if len(items) != n:
            bad.append(f"{name}: 行项目 {len(items)}≠{n}")
    check(not bad, "; ".join(bad))


@case(S, "CT-07", "认不出的资产字段返回 400，不静默丢弃", risk="静默丢弃", level="P0")
def _(ctx):
    a = ctx.asset()
    st, r = ctx.admin.post("/workorders", {"woType": "巡检", "title": "未知字段", "assetIdList": [a]})
    check(st == 400 and "assetIdList" in msg(r), f"HTTP {st} {msg(r)}")


@case(S, "CT-08", "PUT 更新工单可整体替换关联资产；已关闭工单返回 409", level="P1")
def _(ctx):
    a, b = ctx.asset(), ctx.asset()
    wo = ctx.workorder(relatedAssets=[a, b])
    st, r = ctx.admin.put(f"/workorders/{wo}", {"title": "改关联", "assetNos": [b]})
    check(st == 200, f"HTTP {st} {msg(r)}")
    st, d = ctx.admin.get(f"/workorders/{wo}")
    items = [i["assetNo"] for i in (data(d) or {}).get("items") or []]
    check(items == [b], f"行项目 {items}")
    check((data(d) or {}).get("relatedAssets") == b, f"relatedAssets={data(d).get('relatedAssets')}")
    ctx.admin.post(f"/workorders/{wo}/transitions", {"action": "开始处理", "opinion": "t"})
    ctx.admin.post(f"/workorders/{wo}/transitions", {"action": "关闭", "opinion": "t", "rootCause": "t"})
    st, r = ctx.admin.put(f"/workorders/{wo}", {"title": "已关闭", "assetNos": [a]})
    check(st == 409, f"已关闭工单 HTTP {st} {msg(r)}")


@case(S, "CT-09", "删除工单最后一条资产行后 relatedAssets 清空", risk="详情显示已删除资产（v3.2 起）", level="P1")
def _(ctx):
    a = ctx.asset()
    wo = ctx.workorder(relatedAssets=[a])
    st, d = ctx.admin.get(f"/workorders/{wo}")
    item = data(d)["items"][0]["itemNo"]
    ctx.admin.delete(f"/workorders/{wo}/items/{item}")
    st, d = ctx.admin.get(f"/workorders/{wo}")
    check(not data(d).get("relatedAssets"), f"relatedAssets={data(d).get('relatedAssets')!r}")


@case(S, "CT-10", "唯一键冲突返回 409 并指出字段", level="P1")
def _(ctx):
    sn = f"SN-DUP-{ctx.run_id}"
    ctx.asset(sn=sn)
    st, r = ctx.admin.post("/assets", {"name": "dup", "sn": sn, "categoryCode": "SRV"})
    check(st == 409, f"HTTP {st} {msg(r)}")


# ───────── v3.5：事件目录 / 联动工单 / SSE 契约 ─────────

def _first_unit(ctx):
    """从单位字典取一个合法计量单位（物料创建强校验单位字典，历史数据可能存有非法值）"""
    st, b = ctx.admin.get("/materials/meta")
    units = (data(b) or {}).get("units") or []
    if not units:
        skip("单位字典为空，取不到合法计量单位")
    return units[0]


def _first_warehouse(ctx):
    st, b = ctx.admin.get("/warehouses", params={"page": 1, "size": 1})
    recs = (data(b) or {}).get("records") or []
    if not recs:
        skip("库中无仓库，无法做出入库")
    return recs[0]["warehouseCode"]


def _material(ctx, safety=0, stock=0):
    """创建测试物料并按需入库，返回 (materialCode, warehouseCode)"""
    code = f"MAT-CT35-{uuid.uuid4().hex[:8].upper()}"
    wh = _first_warehouse(ctx)
    st, b = ctx.admin.post("/materials", {"materialCode": code, "name": f"契约物料 {code}",
                                          "unit": _first_unit(ctx), "safetyStock": safety})
    check(st == 200, f"创建物料 HTTP {st} {msg(b)}")
    if stock:
        st, b = ctx.admin.post("/stock-logs", {"logType": "IN", "materialCode": code, "qty": stock, "warehouseCode": wh})
        check(st == 200, f"物料入库 HTTP {st} {msg(b)}")
    return code, wh


@case(S, "CT-11", "v3.5 新事件 workorder.sla_escalated / inventory.diff 可被 Webhook 订阅，未知事件仍被拒",
      risk="新事件未登记目录，订阅即 400", level="P1")
def _(ctx):
    st, b = ctx.admin.post("/webhooks", {"name": f"t35-catalog-{ctx.run_id}", "url": "http://203.0.113.10:9/hook",
                                          "eventTypes": ["workorder.sla_escalated", "inventory.diff"], "enabled": True})
    check(st == 200, f"订阅两个新事件 HTTP {st} {msg(b)}")
    hid = data(b)["id"]
    try:
        st, b = ctx.admin.post("/webhooks", {"name": "t35-unknown-event", "url": "http://203.0.113.10:9/hook",
                                              "eventTypes": ["not.a-real-event"], "enabled": True})
        if st == 200:
            ctx.admin.delete(f"/webhooks/{data(b)['id']}")
        check(st == 400 and "not.a-real-event" in msg(b), f"未知事件应 400：HTTP {st} {msg(b)}")
        return "两个 v3.5 新事件均已登记事件目录"
    finally:
        ctx.admin.delete(f"/webhooks/{hid}")


@case(S, "CT-12", "物料盘点盘亏：完成任务自动生成「盘点差异」工单且明细正确", risk="盘点差异无人跟进（v3.5 联动核心）", level="P0")
def _(ctx):
    code, wh = _material(ctx, safety=0, stock=10)
    st, b = ctx.admin.post("/inventory-tasks", {"name": f"契约盘点 {ctx.run_id}", "inspector": ctx.admin_user,
                                                "targetType": "MATERIAL",
                                                "filter": {"materialCodes": [code], "warehouseCodes": [wh]}})
    check(st == 200, f"创建盘点任务 HTTP {st} {msg(b)}")
    task = data(b)
    check(task["totalCount"] == 1, f"任务应只圈定 1 个物料行，实际 {task['totalCount']}")
    st, b = ctx.admin.get(f"/inventory-tasks/{task['taskNo']}/items", params={"page": 1, "size": 10})
    items = (data(b) or {}).get("records") or []
    check(len(items) == 1, f"盘点明细 {len(items)} 行")
    check((items[0].get("expected") or {}).get("qty") == 10, f"账面快照 {items[0].get('expected')}")
    st, b = ctx.admin.put(f"/inventory-tasks/{task['taskNo']}/items/{items[0]['itemNo']}",
                          {"countedQty": 7, "diffNote": "契约测试盘亏"})
    check(st == 200 and (data(b) or {}).get("result") == "盘亏", f"提交实盘 HTTP {st} {(data(b) or {}).get('result')} {msg(b)}")
    st, b = ctx.admin.post(f"/inventory-tasks/{task['taskNo']}/complete")
    check(st == 200 and (data(b) or {}).get("diffCount") == 1, f"完成任务 HTTP {st} diffCount={(data(b) or {}).get('diffCount')}")
    st, b = ctx.admin.get("/workorders", params={"page": 1, "size": 20, "keyword": task["taskNo"]})
    recs = [r for r in (data(b) or {}).get("records") or [] if r.get("woType") == "盘点差异"]
    check(len(recs) == 1, f"应恰好生成 1 张「盘点差异」工单，实际 {len(recs)}")
    wo_no = recs[0]["woNo"]
    st, d = ctx.admin.get(f"/workorders/{wo_no}")
    d = data(d) or {}
    check(code in (d.get("relatedMaterials") or ""), f"差异工单未关联物料：{d.get('relatedMaterials')!r}")
    check("账面 10，实盘 7" in (d.get("description") or ""), "差异工单描述缺少正确的差额明细")
    return f"差异工单 {wo_no}（关联物料 {code}）"


def _open_restock_tickets(ctx, code):
    st, b = ctx.admin.get("/workorders", params={"page": 1, "size": 50, "keyword": code})
    return [r for r in (data(b) or {}).get("records") or []
            if r.get("woType") == "补货" and r.get("status") in ("待处理", "待审批", "处理中")]


@case(S, "CT-13", "低库存出库触发联动：ticket 模式自动建补货单且不重复；notify 模式仓库管理员收站内信",
      risk="低库存无人跟进 / 重复建单（v3.5 联动核心）", level="P0")
def _(ctx):
    code, wh = _material(ctx, safety=5, stock=10)
    # notify 模式的通知对象是 R-WAREHOUSE 角色成员，须在触发前就位
    st, b = ctx.admin.get("/roles", params={"page": 1, "size": 300})
    has_wh = any((r or {}).get("roleCode") == "R-WAREHOUSE" for r in (data(b) or {}).get("records") or [])
    watcher = ctx.user("t34-whnotify", ["R-WAREHOUSE"]) if has_wh else None
    st, b = ctx.admin.post("/stock-logs", {"logType": "OUT", "materialCode": code, "qty": 8,
                                           "warehouseCode": wh, "remark": "CT-13 低库存触发"})
    check(st == 200, f"出库 HTTP {st} {msg(b)}")

    ticket = None
    for _ in range(15):   # 联动在事务提交后异步执行，最多等 15s
        rows = _open_restock_tickets(ctx, code)
        if rows:
            ticket = rows[0]
            break
        time.sleep(1.0)
    if ticket is None:
        if watcher is None:
            skip("非 ticket 模式且库中无 R-WAREHOUSE 角色，notify 通知无从验证（ticket 模式请用 -Ddcim.linkage.low-stock.mode=ticket 启动被测实例）")
        hit = None
        for _ in range(10):
            st, b = watcher.get("/notifications/my", params={"page": 1, "size": 50})
            hit = next((n for n in (data(b) or {}).get("records") or []
                        if n.get("type") == "STOCK_LOW" and n.get("refId") == code), None)
            if hit:
                break
            time.sleep(1.0)
        check(hit is not None, "notify 模式：仓库管理员 10s 内未收到 STOCK_LOW 站内信")
        check(code in (hit.get("content") or ""), f"通知正文未提及物料：{hit.get('content')!r}")
        return "notify 模式：仓库管理员已收到低库存站内信"

    # ticket 模式：工单内容 + 幂等（再次出库仍只有一张未完结补货单）
    st, d = ctx.admin.get(f"/workorders/{ticket['woNo']}")
    d = data(d) or {}
    check(code in (d.get("relatedMaterials") or ""), f"补货单未关联物料：{d.get('relatedMaterials')!r}")
    check("建议补货 3" in (d.get("description") or ""), f"建议量应为 3（安全 5 − 库存 2）：{ (d.get('description') or '')[:80] }")
    st, b = ctx.admin.post("/stock-logs", {"logType": "OUT", "materialCode": code, "qty": 1,
                                           "warehouseCode": wh, "remark": "CT-13 重复触发"})
    check(st == 200, f"再次出库 HTTP {st} {msg(b)}")
    time.sleep(4.0)
    rows = _open_restock_tickets(ctx, code)
    check(len(rows) == 1, f"重复触发后应仍只有 1 张未完结补货单，实际 {len(rows)}")
    return f"ticket 模式：补货单 {ticket['woNo']}，幂等校验通过"


@case(S, "CT-14", "SLA 超时升级补发 workorder.sla_escalated 事件并进入 Webhook 发件箱", risk="升级只发站内信（v3.5 补全）", level="P1")
def _(ctx):
    wo_type, prio = "巡检", "高"
    st, b = ctx.admin.get("/workorders/sla-config")
    prev = next((r for r in (data(b) or []) if r.get("woType") == wo_type and r.get("priority") == prio), None)
    restore = {"responseMinutes": (prev or {}).get("responseMinutes"),
               "resolveMinutes": (prev or {}).get("resolveMinutes"),
               "escalateTo": (prev or {}).get("escalateTo")}
    st, b = ctx.admin.put(f"/workorders/sla-config/{wo_type}/{prio}", {"responseMinutes": 1, "resolveMinutes": 1})
    check(st == 200, f"收紧 SLA 时限 HTTP {st} {msg(b)}")
    hid, wo = None, None
    try:
        st, b = ctx.admin.post("/webhooks", {"name": f"t35-sla-{ctx.run_id}", "url": "http://203.0.113.10:9/hook",
                                              "eventTypes": ["workorder.sla_escalated"], "enabled": True})
        check(st == 200, f"创建 Webhook HTTP {st} {msg(b)}")
        hid = data(b)["id"]
        wo = ctx.workorder(wo_type=wo_type, priority=prio, title="CT-14 SLA 升级")
        st, d = ctx.admin.get(f"/workorders/{wo}")
        check((data(d) or {}).get("slaResolveDeadline"), "工单未按 SLA 配置生成解决时限")
        got = None
        for _ in range(44):   # 升级扫描 60s 一轮、时限 1 分钟：最多等 220s
            time.sleep(5.0)
            st, b = ctx.admin.get(f"/webhooks/{hid}/deliveries", params={"page": 1, "size": 50})
            rows = (data(b) or {}).get("records") or (data(b) or {}).get("list") or []
            got = next((r for r in rows if r.get("eventType") == "workorder.sla_escalated"
                        and (r.get("payload") or {}).get("objId") == wo), None)
            if got:
                break
        check(got is not None, f"220s 内未等到 {wo} 的 workorder.sla_escalated 投递记录")
        st, b = ctx.admin.get("/notifications/my", params={"page": 1, "size": 30})
        noti = next((n for n in (data(b) or {}).get("records") or []
                     if n.get("type") == "SLA" and n.get("refId") == wo), None)
        check(noti is not None, "申请人未收到 SLA 升级站内信")
        return f"投递记录 #{got.get('id')} + 站内信 #{noti.get('id')}"
    finally:
        if hid:
            ctx.admin.delete(f"/webhooks/{hid}")
        if wo:
            try:
                ctx.admin.post(f"/workorders/{wo}/transitions", {"action": "开始处理", "opinion": "t"})
                ctx.admin.post(f"/workorders/{wo}/transitions", {"action": "关闭", "opinion": "t", "rootCause": "测试收尾"})
            except Exception:  # noqa
                pass
        try:
            ctx.admin.put(f"/workorders/sla-config/{wo_type}/{prio}", restore)
        except Exception:  # noqa
            pass


@case(S, "CT-15", "SSE 端点契约：无 token 401；建连发 connected 帧；工单事件按频道实时广播", risk="实时推送链路断裂", level="P0")
def _(ctx):
    url = f"{ctx.base}/notifications/sse"
    r = rq.get(url, timeout=10)
    check(r.status_code == 401, f"未带 token 应 401，实际 {r.status_code}")
    tok = ctx.admin.s.cookies.get("access_token")
    check(bool(tok), "登录会话缺少 access_token cookie")
    with rq.get(url, params={"token": tok}, stream=True, timeout=(10, 15)) as s:
        check(s.status_code == 200, f"建连 HTTP {s.status_code}")
        # iter_lines 不可重入：第二次调用会新建生成器，而首个生成器被 GC 时会把
        # urllib3 底层连接一并关闭，第二段只能读到空流——必须单生成器跨阶段消费
        lines = s.iter_lines(decode_unicode=True)
        head, t0 = [], time.time()
        for line in lines:
            if line:
                head.append(line)
            if any(x.startswith("data:") for x in head) or time.time() - t0 > 8:
                break
        check(any("connected" in x for x in head), f"首帧不是 connected：{head[:4]}")
        st, b = ctx.admin.post("/workorders", {"woType": "巡检", "title": f"CT-15 SSE 广播 {ctx.run_id}"})
        check(st == 200, f"建单 HTTP {st} {msg(b)}")
        hit, t0 = False, time.time()
        for line in lines:
            if line and line.startswith("data:") and '"channel":"workorder"' in line.replace(" ", ""):
                hit = True
                break
            if time.time() - t0 > 10:
                break
        check(hit, "10s 内未在流上收到 workorder 频道广播帧")
    return "connected 帧 + workorder 频道广播均正常"


@case(S, "CT-16", "资产删除财务校验：已进入折旧流程（转固计提）的资产拒绝删除 409，未入账可删", risk="删除资产静默抹掉财务台账（F4）", level="P1")
def _(ctx):
    a = ctx.asset()
    # 财务状态链：未入账 → 已入账 → 转固计提（状态机允许的两步流转）
    for fin in ("已入账", "转固计提"):
        st, b = ctx.admin.post(f"/assets/{a}/transitions", {"targetFin": fin, "reason": f"CT-16 {ctx.run_id}"})
        check(st == 200, f"流转 {fin} HTTP {st} {msg(b)}")
    st, b = ctx.admin.delete(f"/assets/{a}")
    check(st == 409, f"转固计提资产删除应 409，实际 {st}")
    check("财务" in msg(b), f"提示应说明财务状态：{msg(b)}")
    # 批量删除同资产：计入错误行而非整批失败
    st, b = ctx.admin.post("/assets/batch-delete", {"assetNos": [a]})
    rows = (data(b) or {}).get("skipped") or []
    check(st == 200 and len(rows) == 1, f"batch-delete 应 200 且 1 行错误，实际 {st} {rows}")
    check("财务" in str(rows), f"错误行应含财务提示：{rows}")
    # 对照组：未入账（初始财务状态）的资产仍可正常删除
    a2 = ctx.asset()
    st, b = ctx.admin.delete(f"/assets/{a2}")
    check(st == 200, f"未入账资产删除应 200，实际 {st} {msg(b)}")
    return "财务删除守卫生效：转固计提 409 / batch 错误行 / 未入账放行"


@case(S, "CT-17", "带外端点 bmc_ip 唯一约束：顺序重复 409；并发 8 路同一 bmcIp 恰好 1 次 200、其余 409（无 500）", risk="check-then-act 竞态穿透唯一约束返回 500（P3-2）", level="P1")
def _(ctx):
    tok = ctx.admin.s.cookies.get("access_token")
    check(bool(tok), "管理员会话缺少 access_token cookie")

    def uniq_ip():
        n = uuid.uuid4().int
        return f"10.{n % 250 + 1}.{(n >> 8) % 250 + 1}.{(n >> 16) % 250 + 1}"

    # 顺序重复：应用层 selectCount 查重 → 409（v3.4 已有行为，回归保护）
    ip = uniq_ip()
    st, b = ctx.admin.post("/oob/endpoints", {"bmcIp": ip})
    check(st == 200, f"首次创建 HTTP {st} {msg(b)}")
    st, b = ctx.admin.post("/oob/endpoints", {"bmcIp": ip})
    check(st == 409, f"顺序重复创建应 409，实际 {st} {msg(b)}")
    check("已存在" in msg(b), f"提示应说明端点已存在：{msg(b)}")

    # 并发竞态：8 路同时创建另一 bmcIp——selectCount 查重同时放行、由数据库唯一约束兜底，
    # DuplicateKeyException 须映射为 409（v3.5.2 前：竞态方收到 500）
    ip2 = uniq_ip()

    def race(_):
        c = ctx.client()
        c.s.cookies.set("access_token", tok)
        return c.post("/oob/endpoints", {"bmcIp": ip2})[0]

    with ThreadPoolExecutor(8) as ex:
        codes = list(ex.map(race, range(8)))
    check(codes.count(200) == 1, f"并发 8 路应恰好 1 次 200，实际 {codes}")
    check(all(c in (200, 409) for c in codes), f"竞态穿透应映射为 409，实际状态码：{codes}")

    # 清理本用例创建的端点（无凭据、无关联资产，留着只会让采集器反复外呼失败）
    for kw in (ip, ip2):
        try:
            st, b = ctx.admin.get("/oob/endpoints", params={"page": 1, "size": 50, "keyword": kw})
            for r in (data(b) or {}).get("records") or []:
                if r.get("bmcIp") == kw:
                    ctx.admin.delete(f"/oob/endpoints/{r['id']}")
        except Exception:  # noqa
            pass
    return f"顺序 409 + 并发 {codes.count(200)}×200/{codes.count(409)}×409，唯一约束兜底生效"


@case(S, "CT-18", "资产全生命周期时间线聚合：台账/工单源统一分页、source 过滤白名单、数据范围按不存在处理", risk="时间线漏聚合金链路事件，资产追溯断档（v3.6 fe-1）", level="P1")
def _(ctx):
    no = ctx.asset(name="t34-timeline-asset")

    # 台账源：新建资产必有 CREATE 事件，字段契约（source/eventType/eventTime/refDoc/detail）
    st, b = ctx.admin.get(f"/assets/{no}/timeline")
    check(st == 200, f"timeline HTTP {st} {msg(b)}")
    d = data(b)
    check(isinstance(d.get("total"), int) and isinstance(d.get("records"), list), "应返回 PageResult 结构")
    recs = d["records"]
    create_ev = [r for r in recs if r.get("source") == "asset" and r.get("eventType") == "CREATE"]
    check(bool(create_ev), "台账源应含 CREATE 事件")
    if create_ev:
        ev = create_ev[0]
        # operator/refDoc 可为 null（non_null 序列化会剔除），仅断言必有字段
        check(all(k in ev for k in ("id", "source", "eventType", "detail", "eventTime")),
              f"timeline 项缺字段：{sorted(ev.keys())}")
        check(isinstance(ev["detail"], str) and ev["detail"].startswith("{"), "detail 应为 JSON 字符串（::text 惯例）")

    # 工单源：关联资产的工单 → WO_SUBMIT（refDoc=工单号）
    wo = ctx.workorder(wo_type="巡检", title="t34-timeline 工单", assetNos=[no])
    st, b = ctx.admin.get(f"/assets/{no}/timeline")
    recs = data(b)["records"]
    check(any(r.get("source") == "workorder" and r.get("eventType") == "WO_SUBMIT" and r.get("refDoc") == wo
              for r in recs), f"工单源应含 {wo} 的 WO_SUBMIT 事件")

    # source 过滤：workorder 只回工单源；非法值 400
    st, b = ctx.admin.get(f"/assets/{no}/timeline", params={"source": "workorder"})
    recs = data(b)["records"]
    check(st == 200 and recs and all(r["source"] == "workorder" for r in recs), "source=workorder 应只返回工单源")
    st, b = ctx.admin.get(f"/assets/{no}/timeline", params={"source": "workorder,fin"})
    recs = data(b)["records"]
    check(st == 200 and all(r["source"] in ("workorder", "fin") for r in recs), "逗号分隔多源过滤应生效")
    st, b = ctx.admin.get(f"/assets/{no}/timeline", params={"source": "hack"})
    check(st == 400, f"非法 source 应 400，实际 {st}")

    # 财务 / 告警源：需数据库直连造数（无 psql 时跳过该部分）
    if ctx.sql("SELECT 1") is not None:
        fp = f"t34-tl-{uuid.uuid4().hex[:12]}"
        try:
            ctx.sql(f"INSERT INTO depreciation_record (asset_no, period, origin_value, month_dep, accumulated, net_value, method) "
                    f"VALUES ('{no}', DATE '2026-10-01', 100000, 2000, 2000, 98000, '平均年限法')")
            ctx.sql(f"INSERT INTO alert_event (fingerprint, alertname, severity, status, starts_at, asset_no) "
                    f"VALUES ('{fp}', 'HighTemp', 'critical', 'FIRING', NOW(), '{no}')")
            st, b = ctx.admin.get(f"/assets/{no}/timeline", params={"source": "fin,alert"})
            recs = data(b)["records"]
            check(any(r["eventType"] == "DEPRECIATION" for r in recs), "财务源应含折旧计提事件")
            check(any(r["eventType"] == "ALERT_FIRING" for r in recs), "告警源应含告警触发事件")
        finally:
            ctx.sql(f"DELETE FROM alert_event WHERE fingerprint = '{fp}'")
            ctx.sql(f"DELETE FROM depreciation_record WHERE asset_no = '{no}'")

    # 数据范围：SELF 用户看不到 admin 创建的资产 → 按不存在处理（404）
    ctx.role("R34-TLSELF", {"assets": "r"}, scopes={"assets": "SELF"})
    u = ctx.user("t34-tlself", ["R34-TLSELF"])
    st, b = u.get(f"/assets/{no}/timeline")
    check(st == 404, f"SELF 范围外资产的时间线应 404，实际 {st}")

    return f"四源聚合 + source 过滤 + 数据范围均符合契约（工单 {wo}）"

@case(S, "CT-TL-01", "v3.6 时间线倒序与分页：event_time 倒序、size=200 钳制 100、翻页正确（5.1.1-1a/6a/6b）",
      risk="分页钳制缺失导致深翻页全量拉取（v3.6 fe-1）", level="P1")
def _(ctx):
    no = ctx.asset(name="t34-tl-page-asset")
    if ctx.sql("SELECT 1") is None:
        return "跳过：无数据库直连，钳制/翻页用例需直连造数"
    # generate_series 一条 SQL 造 105 条折旧事件（不同月份不冲突唯一约束）
    ctx.sql(f"INSERT INTO depreciation_record (asset_no, period, origin_value, month_dep, accumulated, net_value, method) "
            f"SELECT '{no}', (DATE '2024-01-01' + (i || ' month')::interval)::date, 100000, 100, i * 100, 100000 - i * 100, '平均年限法' "
            f"FROM generate_series(1, 105) AS i")
    try:
        # 倒序：source=fin 最新月份在前（105 个月 → 2025-10）
        st, b = ctx.admin.get(f"/assets/{no}/timeline", params={"source": "fin", "size": 20})
        check(st == 200, f"timeline HTTP {st}")
        recs = data(b)["records"]
        check(bool(recs) and recs[0]["refDoc"] == "2025-10", f"应按 event_time 倒序，首条 {recs[0]['refDoc'] if recs else '-'}")
        times = [r["eventTime"] for r in recs]
        check(times == sorted(times, reverse=True), "eventTime 应倒序")
        # 钳制：size=200 → 实际 100（若不钳制会返回 105）
        st, b = ctx.admin.get(f"/assets/{no}/timeline", params={"source": "fin", "size": 200})
        got = len(data(b)["records"])
        check(st == 200 and got == 100, f"size=200 应钳制为 100，实际 {got}")
        # 翻页：size=20&page=2 == size=40&page=1 的第 21~40 条
        st, b1 = ctx.admin.get(f"/assets/{no}/timeline", params={"source": "fin", "size": 40})
        st, b2 = ctx.admin.get(f"/assets/{no}/timeline", params={"source": "fin", "size": 20, "page": 2})
        all40 = [r["refDoc"] for r in data(b1)["records"]]
        page2 = [r["refDoc"] for r in data(b2)["records"]]
        check(page2 == all40[20:], "page=2 应返回第 21~40 条且与全量顺序一致")
    finally:
        ctx.sql(f"DELETE FROM depreciation_record WHERE asset_no = '{no}'")
    return "倒序 / size 钳制 100 / 翻页一致性均通过"


@case(S, "CT-TL-02", "v3.6 时间线告警/折旧条目字段契约：RESOLVED 操作人=确认人取 endsAt；折旧条目含完整财务字段（5.1.1-3a/4a）",
      risk="条目字段缺失导致前端渲染断档（v3.6 fe-1）", level="P1")
def _(ctx):
    no = ctx.asset(name="t34-tl-field-asset")
    if ctx.sql("SELECT 1") is None:
        return "跳过：无数据库直连"
    fp = f"t34-tlf-{uuid.uuid4().hex[:12]}"
    try:
        ctx.sql(f"INSERT INTO alert_event (fingerprint, alertname, severity, status, starts_at, ends_at, ack_by, close_reason, asset_no) "
                f"VALUES ('{fp}', 'HighTemp', 'critical', 'FIRING', NOW(), NULL, NULL, NULL, '{no}')")
        ctx.sql(f"INSERT INTO alert_event (fingerprint, alertname, severity, status, starts_at, ends_at, ack_by, close_reason, asset_no) "
                f"VALUES ('{fp}-r', 'HighTemp', 'critical', 'RESOLVED', NOW(), NOW() + interval '1 hour', 't34-tl-acker', '温度恢复', '{no}')")
        ctx.sql(f"INSERT INTO depreciation_record (asset_no, period, origin_value, month_dep, accumulated, net_value, method) "
                f"VALUES ('{no}', DATE '2026-09-01', 100000, 2000, 24000, 76000, '平均年限法')")
        st, b = ctx.admin.get(f"/assets/{no}/timeline", params={"source": "alert,fin"})
        check(st == 200, f"timeline HTTP {st}")
        recs = data(b)["records"]
        firing = [r for r in recs if r["eventType"] == "ALERT_FIRING"]
        resolved = [r for r in recs if r["eventType"] == "ALERT_RESOLVED"]
        check(bool(firing), "告警源应含 ALERT_FIRING 条目")
        if firing:
            d = json.loads(firing[0]["detail"])
            check(all(k in d for k in ("alertname", "severity", "summary", "instance")), f"FIRING detail 缺字段：{sorted(d.keys())}")
        check(bool(resolved), "已恢复告警应有 ALERT_RESOLVED 条目")
        if resolved:
            check(resolved[0]["operator"] == "t34-tl-acker", f"RESOLVED 操作人应为确认人，实际 {resolved[0]['operator']}")
            rd = json.loads(resolved[0]["detail"])
            check("closeReason" in rd, f"RESOLVED detail 应含 closeReason：{sorted(rd.keys())}")
        dep = [r for r in recs if r["eventType"] == "DEPRECIATION"]
        check(bool(dep), "财务源应含折旧条目")
        if dep:
            dd = json.loads(dep[0]["detail"])
            check(all(k in dd for k in ("period", "monthDep", "accumulated", "netValue", "method")),
                  f"折旧条目 detail 缺字段：{sorted(dd.keys())}")
            check(dep[0]["refDoc"] == "2026-09", f"折旧条目 refDoc 应为月份，实际 {dep[0]['refDoc']}")
    finally:
        ctx.sql(f"DELETE FROM alert_event WHERE fingerprint LIKE '{fp}%'")
        ctx.sql(f"DELETE FROM depreciation_record WHERE asset_no = '{no}'")
    return "告警 FIRING/RESOLVED 与折旧条目字段契约均通过"


@case(S, "CT-TL-03", "v3.6 时间线工单源含/不含断言：related_assets 含资产的工单出现、不含的不出现（5.1.1-2a/2b）",
      risk="工单误聚合污染其他资产时间线（v3.6 fe-1）", level="P1")
def _(ctx):
    no = ctx.asset(name="t34-tl-wo-asset")
    wo_in = ctx.workorder(wo_type="巡检", title="t34-tl 关联工单", assetNos=[no])
    wo_out = ctx.workorder(wo_type="巡检", title="t34-tl 无关联工单")
    st, b = ctx.admin.get(f"/assets/{no}/timeline", params={"source": "workorder"})
    check(st == 200, f"timeline HTTP {st}")
    recs = data(b)["records"]
    wos = [json.loads(r["detail"]).get("woNo") for r in recs if r.get("source") == "workorder"]
    check(wo_in in wos, f"关联资产工单 {wo_in} 应出现在时间线")
    check(wo_out not in wos, f"未关联工单 {wo_out} 不应出现在时间线")
    return f"工单源聚合范围正确（含 {wo_in}，不含 {wo_out}）"
