#!/usr/bin/env python3
"""把已发布的数据库迁移登记进 tests/data/migration-checksums.json（发布新版本时运行一次）。

    python3 tests/tools/register_migrations.py [迁移目录]

默认目录依次尝试 backend-resources/db/migration、source/backend/resources/db/migration。
只追加新文件；已登记的文件如果内容变了，直接报错退出 —— 已发布的迁移不允许改动，应新增 V{n+1}。
"""
import hashlib, json, os, sys

HERE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
REG = os.path.join(HERE, "data", "migration-checksums.json")
ROOT = os.path.dirname(HERE)


def version(name):
    head = name[1:].split("__", 1)[0]
    return tuple(int(x) for x in head.replace("_", ".").split(".") if x.isdigit())


def main():
    cands = sys.argv[1:] or [os.path.join(ROOT, "backend-resources", "db", "migration"),
                             os.path.join(ROOT, "source", "backend", "resources", "db", "migration")]
    d = next((c for c in cands if os.path.isdir(c)), None)
    if not d:
        sys.exit(f"找不到迁移目录：{cands}")
    reg = json.load(open(REG, encoding="utf-8")) if os.path.exists(REG) else {}
    cur = {f: hashlib.sha256(open(os.path.join(d, f), "rb").read()).hexdigest()
           for f in os.listdir(d) if f.startswith("V") and f.endswith(".sql")}
    changed = [f for f in reg if f in cur and cur[f] != reg[f]]
    if changed:
        sys.exit(f"已发布的迁移被改动：{changed}（请撤销改动，改为新增迁移）")
    added = sorted(set(cur) - set(reg), key=version)
    reg.update({f: cur[f] for f in added})
    os.makedirs(os.path.dirname(REG), exist_ok=True)
    with open(REG, "w", encoding="utf-8") as f:
        json.dump(dict(sorted(reg.items(), key=lambda kv: version(kv[0]))), f, indent=1)
    print(f"已登记 {len(reg)} 个迁移，本次新增 {added or '无'}")


if __name__ == "__main__":
    main()
