"""极简测试框架（v3.4）：用例注册、断言、跳过、计时与多格式报告。

不依赖 pytest，只用标准库 + requests，便于在内网 / 离线环境直接运行。

    from lib.framework import suite, case, check, skip

    S = suite("permission", "权限矩阵", order=30)

    @case(S, "P-DEL-01", "未授予删除的角色删除资产返回 403", risk="越权删除", level="P0")
    def _(ctx):
        st, body = ctx.client("fld1").delete("/assets/X")
        check(st == 403, f"HTTP {st}")
"""
import json, os, time, traceback, html
from dataclasses import dataclass, field
from xml.sax.saxutils import escape


class Skip(Exception):
    pass


class CheckFailed(AssertionError):
    pass


def skip(reason):
    raise Skip(reason)


def check(cond, message="", detail=None):
    """断言；失败时抛出 CheckFailed，消息进入报告"""
    if not cond:
        raise CheckFailed(message + (f" | {detail}" if detail is not None else ""))


def check_eq(actual, expected, message=""):
    check(actual == expected, f"{message} 期望 {expected!r}，实际 {actual!r}")


@dataclass(eq=False)
class Case:
    suite: "Suite"
    id: str
    title: str
    risk: str
    level: str
    tags: tuple
    fn: object


@dataclass(eq=False)
class Suite:
    name: str
    title: str
    order: int
    cases: list = field(default_factory=list)
    setup: object = None


_SUITES = {}


def suite(name, title, order=50):
    s = _SUITES.get(name) or Suite(name, title, order)
    _SUITES[name] = s
    return s


def case(s, cid, title, risk="", level="P1", tags=()):
    def deco(fn):
        s.cases.append(Case(s, cid, title, risk, level, tuple(tags), fn))
        return fn
    return deco


def setup(s):
    """套件级前置：失败则整套跳过"""
    def deco(fn):
        s.setup = fn
        return fn
    return deco


def all_suites():
    return sorted(_SUITES.values(), key=lambda x: (x.order, x.name))


@dataclass
class Result:
    case: Case
    status: str  # PASS / FAIL / ERROR / SKIP
    seconds: float
    detail: str = ""


def run(ctx, suites, select=None, out=print):
    results = []
    for s in suites:
        cases = [c for c in s.cases if not select or select(c)]
        if not cases:
            continue
        out(f"\n━━ {s.title}（{s.name}，{len(cases)} 项）")
        if s.setup:
            try:
                s.setup(ctx)
            except Skip as e:
                for c in cases:
                    results.append(Result(c, "SKIP", 0.0, f"套件跳过：{e}"))
                out(f"  [跳过] 整套：{e}")
                continue
            except Exception as e:  # noqa
                for c in cases:
                    results.append(Result(c, "ERROR", 0.0, f"套件前置失败：{e}"))
                out(f"  [错误] 套件前置失败：{e}")
                continue
        for c in cases:
            t0 = time.perf_counter()
            try:
                note = c.fn(ctx)
                r = Result(c, "PASS", time.perf_counter() - t0, note or "")
            except Skip as e:
                r = Result(c, "SKIP", time.perf_counter() - t0, str(e))
            except CheckFailed as e:
                r = Result(c, "FAIL", time.perf_counter() - t0, str(e))
            except Exception as e:  # noqa
                tb = traceback.format_exc(limit=3)
                r = Result(c, "ERROR", time.perf_counter() - t0, f"{type(e).__name__}: {e}\n{tb}")
            results.append(r)
            mark = {"PASS": "通过", "FAIL": "失败", "ERROR": "错误", "SKIP": "跳过"}[r.status]
            line = f"  [{mark}] {c.id} {c.title}"
            if r.status != "PASS" or r.detail:
                line += f" —— {r.detail.splitlines()[0] if r.detail else ''}"
            out(line[:400])
    return results


def summarize(results):
    counts = {k: 0 for k in ("PASS", "FAIL", "ERROR", "SKIP")}
    for r in results:
        counts[r.status] += 1
    return counts


def write_reports(results, out_dir, meta):
    os.makedirs(out_dir, exist_ok=True)
    counts = summarize(results)
    # JSON
    data = {
        "meta": meta, "summary": counts,
        "results": [{"suite": r.case.suite.name, "id": r.case.id, "title": r.case.title, "level": r.case.level,
                     "risk": r.case.risk, "status": r.status, "seconds": round(r.seconds, 3), "detail": r.detail}
                    for r in results],
    }
    with open(os.path.join(out_dir, "report.json"), "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=1)
    # JUnit XML（CI 平台通用）
    by_suite = {}
    for r in results:
        by_suite.setdefault(r.case.suite, []).append(r)
    xml = ['<?xml version="1.0" encoding="UTF-8"?>', f'<testsuites name="dcim" tests="{len(results)}" failures="{counts["FAIL"]}" errors="{counts["ERROR"]}" skipped="{counts["SKIP"]}">']
    for s, rs in by_suite.items():
        f_ = sum(1 for r in rs if r.status == "FAIL")
        e_ = sum(1 for r in rs if r.status == "ERROR")
        k_ = sum(1 for r in rs if r.status == "SKIP")
        xml.append(f'  <testsuite name="{escape(s.name)}" tests="{len(rs)}" failures="{f_}" errors="{e_}" skipped="{k_}" time="{sum(r.seconds for r in rs):.3f}">')
        for r in rs:
            xml.append(f'    <testcase classname="{escape(s.name)}" name="{escape(r.case.id + " " + r.case.title)}" time="{r.seconds:.3f}">')
            if r.status == "FAIL":
                xml.append(f'      <failure message="{escape(r.detail[:200], {chr(34): "&quot;"})}">{escape(r.detail)}</failure>')
            elif r.status == "ERROR":
                xml.append(f'      <error message="{escape(r.detail[:200], {chr(34): "&quot;"})}">{escape(r.detail)}</error>')
            elif r.status == "SKIP":
                xml.append(f'      <skipped message="{escape(r.detail[:200], {chr(34): "&quot;"})}"/>')
            xml.append('    </testcase>')
        xml.append('  </testsuite>')
    xml.append('</testsuites>')
    with open(os.path.join(out_dir, "junit.xml"), "w", encoding="utf-8") as f:
        f.write("\n".join(xml))
    # Markdown（人读）
    md = [f"# 回归测试报告", "", f"- 时间：{meta.get('time')}", f"- 被测：{meta.get('base')}（后端 {meta.get('version')}）",
          f"- 结果：通过 {counts['PASS']} · 失败 {counts['FAIL']} · 错误 {counts['ERROR']} · 跳过 {counts['SKIP']}", ""]
    for s, rs in by_suite.items():
        md.append(f"## {s.title}（{s.name}）")
        md.append("")
        md.append("| 编号 | 用例 | 级别 | 结果 | 说明 |")
        md.append("|---|---|---|---|---|")
        for r in rs:
            mark = {"PASS": "✅ 通过", "FAIL": "❌ 失败", "ERROR": "⚠️ 错误", "SKIP": "⏭ 跳过"}[r.status]
            detail = (r.detail.splitlines()[0] if r.detail else "").replace("|", "\\|")[:160]
            md.append(f"| {r.case.id} | {r.case.title} | {r.case.level} | {mark} | {detail} |")
        md.append("")
    with open(os.path.join(out_dir, "report.md"), "w", encoding="utf-8") as f:
        f.write("\n".join(md))
    return counts
