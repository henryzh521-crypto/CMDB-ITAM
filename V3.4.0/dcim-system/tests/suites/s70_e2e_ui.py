"""浏览器端到端（Playwright，可选）：关键页面在真实浏览器里可用、前后端字段契约一致、权限在界面上生效。

需要 `pip install playwright` 且有 Chromium；前端地址 UI_BASE（默认 http://127.0.0.1:4173）。缺任一条件整套跳过。
"""
import asyncio, glob, os, time, uuid
from lib.framework import suite, case, setup, check, Skip
from lib.context import data, msg

S = suite("e2e", "浏览器端到端", order=70)


def _exe():
    c = sorted(glob.glob("/opt/pw-browsers/chromium-*/chrome-linux/chrome"))
    return c[-1] if c else None


@setup(S)
def _setup(ctx):
    try:
        import playwright  # noqa
    except ImportError:
        raise Skip("未安装 playwright（pip install playwright && playwright install chromium）")
    import requests
    try:
        requests.get(ctx.ui_base + "/", timeout=5)
    except Exception:
        raise Skip(f"前端 {ctx.ui_base} 不可达")


def run_page(ctx, user, pw, fn):
    """在独立浏览器上下文里登录后执行 fn(page, errors)"""
    from playwright.async_api import async_playwright

    async def main():
        async with async_playwright() as p:
            b = await p.chromium.launch(executable_path=_exe())
            # 视口 1920：看板 6 列（每列 280px）在 1440 宽度下「已关闭」列溢出可视区，
            # drag_to 的 mouse.move 不会横向滚动 overflow 容器，drop 会落空（UI-08）
            context = await b.new_context(viewport={"width": 1920, "height": 900}, locale="zh-CN",
                                          extra_http_headers={"X-Forwarded-For": ctx.ip()})
            page = await context.new_page()
            errors = {"console": [], "api": []}
            page.on("console", lambda m: errors["console"].append(m.text) if m.type == "error" else None)
            page.on("response", lambda r: errors["api"].append(f"{r.status} {r.url}") if r.status >= 500 and "/api/" in r.url else None)
            await page.goto(ctx.ui_base + "/login")
            await page.fill("input[placeholder='用户名']", user)
            await page.fill("input[placeholder='密码']", pw)
            await page.keyboard.press("Enter")
            await page.wait_for_url("**/dashboard", timeout=20000)
            try:
                return await fn(page, errors)
            finally:
                await b.close()
    return asyncio.run(main())


@case(S, "UI-01", "管理员登录后运营总览渲染，无 5xx、无脚本错误", level="P0")
def _(ctx):
    async def fn(page, errors):
        await page.wait_for_timeout(3000)
        cards = await page.locator(".dashboard .el-card, .dashboard .stat-card, .dashboard [class*=card]").count()
        js = [e for e in errors["console"] if "Failed to load resource" not in e]
        return cards, js, errors["api"]
    cards, js, api = run_page(ctx, ctx.admin_user, ctx.admin_pw, fn)
    check(cards > 0, "运营总览没有渲染出卡片")
    check(not api, f"接口 5xx：{api[:3]}")
    check(not js, f"脚本错误：{js[:3]}")


@case(S, "UI-02", "通过界面创建带关联资产的工单，后端行项目正确落库", risk="前后端字段不一致（v3.2 assetNos 类问题）", level="P0")
def _(ctx):
    a = ctx.asset(name=f"t34-ui-{uuid.uuid4().hex[:6]}")
    title = f"t34 界面建单 {uuid.uuid4().hex[:6]}"

    async def fn(page, errors):
        await page.goto(ctx.ui_base + "/workorders")
        await page.wait_for_timeout(1500)
        await page.locator("button:has-text('创建工单')").first.click()
        dlg = page.locator(".el-dialog:has-text('创建工单')")
        await dlg.locator("label:has-text('巡检')").first.click()
        await dlg.locator("input[placeholder='必填']").first.fill(title)
        # Element Plus 2.8 的远程搜索下拉：占位文字不是 input 的 placeholder 属性，按区块定位
        block = dlg.locator(".detail-block:has-text('关联资产')")
        if not await block.locator(".el-select").count():
            await block.locator("button:has-text('添加行')").click()
        await block.locator(".el-select").first.click()
        await page.keyboard.type(a, delay=20)
        await page.wait_for_timeout(1200)
        await page.locator(f".el-select-dropdown__item:visible:has-text('{a}')").first.click()
        await dlg.locator("button:has-text('创建')").click()
        await page.wait_for_timeout(2500)
        return errors["api"]
    api = run_page(ctx, ctx.admin_user, ctx.admin_pw, fn)
    check(not api, f"接口 5xx：{api[:3]}")
    st, b = ctx.admin.get("/workorders", params={"page": 1, "size": 20, "keyword": title})
    recs = data(b)["records"]
    check(recs, "界面创建的工单没查到")
    st, d = ctx.admin.get(f"/workorders/{recs[0]['woNo']}")
    items = [i["assetNo"] for i in data(d).get("items") or []]
    check(items == [a], f"行项目 {items}，期望 [{a}]")


