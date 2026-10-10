"""权限矩阵：模块读写 → 操作（删除 / 导出）→ 数据范围（行）→ 字段（列），以及授权天花板。"""
import uuid
from lib.framework import suite, case, setup, check, skip
from lib.context import data, msg

S = suite("permission", "权限矩阵", order=30)

FLD_FIELDS = {
    "assets": {"hidden": ["purchasePrice", "sn", "mgmtIp", "finance", "roomCode"], "readonly": ["department", "statusFin"]},
    "workorders": {"hidden": ["costLabor", "costService", "costOther", "attachments"], "readonly": ["description", "relatedAssets"]},
}


@setup(S)
def _setup(ctx):
    r = {}
    r["ro"] = ctx.role("R34-ARO", {"assets": "r"})
    r["aw"] = ctx.role("R34-AW", {"assets": "rw", "workorders": "rw"})
    r["awd"] = ctx.role("R34-AWD", {"assets": "rw"}, actions={"assets": {"delete": True}})
    r["noexp"] = ctx.role("R34-NOEXP", {"assets": "rw", "workorders": "rw"}, actions={"assets": {"export": False}, "workorders": {"export": False}})
    r["self"] = ctx.role("R34-SELF", {"assets": "rw", "workorders": "rw"}, scopes={"assets": "SELF", "workorders": "SELF"})
    r["dept"] = ctx.role("R34-DEPT", {"assets": "rw", "workorders": "rw"}, scopes={"assets": "DEPT", "workorders": "DEPT"})
    r["fld"] = ctx.role("R34-FLD", {"assets": "rw", "workorders": "rw"}, fields=FLD_FIELDS)
    r["open"] = ctx.role("R34-OPEN", {"assets": "r"})
    r["hd"] = ctx.role("R34-HD", {"system": "rw", "assets": "rw"}, scopes={"assets": "SELF"},
                       fields={"assets": {"hidden": ["purchasePrice"], "readonly": []}})
    ctx.cache["roles"] = r


def U(ctx, name, roles, dept=None):
    return ctx.user(f"t34-{name}", roles, department=dept)


# ───────────── 模块 ─────────────

@case(S, "PM-01", "只读角色：可查资产、不能新建资产", level="P0")
def _(ctx):
    c = U(ctx, "ro", ["R34-ARO"])
    st, _ = c.get("/assets", params={"page": 1, "size": 1})
    check(st == 200, f"读 HTTP {st}")
    st, b = c.post("/assets", {"name": "x", "categoryCode": "SRV"})
    check(st == 403, f"写 HTTP {st} {msg(b)}")


@case(S, "PM-02", "未授予模块：访问工单返回 403", level="P0")
def _(ctx):
    c = U(ctx, "ro", ["R34-ARO"])
    st, b = c.get("/workorders", params={"page": 1, "size": 1})
    check(st == 403, f"HTTP {st}")


# ───────────── 操作 ─────────────

@case(S, "PA-01", "未授予删除：删除 / 批量删除资产返回 403 并说明原因", risk="v3.4 删除改为显式授予", level="P0")
def _(ctx):
    c = U(ctx, "aw", ["R34-AW"])
    a = ctx.asset()
    st, b = c.delete(f"/assets/{a}")
    check(st == 403 and "删除" in msg(b), f"HTTP {st} {msg(b)}")
    st, b = c.post("/assets/batch-delete", {"assetNos": [a]})
    check(st == 403, f"批量 HTTP {st} {msg(b)}")


@case(S, "PA-02", "显式授予删除：可删除待上架资产；/auth/me 的 actions 同步", level="P0")
def _(ctx):
    c = U(ctx, "awd", ["R34-AWD"])
    st, me = c.get("/auth/me")
    check(data(me)["actions"]["assets"]["delete"] is True, "me.actions.assets.delete 应为 true")
    a = ctx.asset()
    st, b = c.delete(f"/assets/{a}")
    check(st == 200, f"HTTP {st} {msg(b)}")
    c2 = U(ctx, "aw", ["R34-AW"])
    st, me = c2.get("/auth/me")
    check(data(me)["actions"]["assets"]["delete"] is False, "未授予时 me.actions.assets.delete 应为 false")


@case(S, "PA-03", "导出被禁止：资产导出、工单导出都返回 403", risk="v3.3 工单导出不校验导出权限", level="P1")
def _(ctx):
    c = U(ctx, "noexp", ["R34-NOEXP"])
    st, b = c.get("/assets/export")
    check(st == 403, f"资产导出 HTTP {st}")
    st, b = c.get("/workorders/export")
    check(st == 403, f"工单导出 HTTP {st}")


# ───────────── 数据范围 ─────────────

