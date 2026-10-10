# 智算资产管理平台 v3.5.2 升级说明

v3.5.2 是 **P3 稳定性与安全加固版**：落地 v3.5.1 回归报告中列出的 8 项遗留 P3 加固事项中的 6 项编码修复（定时任务锁租约上限、带外端点并发去重兜底、XFF 可信代理收敛、PAT 吊销跨实例即时生效、硬件快照保留策略、监控栈镜像摘要锁定），另有 2 项（Webhook DNS rebinding、Tomcat 升级）经评估维持现有缓解、暂不改动。
**无新功能**；含 1 个自动执行的数据库迁移（V47 快照清理支撑索引）。全部改动经 95 个自动化用例（9 个套件）回归通过，修复清单与结果见 `docs/TEST-REPORT-3.5.2.md`。

> 从 v3.5.0 / v3.5.1 升级的：v3.5.2 为 P3 稳定性加固版，直接升级即可。
> 从 v3.4.x 及更早版本升级的，请先按 `docs/UPGRADE-3.5.md` 升级到 v3.5.0，再依次升 v3.5.1、v3.5.2（SSE 反代配置等注意事项见该文档第一节）。

---

## 一、升级步骤

### Docker 部署

```bash
# 在旧安装目录
./dcimctl backup                                   # 先手工备份一次
./dcimctl upgrade /path/to/dcim-system-3.5.2     # 复制 .env/证书 → 自动备份 → 构建 → 启动
```

- 启动时自动执行 V47 迁移（`hw_snapshot` 时间索引，秒级完成，无锁表风险）。
- `.env` 会被沿用；本版新增变量均有代码默认值，无需手工添加（见第二节）。
- 安装/升级完成后，`install.sh` 自动把实际拉取的外部镜像（postgres/redis/tdengine/caddy/prometheus 等）锁定为镜像摘要写入 `.env` 的 `*_IMAGE` 变量，防止日后重建容器时被 registry 端覆盖 tag 悄悄换版本。

### 原生部署（systemd）

替换 `artifacts/` 后执行 `./install.sh --native`。启动脚本会在多个 jar 并存时选择**版本号最高**的一个。

### 升级后自检

```bash
ADMIN_PW='<admin口令>' EXPECT_VERSION=3.5.2 python3 tests/run_all.py -s smoke
```

界面右上角「关于本系统」应显示前后端版本 3.5.2。

---

## 二、行为变化（请通知用户）

| 变化 | 说明 |
|---|---|
| 带外端点并发创建 409 | 多路并发创建同一 `bmcIp` 的带外端点时，恰好一路成功，其余返回 409 并提示已存在（原 check-then-act 竞态下可能返回 500）；顺序重复创建同样 409 |
| PAT 吊销跨实例即时生效 | 多实例部署下吊销 API Token，其余实例通过 Redis pub/sub 广播 3 秒内同步拒绝（原最长存在 60 秒缓存窗口）；单实例与未接 Redis 的部署不受影响 |
| 定时任务锁租约上限 | 集群锁心跳续租新增上限（默认 `DCIM_SCHEDULER_LOCK_MAX_LEASE=1800` 秒）：持锁实例被强杀后，锁至多在上限内自愈释放，不再出现「已死实例的锁无限续租、阻塞任务数十分钟」 |
| 硬件快照自动清理 | `hw_snapshot` 新增保留策略（默认 `DCIM_COLLECTOR_SNAPSHOT_RETENTION_DAYS=180` 天，0 = 不清理）：每日 02:40 分批删除超期快照；首次启动自动建 V47 时间索引 |
| XFF 信任收敛 ⚠ | 默认可信代理网段从全部 RFC1918 收敛为「回环 + `172.16.0.0/12`（Docker 网桥）」。Docker 部署不受影响；**原生部署且网关与后端同处 `10.x` / `192.168.x` 网段的，须在 `.env` 显式配置 `DCIM_TRUSTED_PROXIES`（加入网关网段），否则审计 IP 会记成网关地址**。请勿把客户端可达网段加入该值 |
| 镜像摘要锁定 | 外部镜像不再随 tag 漂移：安装/升级后 `install.sh` 把实际拉取的镜像摘要写入 `.env`。升级镜像版本：删除对应 `*_IMAGE` 行后重新拉取 |
| 调度锁租约上限可调 | `DCIM_SCHEDULER_LOCK_MAX_LEASE` 须大于最慢定时任务的执行时长，默认 1800 秒 |

其余修复（调度锁持有者存活检测、`ClientIp` 标准 XFF 解析算法、compose 镜像变量化等）不改变正常操作路径下的可感知行为。

---

## 三、回滚

本版含 DDL（V47 索引）。回滚到 v3.5.1 前须先移除迁移登记，否则旧版 Flyway 校验会因「存在未解析的已应用迁移 47」拒绝启动：

```bash
# 1) 恢复备份目录（或 artifacts/ 换回旧 jar）之前，先清掉 V47 登记与索引
docker exec -i dcim-postgres psql -U dcim -d platform <<'SQL'
DROP INDEX IF EXISTS idx_hw_snapshot_collected;
DELETE FROM flyway_schema_history WHERE version = '47';
SQL
# 2) 换回旧目录 / 旧 jar 后重启
```

原生部署把 `psql` 换成本机 PostgreSQL 客户端即可。曾显式配置的新增 `.env` 变量（`DCIM_TRUSTED_PROXIES` 等）回滚后保留不影响运行；`*_IMAGE` 摘要锁定行可留可删。

---

## 四、源码、补丁与测试

- `source/backend/`：完整后端源码（v3.5.2）
- `patches/v3.5.1-to-v3.5.2/`：相对 v3.5.1 的补丁（后端 / 前端 / 部署 / 测试），路径映射见其中 README
- `tests/`：回归测试套件 95 用例（新增 CT-17 带外端点并发唯一约束、CL-09 PAT 吊销跨实例生效），用法见 `tests/README.md`
- 本版回归报告：`docs/TEST-REPORT-3.5.2.md`