@case(S, "UI-03", "字段受限用户：资产列表不显示隐藏列，没有删除 / 导入按钮", level="P0")
def _(ctx):
    ctx.role("R34-FLD", {"assets": "rw", "workorders": "rw"},
             fields={"assets": {"hidden": ["purchasePrice", "sn", "mgmtIp", "finance", "roomCode"], "readonly": ["department", "statusFin"]}})
    u = ctx.user("t34-uifld", ["R34-FLD"])

    async def fn(page, errors):
        await page.goto(ctx.ui_base + "/assets/inventory")
        await page.wait_for_timeout(3000)
        heads = [h.strip() for h in await page.locator(".el-table__header th").all_inner_texts() if h.strip()]
        dels = await page.locator(".el-table__body button:has-text('删除')").count()
        imp = await page.locator("button:has-text('导入')").count()
        return heads, dels, imp
    heads, dels, imp = run_page(ctx, "t34-uifld", u.password, fn)
    check("SN" not in heads, f"仍显示 SN 列：{heads}")
    check("使用部门" in heads, f"只读字段应仍显示：{heads}")
    check(dels == 0 and imp == 0, f"删除按钮 {dels} 个，导入按钮 {imp} 个")


@case(S, "UI-04", "角色编辑页可配置字段权限并保存", level="P1")
def _(ctx):
    code = "R34-UIROLE"
    ctx.role(code, {"assets": "rw"})   # 每次先复位为「无字段规则」

    async def fn(page, errors):
        await page.goto(ctx.ui_base + "/system/roles")
        await page.wait_for_timeout(1500)
        search = page.locator("input[placeholder='搜索角色编码 / 名称']")
        await search.fill(code.lower())        # 顺带验证搜索不区分大小写
        await search.press("Enter")
        await page.wait_for_timeout(1200)
        await page.locator(f".el-table__body tr:has-text('{code}') button:has-text('编辑')").first.click()
        dlg = page.locator(".el-dialog:has-text('编辑角色')")
        row = dlg.locator(".field-rule-row:has-text('资产')")
        await row.locator(".el-select").first.click()
        await page.locator(".el-select-dropdown__item:visible:has-text('采购价格')").first.click()
        await page.keyboard.press("Escape")
        await dlg.locator("button:has-text('保存')").click()
        await page.wait_for_timeout(1500)
        return errors["api"]
    api = run_page(ctx, ctx.admin_user, ctx.admin_pw, fn)
    check(not api, f"接口 5xx：{api[:3]}")
    st, b = ctx.admin.get("/roles", params={"page": 1, "size": 200})
    role = [r for r in data(b)["records"] if r["roleCode"] == code][0]
    hidden = (role.get("fieldPerms") or {}).get("assets", {}).get("hidden") or []
    check("purchasePrice" in hidden, f"保存后的字段权限 {role.get('fieldPerms')}")


@case(S, "UI-05", "接口文档页可浏览，关于对话框显示前后端版本一致", level="P2")
def _(ctx):
    async def fn(page, errors):
        await page.goto(ctx.ui_base + "/developer/api-docs")
        await page.wait_for_timeout(2500)
        rows = await page.locator(".api-row").count()
        await page.locator(".layout-user").click()
        await page.locator("text=关于本系统").click()
        await page.wait_for_timeout(1500)
        about = await page.locator(".el-dialog:has-text('关于本系统')").inner_text()
        return rows, about
    rows, about = run_page(ctx, ctx.admin_user, ctx.admin_pw, fn)
    check(rows > 100, f"接口文档只有 {rows} 行")
    check("前后端版本不一致" not in about, about.replace("\n", " ")[:120])