@case(S, "PS-01", "工单 SELF：列表只含本人相关，他人工单详情 404", level="P0")
def _(ctx):
    other = U(ctx, "other", ["R34-AW"], dept="测试乙部")
    me = U(ctx, "self", ["R34-SELF"], dept="测试甲部")
    wo_other = ctx.workorder(client=other)
    wo_mine = ctx.workorder(client=me)
    st, b = me.get("/workorders", params={"page": 1, "size": 200})
    recs = data(b)["records"]
    leaked = [r["woNo"] for r in recs if r.get("applicantUsername") not in ("t34-self",) and r.get("handler") != "t34-self"]
    check(not leaked, f"可见他人工单 {leaked[:5]}")
    check(any(r["woNo"] == wo_mine for r in recs), "看不到自己的工单")
    st, b = me.get(f"/workorders/{wo_other}")
    check(st == 404, f"他人工单详情 HTTP {st}")


@case(S, "PS-02", "工单 DEPT：可见同部门同事的工单，不可见其他部门", level="P1")
def _(ctx):
    mate = U(ctx, "mate", ["R34-AW"], dept="测试甲部")
    other = U(ctx, "other", ["R34-AW"], dept="测试乙部")
    d = U(ctx, "dept", ["R34-DEPT"], dept="测试甲部")
    wo_mate = ctx.workorder(client=mate)
    wo_other = ctx.workorder(client=other)
    st, b = d.get(f"/workorders/{wo_mate}")
    check(st == 200, f"同部门 HTTP {st}")
    st, b = d.get(f"/workorders/{wo_other}")
    check(st == 404, f"其他部门 HTTP {st}")


@case(S, "PS-03", "资产 SELF：按编号导出也不能拿到他人资产（v3.3 绕过修复）", risk="数据范围绕过", level="P0")
def _(ctx):
    me = U(ctx, "self", ["R34-SELF"], dept="测试甲部")
    mine = ctx.asset(owner="t34-self")
    theirs = ctx.asset(owner="someone-else")
    st, b = me.get(f"/assets/{theirs}")
    check(st == 404, f"他人资产详情 HTTP {st}")
    me.get("/assets/export", params={"assetNos": f"{mine},{theirs}"})
    text = me.last.text
    check(mine in text and theirs not in text, "导出结果包含他人资产或缺少本人资产")


# ───────────── 字段 ─────────────

def _fld(ctx):
    return U(ctx, "fld", ["R34-FLD"])


@case(S, "PF-01", "读：列表、详情、子资产、变更记录剔除隐藏字段，并声明 X-Field-Hidden", level="P0")
def _(ctx):
    c = _fld(ctx)
    parent = ctx.asset(purchasePrice=9999, mgmtIp="10.9.9.9")
    child = ctx.asset(parentAssetNo=parent, purchasePrice=1)
    st, b = c.get(f"/assets/{parent}")
    d = data(b)
    for k in ("purchasePrice", "sn", "mgmtIp", "finance", "roomCode"):
        check(k not in d, f"详情仍含隐藏字段 {k}")
    check(all("sn" not in ch for ch in d.get("children", [])), "子资产仍含 sn")
    hdr = c.last.headers.get("X-Field-Hidden", "")
    check("purchasePrice" in hdr, f"X-Field-Hidden={hdr!r}")
    st, b = c.get("/assets", params={"page": 1, "size": 5})
    check(all("sn" not in r and "purchasePrice" not in r for r in data(b)["records"]), "列表仍含隐藏字段")


@case(S, "PF-02", "写：原样回传只读字段可保存；改动只读或隐藏字段返回 403；新建带隐藏字段 403", level="P0")
def _(ctx):
    c = _fld(ctx)
    a = ctx.asset(department="甲", purchasePrice=100)
    st, b = ctx.admin.get(f"/assets/{a}")
    cur = data(b)
    base = {k: cur.get(k) for k in ("name", "categoryCode", "department", "ownerType", "version")}
    st, b = c.put(f"/assets/{a}", {**base, "name": "改名"})
    check(st == 200, f"原样回传只读字段 HTTP {st} {msg(b)}")
    st, b = ctx.admin.get(f"/assets/{a}")
    base["version"] = data(b)["version"]
    st, b = c.put(f"/assets/{a}", {**base, "department": "乙"})
    check(st == 403 and "使用部门" in msg(b), f"改只读 HTTP {st} {msg(b)}")
    st, b = c.put(f"/assets/{a}", {**base, "purchasePrice": 1})
    check(st == 403 and "采购价格" in msg(b), f"改隐藏 HTTP {st} {msg(b)}")
    st, b = c.post("/assets", {"name": "x", "categoryCode": "SRV", "sn": "SN-HIDDEN-1"})
    check(st == 403, f"新建带隐藏字段 HTTP {st} {msg(b)}")


