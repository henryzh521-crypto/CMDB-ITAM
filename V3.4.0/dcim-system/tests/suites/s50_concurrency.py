"""并发：唯一键、乐观锁、状态流转、编号生成在并发下的正确性（不出现 500、不重复、不丢失）。

v3.5 增补：CC-07 覆盖 SSE 长连接的并发建连 / 心跳 / 广播 / 断开清理。
"""
import queue
import threading
import time
import uuid
from concurrent.futures import ThreadPoolExecutor

import requests as rq

from lib.framework import suite, case, check
from lib.context import data, msg

S = suite("concurrency", "并发正确性", order=50)


def _par(n, fn):
    with ThreadPoolExecutor(n) as ex:
        return list(ex.map(fn, range(n)))


@case(S, "CC-01", "8 路并发录入同一 SN：1 个成功，其余 409，无 500", risk="唯一键冲突返回 500（v3.3 H2）", level="P0")
def _(ctx):
    sn = "SN-CC-" + uuid.uuid4().hex[:10]
    codes = _par(8, lambda _: ctx.admin.post("/assets", {"name": "cc", "sn": sn, "categoryCode": "SRV"})[0])
    check(codes.count(200) == 1 and codes.count(409) == 7, f"状态码 {sorted(codes)}")


@case(S, "CC-02", "同一版本号并发修改资产：只有 1 个成功，其余 409", risk="并发覆盖（丢失更新）", level="P0")
def _(ctx):
    a = ctx.asset()
    st, b = ctx.admin.get(f"/assets/{a}")
    cur = data(b)
    base = {k: cur.get(k) for k in ("name", "categoryCode", "ownerType", "version")}
    codes = _par(8, lambda i: ctx.admin.put(f"/assets/{a}", {**base, "name": f"cc-{i}"})[0])
    check(codes.count(200) == 1 and 500 not in codes, f"状态码 {sorted(codes)}")


@case(S, "CC-03", "8 路并发流转同一工单：只有 1 个成功，无 500", level="P1")
def _(ctx):
    wo = ctx.workorder()
    codes = _par(8, lambda _: ctx.admin.post(f"/workorders/{wo}/transitions", {"action": "开始处理", "opinion": "cc"})[0])
    check(codes.count(200) == 1 and 500 not in codes, f"状态码 {sorted(codes)}")
    st, b = ctx.admin.get(f"/workorders/{wo}")
    flows = [f for f in (data(b).get("flows") or []) if f.get("action") == "开始处理"]
    check(len(flows) == 1, f"流转记录 {len(flows)} 条")


@case(S, "CC-04", "8 路并发给同一工单添加同一资产：只建 1 行", level="P1")
def _(ctx):
    a = ctx.asset()
    wo = ctx.workorder()
    codes = _par(8, lambda _: ctx.admin.post(f"/workorders/{wo}/items", {"assetNo": a})[0])
    st, b = ctx.admin.get(f"/workorders/{wo}")
    items = data(b).get("items") or []
    check(len(items) == 1 and 500 not in codes, f"行项目 {len(items)} 条，状态码 {sorted(codes)}")


@case(S, "CC-05", "40 路并发建单（超过连接池默认 20）：全部成功且工单号不重复", risk="连接池饥饿（v3.4 测试发现：事件监听另借连接）", level="P0")
def _(ctx):
    res = _par(40, lambda _: ctx.admin.post("/workorders", {"woType": "巡检", "title": "cc 并发建单"}))
    codes = [st for st, _ in res]
    nos = [data(b)["woNo"] for st, b in res if st == 200]
    check(codes.count(200) == 40, f"状态码 {sorted(codes)}", "；".join(sorted({msg(b)[:60] for st, b in res if st != 200}))[:300])
    check(len(set(nos)) == 40, f"工单号重复：{len(nos) - len(set(nos))} 个")


