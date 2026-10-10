"""安装包检查（静态）：脚本语法、编排文件、敏感文件、权限、版本一致性、已发布迁移未被改动、启动脚本选包逻辑。

需要 PKG_DIR 指向解压后的安装包根目录（含 install.sh）。
"""
import glob, hashlib, json, os, re, shutil, stat, subprocess, tempfile
from lib.framework import suite, case, setup, check, Skip

S = suite("deploy", "安装包检查", order=90)
HERE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


@setup(S)
def _setup(ctx):
    if not ctx.pkg_dir or not os.path.isfile(os.path.join(ctx.pkg_dir, "install.sh")):
        raise Skip("未设置 PKG_DIR（安装包根目录）")


def P(ctx, *parts):
    return os.path.join(ctx.pkg_dir, *parts)


_IS_NT = os.name == "nt"
_BASH: str | None = None


def _bash() -> str | None:
    """Windows 下优先找 Git Bash（MSYS 能处理 Windows 路径）；WSL bash 需要换算 /mnt/ 路径，直接排除。"""
    global _BASH
    if not _IS_NT:
        return "bash"
    if _BASH is None:
        for cand in (r"C:\Program Files\Git\bin\bash.exe", r"C:\Program Files\Git\usr\bin\bash.exe"):
            if os.path.isfile(cand):
                _BASH = cand
                break
        else:
            _BASH = ""
    return _BASH or None


def _shpath(p: str) -> str:
    """Windows 下把路径换成 Git Bash 可识别的正斜杠形式。"""
    return p.replace("\\", "/") if _IS_NT else p


@case(S, "DP-01", "全部 shell 脚本语法正确", level="P0")
def _(ctx):
    bash = _bash()
    if not bash:
        raise Skip("本机没有可用于语法检查的 bash（Windows 需安装 Git Bash）")
    bad = []
    files = [f for f in glob.glob(P(ctx, "**", "*.sh"), recursive=True) if "/source/" not in f] + [P(ctx, "install.sh"), P(ctx, "dcimctl")]
    for f in files:
        r = subprocess.run([bash, "-n", _shpath(f)], capture_output=True, text=True)
        if r.returncode != 0:
            bad.append(f"{os.path.relpath(f, ctx.pkg_dir)}: {r.stderr.strip()[:80]}")
    check(not bad, "; ".join(bad))
    return f"{len(files)} 个脚本"


@case(S, "DP-02", "docker-compose.yml 在两种 seccomp 设置下都能通过校验", level="P0")
def _(ctx):
    if not shutil.which("docker"):
        raise Skip("没有 docker 命令")
    with tempfile.TemporaryDirectory() as d:
        env = os.path.join(d, ".env")
        tpl = open(P(ctx, "deploy", ".env.example"), encoding="utf-8").read()
        for opt in ("seccomp:unconfined", "no-new-privileges:true"):
            open(env, "w", encoding="utf-8").write(re.sub(r"^SECCOMP_OPT=.*$", f"SECCOMP_OPT={opt}", tpl, flags=re.M))
            r = subprocess.run(["docker", "compose", "--env-file", env, "-f", P(ctx, "deploy", "docker-compose.yml"), "config", "-q"],
                               capture_output=True, text=True)
            check(r.returncode == 0, f"SECCOMP_OPT={opt}：{r.stderr.strip()[:200]}")


@case(S, "DP-03", "安装包里没有 .env、私钥、数据目录", risk="v3.2.0 安装包外泄", level="P0")
def _(ctx):
    bad = []
    for root, dirs, files in os.walk(ctx.pkg_dir):
        rel = os.path.relpath(root, ctx.pkg_dir)
        if rel.split(os.sep)[0] in ("data",) or "pgdata" in rel:
            bad.append(rel)
        for f in files:
            if f == ".env" or f.endswith((".key", ".p12", ".jks")) or f.startswith("id_rsa"):
                bad.append(os.path.join(rel, f))
    check(not bad, f"{bad[:8]}")


