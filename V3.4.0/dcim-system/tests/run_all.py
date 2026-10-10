#!/usr/bin/env python3
"""一键回归（v3.4）：按层次运行全部测试套件，输出控制台结果与 report.md / report.json / junit.xml。

用法：
  ADMIN_PW='...' python3 tests/run_all.py                         # 全部套件（缺条件的自动跳过）
  ADMIN_PW='...' python3 tests/run_all.py -s smoke,contract        # 指定套件
  ADMIN_PW='...' python3 tests/run_all.py -k PF- --level P0        # 按用例编号前缀 / 级别筛选
  python3 tests/run_all.py --list                                  # 只列出用例

退出码：有失败或错误时为 1，便于接入 CI 作为发布门禁。
环境变量见 tests/lib/context.py 顶部说明。
"""
import argparse, importlib, os, pkgutil, sys, time

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

from lib import framework  # noqa: E402
from lib.context import Context  # noqa: E402


def load_suites():
    import suites
    for m in pkgutil.iter_modules(suites.__path__):
        importlib.import_module(f"suites.{m.name}")
    return framework.all_suites()


def main():
    ap = argparse.ArgumentParser(description="智算资产管理平台回归测试")
    ap.add_argument("-s", "--suites", default="", help="逗号分隔的套件名（smoke,contract,permission,security,concurrency,performance,e2e,cluster,deploy）")
    ap.add_argument("-k", "--keyword", default="", help="只运行编号或标题包含该文本的用例")
    ap.add_argument("--level", default="", help="只运行指定级别，如 P0 或 P0,P1")
    ap.add_argument("-o", "--out", default=os.path.join(HERE, "out"), help="报告目录")
    ap.add_argument("--list", action="store_true", help="只列出用例")
    a = ap.parse_args()

    suites = load_suites()
    if a.suites:
        want = {x.strip() for x in a.suites.split(",") if x.strip()}
        suites = [s for s in suites if s.name in want]
    levels = {x.strip() for x in a.level.split(",") if x.strip()}

    def select(c):
        if a.keyword and a.keyword not in c.id and a.keyword not in c.title:
            return False
        return not levels or c.level in levels

    if a.list:
        for s in suites:
            print(f"\n{s.title}（{s.name}）")
            for c in s.cases:
                if select(c):
                    print(f"  {c.id:<7} {c.level}  {c.title}" + (f"  〔风险：{c.risk}〕" if c.risk else ""))
        return 0

    ctx = Context()
    meta = {"time": time.strftime("%Y-%m-%d %H:%M:%S"), "base": ctx.base}
    try:
        meta["version"] = ctx.version()
    except Exception as e:  # noqa
        meta["version"] = f"不可达：{e}"
    print(f"被测：{ctx.base}  后端版本：{meta['version']}  批次：{ctx.run_id}")
    t0 = time.time()
    results = framework.run(ctx, suites, select)
    counts = framework.write_reports(results, a.out, meta)
    print(f"\n合计 {len(results)} 项：通过 {counts['PASS']} · 失败 {counts['FAIL']} · 错误 {counts['ERROR']} · 跳过 {counts['SKIP']}"
          f"（{time.time() - t0:.0f} 秒）\n报告：{os.path.join(a.out, 'report.md')}")
    return 1 if counts["FAIL"] or counts["ERROR"] else 0


if __name__ == "__main__":
    sys.exit(main())