@case(S, "CC-06", "有 Webhook 订阅时 30 路并发建单：全部成功，每张工单恰好一条投递记录", risk="事务性发件箱：丢事件 / 重复投递 / 连接池饥饿", level="P0")
def _(ctx):
    st, b = ctx.admin.post("/webhooks", {"name": f"t34-outbox-{ctx.run_id}", "url": "http://203.0.113.10:9/hook",
                                          "eventTypes": ["workorder.created"], "enabled": True})
    check(st == 200, f"创建 Webhook HTTP {st} {msg(b)}")
    hid = data(b)["id"]
    try:
        res = _par(30, lambda _: ctx.admin.post("/workorders", {"woType": "巡检", "title": "cc 发件箱"}))
        codes = [st for st, _ in res]
        check(codes.count(200) == 30, f"状态码 {sorted(codes)}")
        nos = sorted(data(b)["woNo"] for st, b in res if st == 200)
        st, d = ctx.admin.get(f"/webhooks/{hid}/deliveries", params={"page": 1, "size": 100})
        rows = (data(d) or {}).get("records") or (data(d) or {}).get("list") or []
        got = sorted((r.get("payload") or {}).get("objId") for r in rows if r.get("eventType") == "workorder.created")
        check(got == nos, f"投递记录 {len(got)} 条，工单 {len(nos)} 张",
              f"缺 {sorted(set(nos) - set(got))[:3]} 多 {sorted(set(got) - set(nos))[:3]}")
        return f"{len(got)} 条投递记录与 {len(nos)} 张工单一一对应"
    finally:
        ctx.admin.delete(f"/webhooks/{hid}")


@case(S, "CC-07", "20 路 SSE 并发：全部建连成功、收到心跳与广播、断开后连接可回收再建", risk="SSE 连接泄漏 / 广播丢帧（v3.5）", level="P0")
def _(ctx):
    url = f"{ctx.base}/notifications/sse"
    tok = ctx.admin.s.cookies.get("access_token")
    check(bool(tok), "登录会话缺少 access_token cookie")

    def open_stream():
        return rq.get(url, params={"token": tok}, stream=True, timeout=(10, 90))

    def first_line(lines):
        t0 = time.time()
        for line in lines:
            if line:
                return line
            if time.time() - t0 > 8:
                return ""
        return ""

    streams = _par(20, lambda _: open_stream())
    try:
        bad = [s.status_code for s in streams if s.status_code != 200]
        check(not bad, f"20 路建连存在非 200：{bad[:5]}")
        # 每条流只建一个 iter_lines 生成器并全程复用：iter_lines 不可重入，
        # 首个生成器被 GC 时会把 urllib3 底层连接一并关闭，第二次调用只能读到空流
        lines = [s.iter_lines(decode_unicode=True) for s in streams]
        heads = _par(20, lambda i: first_line(lines[i]))
        miss = [i for i, h in enumerate(heads) if "connected" not in h]
        check(not miss, f"{len(miss)} 条流未收到 connected 帧：{[heads[i] for i in miss[:3]]}")

        # 心跳（默认 25s 一帧 :ping 注释）与「建连-断开」回收循环并行进行
        hb = queue.Queue()

        def read_heartbeat(lines):
            try:
                t0 = time.time()
                for line in lines:
                    if line and line.startswith(":"):
                        hb.put(line)
                        return
                    if time.time() - t0 > 40:
                        hb.put("")
                        return
            except Exception as e:  # noqa
                hb.put(f"EXC:{e}")

        th = threading.Thread(target=read_heartbeat, args=(lines[0],), daemon=True)
        th.start()
        for _ in range(2):   # 两轮「20 建连 + 全断开」：连接若未清理，注册表将逼近 200 上限
            time.sleep(1.5)
            again = _par(20, lambda _: open_stream())
            for s in again:
                s.close()
        beat = hb.get(timeout=45)
        check(beat == ":ping", f"心跳帧异常：{beat!r}")

        st, b = ctx.admin.post("/workorders", {"woType": "巡检", "title": f"CC-07 SSE 广播 {ctx.run_id}"})
        check(st == 200, f"建单 HTTP {st} {msg(b)}")

        def recv_broadcast(lines):
            t0 = time.time()
            for line in lines:
                if line and line.startswith("data:") and '"channel":"workorder"' in line.replace(" ", ""):
                    return True
                if time.time() - t0 > 10:
                    return False
            return False

        got = _par(3, lambda i: recv_broadcast(lines[1 + i]))
        check(all(got), f"3 条抽样流仅 {sum(got)} 条收到 workorder 频道广播")
        return "20 建连 + 心跳 + 2×20 建断回收 + 3/3 广播全部正常"
    finally:
        for s in streams:
            s.close()
    # 断开全部后：仍可新建（连接已回收，未耗尽实例上限）
    tail = open_stream()
    try:
        check(tail.status_code == 200, f"回收后再建连 HTTP {tail.status_code}")
    finally:
        tail.close()