@case(S, "DP-04", "没有全局可写文件，脚本可执行", level="P1")
def _(ctx):
    if _IS_NT:
        # NTFS 经 os.stat 模拟出的权限位恒为 0666/0777，S_IWOTH 恒真、IXUSR 恒真，
        # 该检查只在 POSIX 文件系统上有意义（Linux 部署机 / CI 上仍然生效）
        raise Skip("Windows 文件系统无 POSIX 权限位，跳过（发布门禁在 Linux 上执行）")
    ww, noexec = [], []
    for root, dirs, files in os.walk(ctx.pkg_dir):
        for f in files:
            p = os.path.join(root, f)
            m = os.stat(p).st_mode
            if m & stat.S_IWOTH:
                ww.append(os.path.relpath(p, ctx.pkg_dir))
            if (f.endswith(".sh") or f in ("install.sh", "dcimctl")) and not m & stat.S_IXUSR and "/source/" not in p:
                noexec.append(os.path.relpath(p, ctx.pkg_dir))
    check(not ww, f"全局可写：{ww[:5]}")
    check(not noexec, f"不可执行：{noexec[:5]}")


@case(S, "DP-05", "版本号一致：VERSION、后端包名、.env.example、compose 默认值", level="P0")
def _(ctx):
    v = open(P(ctx, "VERSION")).read().strip()
    jars = [os.path.basename(j) for j in glob.glob(P(ctx, "artifacts", "backend", "dcim-platform-*.jar"))]
    check(jars == [f"dcim-platform-{v}.jar"], f"后端包 {jars}，VERSION={v}")
    env = open(P(ctx, "deploy", ".env.example"), encoding="utf-8").read()
    check(f"DCIM_VERSION={v}" in env, ".env.example 的 DCIM_VERSION 不一致")
    compose = open(P(ctx, "deploy", "docker-compose.yml"), encoding="utf-8").read()
    check(f"${{DCIM_VERSION:-{v}}}" in compose, "docker-compose.yml 默认镜像标签不一致")
    return f"v{v}"


@case(S, "DP-06", "已发布的数据库迁移未被改动（与登记的校验和一致）", risk="Flyway 校验失败导致无法启动", level="P0")
def _(ctx):
    reg = json.load(open(os.path.join(HERE, "data", "migration-checksums.json"), encoding="utf-8"))
    mig = glob.glob(P(ctx, "source", "backend", "resources", "db", "migration", "V*.sql"))
    check(mig, "安装包内找不到迁移脚本（source/backend/resources/db/migration）")
    cur = {os.path.basename(m): hashlib.sha256(open(m, "rb").read()).hexdigest() for m in mig}
    changed = [n for n, h in reg.items() if n in cur and cur[n] != h]
    missing = [n for n in reg if n not in cur]
    check(not changed and not missing, f"被改动 {changed}，缺失 {missing}")
    return f"已登记 {len(reg)} 个，当前 {len(cur)} 个"


@case(S, "DP-07", "启动脚本在新旧 jar 并存时选版本最高的", risk="覆盖解压后跑旧包（v3.3.1 根因）", level="P0")
def _(ctx):
    bash = _bash()
    if not bash:
        raise Skip("本机没有可用于执行脚本的 bash（Windows 需安装 Git Bash）")
    src = open(P(ctx, "deploy", "native", "run-backend.sh"), encoding="utf-8").read()
    with tempfile.TemporaryDirectory() as d:
        os.makedirs(os.path.join(d, "artifacts", "backend"))
        os.makedirs(os.path.join(d, "deploy", "native"))
        for v in ("3.2.0", "3.3.0", "3.10.1", "3.4.0"):
            open(os.path.join(d, "artifacts", "backend", f"dcim-platform-{v}.jar"), "w").close()
        test = re.sub(r"^\s*exec java .*$", '  echo "PICK=$JAR"; exit 0', src, flags=re.M)
        sp = os.path.join(d, "deploy", "native", "t.sh")
        open(sp, "w").write(test)
        r = subprocess.run([bash, _shpath(sp)], capture_output=True, text=True)
        check("dcim-platform-3.10.1.jar" in r.stdout, f"选中：{r.stdout.strip()} {r.stderr.strip()[:120]}")


@case(S, "DP-08", "Prometheus 未开启远程生命周期接口，热加载用 SIGHUP", risk="可被远程关停监控", level="P1")
def _(ctx):
    compose = open(P(ctx, "deploy", "docker-compose.yml"), encoding="utf-8").read()
    check(not re.search(r"^\s*-\s*--web\.enable-lifecycle", compose, re.M), "仍开启 --web.enable-lifecycle")
    ctl = open(P(ctx, "dcimctl"), encoding="utf-8").read()
    check("kill -s HUP dcim-prometheus" in ctl, "dcimctl 未改用 SIGHUP 热加载")