@case(S, "UI-06", "暗色模式刷新后保持", level="P2")
def _(ctx):
    async def fn(page, errors):
        await page.evaluate("localStorage.setItem('dcim-theme','dark')")
        await page.reload()
        await page.wait_for_timeout(2000)
        cls = await page.evaluate("document.documentElement.className")
        await page.evaluate("localStorage.setItem('dcim-theme','light')")
        return cls
    cls = run_page(ctx, ctx.admin_user, ctx.admin_pw, fn)
    check("dark" in cls, f"html class={cls!r}")


@case(S, "UI-07", "通知中心：实时到达、红点、列表、全部已读、点击跳转工单", risk="v3.5 通知链路在界面断裂", level="P0")
def _(ctx):
    ctx.role("R35-NOTI", {"workorders": "rw"})
    u = ctx.user("t34-uinoti", ["R35-NOTI"])
    wo = ctx.workorder(client=u, wo_type="巡检")
    # 管理员开始处理 → v3.5 新增：通知 applicant
    st, b = ctx.admin.post(f"/workorders/{wo}/transitions", {"action": "开始处理", "opinion": "ui"})
    check(st == 200, f"开始处理 HTTP {st} {msg(b)}")

    async def fn(page, errors):
        await page.goto(ctx.ui_base + "/notifications")
        await page.wait_for_timeout(3000)
        body = await page.inner_text("body")
        listed = wo in body
        pre_badge = await page.locator(".layout-bell .el-badge__content").count()
        # 布局铃铛下拉与通知中心页各有一个「全部已读」：限定到页面工具栏，避免 strict mode violation
        await page.locator(".noti-toolbar button:has-text('全部已读')").click()
        await page.wait_for_timeout(2000)
        post_badge = await page.locator(".layout-bell .el-badge__content").count()
        await page.locator(f".noti-row:has-text('{wo}')").first.click()
        await page.wait_for_timeout(2000)
        return listed, pre_badge, post_badge, page.url, errors["api"]
    listed, pre_badge, post_badge, url, api = run_page(ctx, "t34-uinoti", u.password, fn)
    check(listed, "通知列表未出现「开始处理」通知")
    check(not api, f"接口 5xx：{api[:3]}")
    check(pre_badge >= 1, "有未读通知时红点未显示")
    check(post_badge == 0, f"全部已读后红点仍在：{post_badge}")
    check("/workorders" in url, f"点击通知未跳转工单页：{url}")


@case(S, "UI-08", "工单看板：拖拽流转生效，非法移动（处理中→已关闭）卡片回弹并提示", risk="v3.5 看板流转", level="P1")
def _(ctx):
    wo1 = ctx.workorder(wo_type="巡检", title="UI-08 拖拽流转")
    wo2 = ctx.workorder(wo_type="巡检", title="UI-08 非法移动")
    ctx.admin.post(f"/workorders/{wo2}/transitions", {"action": "开始处理", "opinion": "t"})

    async def fn(page, errors):
        await page.goto(ctx.ui_base + "/workorders")
        await page.wait_for_timeout(2000)
        await page.locator(".el-radio-button:has-text('看板')").click()
        await page.wait_for_timeout(3000)
        # 1) 合法流转：待处理 → 处理中
        card = page.locator(f".kb-card:has-text('{wo1}')").first
        target = page.locator(".kb-col:has-text('处理中')").first
        await card.drag_to(target)
        await page.wait_for_timeout(2500)
        st1 = await page.locator(f".kb-col:has-text('处理中') .kb-card:has-text('{wo1}')").count()
        # 2) 非法移动：处理中 → 已关闭（未映射动作，前端提示且卡片留在原列）
        card2 = page.locator(f".kb-card:has-text('{wo2}')").first
        target2 = page.locator(".kb-col:has-text('已关闭')").first
        await card2.drag_to(target2)
        await page.wait_for_timeout(1500)
        warn = await page.locator(".el-message:has-text('详情页关闭')").count()
        stay = await page.locator(f".kb-col:has-text('处理中') .kb-card:has-text('{wo2}')").count()
        return st1, warn, stay, errors["api"]
    st1, warn, stay, api = run_page(ctx, ctx.admin_user, ctx.admin_pw, fn)
    check(not api, f"接口 5xx：{api[:3]}")
    check(st1 == 1, "拖拽后卡片未出现在「处理中」列")
    check(warn >= 1, "非法移动未出现提示")
    check(stay == 1, "非法移动后卡片未回弹到「处理中」列")
    st, b = ctx.admin.get(f"/workorders/{wo1}")
    check((data(b) or {}).get("status") == "处理中", f"wo1 后端状态 {(data(b) or {}).get('status')}")
    st, b = ctx.admin.get(f"/workorders/{wo2}")
    check((data(b) or {}).get("status") == "处理中", f"wo2 后端状态 {(data(b) or {}).get('status')}")


