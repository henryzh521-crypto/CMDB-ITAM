# 回归测试套件（v3.5）

一条命令跑完 9 层、95 个用例，输出控制台结果和三种报告（`report.md` 给人看，`junit.xml` 给 CI，`report.json` 给脚本）。
只依赖 Python 3.9+ 和 `requests`；浏览器用例另需 `playwright`，数据库相关用例另需 `psql`。条件不满足的套件或用例会**跳过**，不会报错。

```bash
pip install requests                     # 必需
pip install playwright                   # 可选：浏览器端到端（e2e）

export ADMIN_PW='管理员口令'
python3 tests/run_all.py                 # 全部套件
python3 tests/run_all.py -s smoke,contract,permission,security,concurrency   # 发布门禁（约 1 分钟）
python3 tests/run_all.py -k PF- --level P0                                   # 按编号前缀 / 级别筛选
python3 tests/run_all.py --list          # 只列出用例
```

退出码：有失败或错误时为 1，可直接作为 CI 发布门禁。

## 环境变量

| 变量 | 默认值 | 说明 |
|---|---|---|
| `BASE` | `http://127.0.0.1:8081/api` | 被测后端（建议直连后端端口：测试用 `X-Forwarded-For` 模拟多个来源 IP，后端只信任本机 / 内网代理发来的该头） |
| `ADMIN_USER` / `ADMIN_PW` | `admin` / 无 | 管理员账号；`ADMIN_PW` 必填 |
| `UI_BASE` | `http://127.0.0.1:4173` | 前端地址（e2e 套件） |
| `BASE2` | 无 | 第二个后端实例，与 `BASE` 共用同一数据库和 Redis（cluster 套件） |
| `PGHOST` `PGUSER` `PGPASSWORD` `PGDATABASE` | 无 | 数据库直连（CL-06 读取 `job_lock`） |
| `SLOW` | 无 | 设为 `1` 才运行耗时约 3 分钟的 CL-06 |
| `PKG_DIR` | 无 | 解压后的安装包根目录（deploy 套件） |
| `EXPECT_VERSION` | 无 | 设置后 SM-01 校验运行版本（升级后防止跑的仍是旧包） |
| `PERF_FACTOR` | `1` | 性能预算倍数，较慢的测试机可设 `2` |
| `PERF_MIN_ASSETS` | `50000` | 资产少于此数时跳过性能套件；可用 `seed_scale.sql` 灌数 |

## 套件

| 套件 | 编号 | 内容 | 前置条件 |
|---|---|---|---|
| smoke 冒烟 | SM | 健康检查、版本、登录、全部无参 GET 不出 5xx、未登录 401 | 后端 |
| contract 接口契约 | CT | OpenAPI 完整性、写接口权限声明、统一响应结构、11 种关联资产写法（assetNos 回归）；v3.5：新事件目录、盘点差异工单、低库存联动、SLA 升级投递、SSE 帧契约；v3.5.2：bmc_ip 唯一约束并发兜底 | 后端 |
| permission 权限矩阵 | PM/PA/PS/PF/PC | 模块读写、删除显式授予、导出、数据范围、字段级权限、授权上限 | 后端 |
| security 安全回归 | SE | 越权、会话吊销、令牌重放、用户名枚举、账号锁定、限流、审计、SSRF | 后端 |
| concurrency 并发正确性 | CC | 唯一键、乐观锁、状态流转、编号生成、连接池、事务性发件箱；v3.5：SSE 并发建连/心跳/广播/回收 | 后端 |
| performance 性能基线 | PERF | 8 个关键接口的延迟预算 + 多用户吞吐；v3.5：SSE 20 连接常开下的列表延迟 | ≥5 万资产 |
| e2e 浏览器端到端 | UI | 登录、界面建单落库、字段受限界面、角色字段权限编辑、接口文档、暗色模式；v3.5：通知中心、看板拖拽回弹、网络拓扑 | 前端 + playwright |
| cluster 多实例一致性 | CL | 跨实例会话吊销、账号停用、登录计数、限流共享、定时任务互斥；v3.5：SSE 跨实例送达、补货单跨实例幂等；v3.5.2：PAT 吊销跨实例即时生效 | `BASE2`（CL-07/09 另需 Redis，CL-08 另需 ticket 模式） |
| deploy 安装包检查 | DP | 脚本语法、编排校验、敏感文件、权限、版本一致、迁移未改动、选包逻辑、监控热加载 | `PKG_DIR` |

## 测试数据

- 用例创建的角色以 `R34-` 开头、用户以 `t34-` 开头、资产名称以 `t34-` 开头，可按前缀清理；不会修改既有业务数据。
- 角色和用户每次运行都会复位到用例需要的状态，重复运行结果一致。
- 不要对生产库运行 concurrency / security 套件：它们会故意制造锁定账号、限流和大量测试工单。
- v3.5 联动用例（CT-13 / CL-08）：默认部署运行在 notify 模式，验证站内信路径即可；要验证自动补货单与幂等，须以 `-Ddcim.linkage.low-stock.mode=ticket`（或环境变量）启动被测实例后重跑。

## 新增用例

在 `suites/` 下对应文件里加一个函数即可，编号在套件内唯一：

```python
@case(S, "PF-11", "隐藏字段不出现在资产树接口里", risk="借旁路接口反推隐藏值", level="P1")
def _(ctx):
    ctx.role("R34-FLD2", {"assets": "r"}, fields={"assets": {"hidden": ["sn"]}})
    u = ctx.user("t34-fld2", ["R34-FLD2"])
    st, body = u.get("/assets/tree")
    check(st == 200 and '"sn"' not in json.dumps(body), f"HTTP {st}")
```

- `ctx.admin` 管理员会话；`ctx.role()` / `ctx.user()` 准备角色与用户；`ctx.asset()` / `ctx.workorder()` 造数据；`ctx.sql()` 直连数据库（未配置时返回 `None`）。
- `check(条件, 失败说明)` 断言；`skip(原因)` 跳过；返回字符串会写进报告的「说明」列。
- 新的数据库迁移随版本发布后，运行 `python3 tests/tools/register_migrations.py` 把校验和登记进 `data/migration-checksums.json`，此后任何人改动已发布迁移都会被 DP-06 拦下。

## 文件

```
run_all.py              入口
lib/framework.py        用例注册、断言、报告（JSON / JUnit / Markdown）
lib/context.py          会话、测试数据工厂、数据库直连
suites/s10…s90          9 个套件
data/migration-checksums.json   已发布迁移的 SHA-256 登记
tools/register_migrations.py    登记新迁移
seed_scale.sql          性能测试灌数（10 万资产）
```