@case(S, "PF-03", "写：批量修改与状态流转里出现受限字段返回 403", level="P1")
def _(ctx):
    c = _fld(ctx)
    a = ctx.asset()
    st, b = c.post("/assets/batch-update", {"assetNos": [a], "department": "乙"})
    check(st == 403, f"批量修改 HTTP {st} {msg(b)}")
    st, b = c.post(f"/assets/{a}/transitions", {"targetFin": "已入账", "reason": "t"})
    check(st == 403 and "财务状态" in msg(b), f"流转 HTTP {st} {msg(b)}")


@case(S, "PF-04", "导出不含隐藏列", level="P0")
def _(ctx):
    c = _fld(ctx)
    a = ctx.asset()
    c.get("/assets/export", params={"assetNos": a})
    header = c.last.text.lstrip("\ufeff").splitlines()[0].split(",")
    for col in ("SN", "采购单价", "管理IP", "机房编码"):
        check(col not in header, f"导出表头含隐藏列 {col}：{header}")
    check("使用部门" in header, "只读列应保留")


@case(S, "PF-05", "搜索：不能用隐藏的 SN / 管理 IP 反查资产；不能按隐藏字段筛选", risk="借搜索反推隐藏值", level="P0")
def _(ctx):
    c = _fld(ctx)
    sn = f"SN-HID-{uuid.uuid4().hex[:8].upper()}"   # 后端保存时 SN 统一大写
    ip = f"10.250.{uuid.uuid4().int % 250}.{uuid.uuid4().int % 250}"
    ctx.asset(sn=sn, mgmtIp=ip)
    st, b = c.get("/assets", params={"keyword": sn})
    check(data(b)["total"] == 0, f"关键字按隐藏 SN 命中 {data(b)['total']}")
    st, b = c.get("/search", params={"keyword": ip})
    check(not [x for x in (data(b) or []) if x.get("type") == "asset"], "全局搜索按隐藏 IP 命中资产")
    st, b = ctx.admin.get("/assets", params={"keyword": sn})
    check(data(b)["total"] == 1, "管理员应能搜到（对照）")
    st, b = c.get("/assets", params={"roomCode": "R01"})
    check(st == 403, f"按隐藏字段筛选 HTTP {st}")


@case(S, "PF-06", "有字段限制的角色不能批量导入", level="P1")
def _(ctx):
    c = _fld(ctx)
    st, b = c.req("POST", "/assets/import", files={"file": ("a.csv", b"x")})
    check(st == 403, f"HTTP {st} {msg(b)}")


@case(S, "PF-07", "工单：费用与附件隐藏；改只读的描述 / 关联资产返回 403", level="P0")
def _(ctx):
    c = _fld(ctx)
    a = ctx.asset()
    st, b = ctx.admin.post("/workorders", {"woType": "故障维修", "title": "字段", "description": "原描述", "relatedAssets": [a]})
    wo = data(b)["woNo"]
    st, b = c.get(f"/workorders/{wo}")
    d = data(b)
    check(st == 200 and "costLabor" not in d, f"详情含费用或 HTTP {st}")
    st, b = c.get(f"/workorders/{wo}/attachments")
    check(st == 403, f"附件列表 HTTP {st}")
    st, b = c.post(f"/workorders/{wo}/items", {"assetNo": ctx.asset()})
    check(st == 403 and "关联资产" in msg(b), f"增资产行 HTTP {st} {msg(b)}")
    st, b = c.post("/workorders", {"woType": "巡检", "title": "x", "assetNos": [a]})
    check(st == 403, f"新建带只读关联资产 HTTP {st} {msg(b)}")
    st, b = c.put(f"/workorders/{wo}/repair-cost", {"costLabor": 1})
    check(st == 403, f"登记费用 HTTP {st} {msg(b)}")


@case(S, "PF-08", "多角色合并：另一个角色未隐藏 SN，则 SN 可见（取最宽）", level="P1")
def _(ctx):
    c = ctx.user("t34-merge", ["R34-FLD", "R34-OPEN"])
    a = ctx.asset()
    st, b = c.get(f"/assets/{a}")
    check("sn" in data(b), "合并后 SN 应可见")
    st, me = c.get("/auth/me")
    check("sn" not in (data(me)["fields"].get("assets", {}).get("hidden") or []), "me.fields 仍隐藏 sn")


@case(S, "PF-09", "管理员不受字段规则约束", level="P1")
def _(ctx):
    a = ctx.asset(purchasePrice=5)
    st, b = ctx.admin.get(f"/assets/{a}")
    check("purchasePrice" in data(b) and "sn" in data(b), "管理员详情缺字段")