@case(S, "UI-09", "网络拓扑：全量图渲染、图例可勾选、机房过滤不报错", risk="v3.5 拓扑页", level="P1")
def _(ctx):
    async def fn(page, errors):
        await page.goto(ctx.ui_base + "/topology")
        await page.wait_for_timeout(5000)
        canvas = await page.locator(".chart canvas").count()
        body = await page.inner_text("body")
        legend = await page.locator(".rel-filter .el-checkbox").count()
        if legend:
            await page.locator(".rel-filter .el-checkbox").first.click()
            await page.wait_for_timeout(1500)
            canvas2 = await page.locator(".chart canvas").count()
        else:
            canvas2 = canvas
        return canvas, canvas2, legend, body, errors["api"], errors["console"]
    canvas, canvas2, legend, body, api, js = run_page(ctx, ctx.admin_user, ctx.admin_pw, fn)
    check(canvas > 0, "ECharts canvas 未渲染")
    check(canvas2 > 0, "切换图例后 canvas 消失")
    check(legend > 0, "关系类型图例缺失")
    check("机房" in body and "资产" in body, "节点统计未显示")
    check(not api, f"接口 5xx：{api[:3]}")
    check(not js, f"脚本错误：{js[:3]}")

@case(S, "UI-TL-01", "v3.6 单资产时间线：页面渲染 el-timeline、条目带来源标识、来源筛选器切换生效（5.1.1-8a）",
      risk="时间线前端链路断裂（v3.6 fe-1）", level="P1")
def _(ctx):
    a = ctx.asset(name=f"t34-ui-tl-{uuid.uuid4().hex[:6]}")
    ctx.workorder(wo_type="巡检", title="t34-ui-tl 工单", assetNos=[a])

    async def fn(page, errors):
        await page.goto(ctx.ui_base + "/assets/lifecycle")
        await page.wait_for_timeout(2000)
        # AssetPicker 远程搜索选择资产
        picker = page.locator(".page-toolbar .el-select").first
        await picker.click()
        await page.keyboard.type(a, delay=20)
        await page.wait_for_timeout(1500)
        await page.locator(f".el-select-dropdown__item:visible:has-text('{a}')").first.click()
        await page.wait_for_timeout(2000)
        # 单资产时间线卡片渲染
        card = await page.locator(".asset-card").count()
        tl = await page.locator(".asset-card .el-timeline").count()
        items = await page.locator(".asset-card .el-timeline-item").count()
        # 条目带来源标识（TIMELINE_SOURCE_LABEL：台账/工单/财务/告警）
        body = await page.inner_text(".asset-card")
        source_tagged = any(lbl in body for lbl in ("台账", "工单", "财务", "告警"))
        # 来源筛选器：切换到「工单」只显示工单源条目
        selects = page.locator(".asset-card .el-select")
        await selects.first.click()
        await page.wait_for_timeout(800)
        await page.locator(".el-select-dropdown__item:visible:has-text('工单')").first.click()
        await page.wait_for_timeout(2000)
        filtered = await page.locator(".asset-card .el-timeline-item").count()
        body2 = await page.inner_text(".asset-card")
        filtered_tagged = "工单" in body2
        return card, tl, items, source_tagged, filtered, filtered_tagged, errors["api"], errors["console"]
    card, tl, items, source_tagged, filtered, filtered_tagged, api, js = run_page(ctx, ctx.admin_user, ctx.admin_pw, fn)
    check(card > 0, "选择资产后时间线卡片未出现")
    check(tl > 0 and items > 0, "el-timeline 未渲染条目")
    check(source_tagged, "时间线条目缺少来源标识")
    check(filtered > 0 and filtered_tagged, "来源筛选器切换后无工单条目")
    check(not api, f"接口 5xx：{api[:3]}")
    check(not js, f"脚本错误：{js[:3]}")