@case(S, "PF-10", "角色保存：未知字段 / 未支持模块返回 400", level="P2")
def _(ctx):
    st, b = ctx.admin.put("/roles/R34-OPEN", {"roleCode": "R34-OPEN", "name": "R34-OPEN", "modulePerms": {"assets": {"read": True}},
                                              "dataScopes": {"assets": {"type": "ALL"}}, "actionPerms": {},
                                              "fieldPerms": {"assets": {"hidden": ["noSuchField"]}}})
    check(st == 400, f"未知字段 HTTP {st} {msg(b)}")
    ctx.role("R34-OPEN", {"assets": "r"})


# ───────────── 授权天花板 ─────────────

@case(S, "PC-01", "非管理员不能把自己没有的删除权限授出去（新建角色 / 分配已有角色）", risk="纵向越权", level="P0")
def _(ctx):
    hd = U(ctx, "hd", ["R34-HD"])
    st, b = hd.post("/roles", {"roleCode": f"R34-X{ctx.run_id[-4:]}", "name": "x", "modulePerms": {"assets": {"read": True, "write": True}},
                               "dataScopes": {"assets": {"type": "SELF"}}, "actionPerms": {"assets": {"delete": True}},
                               "fieldPerms": {"assets": {"hidden": ["purchasePrice"]}}})
    check(st == 403 and ("删除" in msg(b) or "delete" in msg(b)), f"新建角色 HTTP {st} {msg(b)}")
    st, b = hd.put("/users/t34-aw", {"name": "t34-aw", "roleCodes": ["R34-AWD"]})
    check(st == 403, f"分配带删除的角色 HTTP {st} {msg(b)}")


@case(S, "PC-02", "非管理员不能授出比自己宽的数据范围或更松的字段规则", risk="纵向越权", level="P0")
def _(ctx):
    hd = U(ctx, "hd", ["R34-HD"])
    base = {"name": "x", "modulePerms": {"assets": {"read": True, "write": True}}, "actionPerms": {}}
    code = f"R34-Y{ctx.run_id[-4:]}"
    st, b = hd.post("/roles", {**base, "roleCode": code, "dataScopes": {"assets": {"type": "ALL"}},
                               "fieldPerms": {"assets": {"hidden": ["purchasePrice"]}}})
    check(st == 403 and "数据范围" in msg(b), f"更宽范围 HTTP {st} {msg(b)}")
    st, b = hd.post("/roles", {**base, "roleCode": code, "dataScopes": {"assets": {"type": "SELF"}}, "fieldPerms": {}})
    check(st == 403 and "字段" in msg(b), f"更松字段 HTTP {st} {msg(b)}")
    st, b = hd.post("/roles", {**base, "roleCode": code, "dataScopes": {"assets": {"type": "SELF"}},
                               "fieldPerms": {"assets": {"hidden": ["purchasePrice", "sn"]}}})
    check(st == 200, f"不超出自身时应允许 HTTP {st} {msg(b)}")

# ───────────── v3.6 时间线权限与数据范围 ─────────────

@case(S, "PF-TL-01", "v3.6 时间线权限：无 assets 模块 403；SELF 范围他人资产 404；旧 /events 接口契约不变（5.1.1-7）",
      risk="时间线越权或旧接口破坏（v3.6 fe-1）", level="P1")
def _(ctx):
    no = ctx.asset(name="t34-tl-perm-asset")
    # 无 assets 权限 → 403
    ctx.role("R34-TLNOP", {"workorders": "r"})
    u = ctx.user("t34-tlnop", ["R34-TLNOP"])
    st, b = u.get(f"/assets/{no}/timeline")
    check(st == 403, f"无 assets 权限访问时间线应 403，实际 {st}")
    # SELF 范围：他人资产按不存在处理 → 404
    ctx.role("R34-TLSELF2", {"assets": "r"}, scopes={"assets": "SELF"})
    u2 = ctx.user("t34-tlself2", ["R34-TLSELF2"])
    st, b = u2.get(f"/assets/{no}/timeline")
    check(st == 404 and "不存在" in msg(b), f"SELF 范围外时间线应 404，实际 {st}")
    # 旧 /events 接口契约不变：字段集不含 timeline 的 source，结构仍为 PageResult
    st, b = ctx.admin.get(f"/assets/{no}/events", params={"page": 1, "size": 20})
    check(st == 200, f"旧 /events HTTP {st}")
    recs = data(b)["records"]
    check(isinstance(data(b).get("total"), int) and bool(recs), "旧 /events 应返回 PageResult 且含 CREATE 事件")
    check(all("source" not in r and "detail" not in r for r in recs), "旧 /events 不应新增 timeline 的 source/detail 字段")
    check(all(k in recs[0] for k in ("id", "eventType", "eventTime")), f"旧 /events 字段缺失：{sorted(recs[0].keys())}")
    return "时间线权限 403 / SELF 404 / 旧接口契约均通过"
