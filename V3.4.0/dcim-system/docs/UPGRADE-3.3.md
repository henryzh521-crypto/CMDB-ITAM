# 智算资产管理平台 v3.3.0 升级说明

v3.3.0 是一次以**安全修复与稳定性**为主的版本，同时补齐了 v3.2 中“配置了但没生效”的几项功能。
所有修复都用自动化回归脚本（`tests/`）在 10 万资产规模的数据上验证过。

---

## 一、⚠ 必读：v3.2.0 安装包密钥外泄

v3.2.0 安装包误把一套真实环境的配置与数据一起打了进去：

| 外泄内容 | 影响 |
|---|---|
| `deploy/.env`（JWT_SECRET、DCIM_CRYPTO_KEY、DCIM_AGENT_KEY、MONITORING_TOKEN、PG/Redis/MinIO/TDengine/Grafana 口令、管理员初始口令） | 持有安装包者可**伪造任意用户的登录令牌**、解密已存的 BMC 凭据、冒充采集器上报数据 |
| `deploy/tls/server.key` | 192.168.24.133 的 HTTPS 私钥 |
| `data/`（pgdata、redis、minio、prometheus、tdengine 日志等 242MB） | 数据库全量内容（管理员密码哈希、审计日志、工单、告警等） |

而且 v3.2 的 `install.sh` 发现 `.env` 已存在就会沿用，**用该包装出的每一套环境都共用同一组密钥**。

**v3.3 的处理：**

1. 安装包不再包含任何 `.env`、证书、数据目录；文件权限从 0666/0777 收紧为 0644/0755。
2. 后端内置外泄密钥的 SHA-256 指纹（只存摘要），生产模式下发现仍在使用会**拒绝启动**并提示轮换。
3. `install.sh` 升级时自动检测，确认后一键轮换；也可随时执行：

```bash
./dcimctl rotate-secrets          # 只轮换与外泄值相同的密钥
./dcimctl rotate-secrets --all    # 强制轮换全部应用密钥
```

自动轮换范围：JWT_SECRET（全员重新登录）、DCIM_CRYPTO_KEY（旧值移入 `DCIM_CRYPTO_KEY_OLD`，后端启动时自动把
BMC 凭据 / Webhook 签名密钥重加密为新密钥）、DCIM_AGENT_KEY（异地采集器需同步新值）、MONITORING_TOKEN（令牌文件自动同步）、
Redis 口令、PostgreSQL 口令（通过运行中的 dcim-postgres 执行 `ALTER USER`）。

需要**手工**轮换的三项：

```bash
# MinIO：修改 .env 中 MINIO_SECRET_KEY 后重建容器（root 凭据随环境变量生效；
#        若 MinIO 日志提示凭据无法解密旧配置，按 MinIO 官方的 root 凭据轮换步骤处理）
docker compose --env-file deploy/.env -f deploy/docker-compose.yml up -d --force-recreate minio platform-be
# TDengine：先用旧口令登录改成新口令，再更新 .env 的 TDENGINE_PASSWORD 并重建 platform-be
docker exec -it dcim-tdengine taos -uroot -p'<旧口令>' -s "ALTER USER root PASS '<新口令>';"
# Grafana（仅启用 grafana 组件时）：口令存在 Grafana 自身数据库，环境变量只在首次启动生效
docker exec -it dcim-grafana grafana cli admin reset-admin-password '<新口令>'
```

TLS：升级时若发现旧环境使用的是随安装包外泄的私钥，`install.sh` / `dcimctl upgrade` 不再沿用它，自动重新生成自签证书；
如该证书曾由内部 CA 签发，请同时在 CA 上吊销。

确实无法立即轮换时，可在 `deploy/.env` 临时设置 `DCIM_ALLOW_LEAKED_SECRETS=true` 启动，但请尽快处理。

---

## 二、升级步骤

### Docker 部署

```bash
# 在旧安装目录
./dcimctl backup                                 # 先手工备份一次
./dcimctl upgrade /path/to/dcim-system-3.3.0     # 复制 .env/证书 → 自动备份 → 构建 → 启动
```

首次启动执行数据库迁移 V43、V44（10 万资产约 10~30 秒，主要是新建索引）。
升级时如检测到外泄密钥，会提示确认轮换（`--yes` 时自动轮换）。

### 原生部署（systemd）

替换 `artifacts/` 后执行 `./install.sh --native`；如 `/etc/dcim/platform.env` 中的密钥与外泄值相同，安装程序会提示，
需手工更换（加密密钥：把旧值填入 `DCIM_CRYPTO_KEY_OLD`、新值填入 `DCIM_CRYPTO_KEY` 后重启）。

### 回滚

数据库迁移只新增列 / 表 / 索引并修正 `try_inet` 函数，v3.2 后端可在 V43/V44 之后的库上继续运行
（Flyway 默认忽略“未来版本”的迁移）。回滚时把旧目录的 `deploy/.env` 换回即可；若已轮换密钥，旧后端需使用新密钥。

---

## 三、行为变化（请通知用户）

| 变化 | 说明 |
|---|---|
| 退出登录立即生效 | 只作废当前终端的会话；此前退出后旧令牌 2 小时内仍可用 |
| 改密 / 管理员重置密码 / 停用 / 删除账号 | 该账号所有终端立即下线 |
| 授权天花板 | 非管理员不能授予 ADMIN 角色、不能修改/停用/重置管理员账号、不能授予超出自身的权限、不能修改内置角色 |
| 数据范围生效 | 角色的「数据范围」对工单、资产真正生效（全部 / 本部门 / 仅本人）。现有角色均为“全部”，升级后行为不变 |
| 健康检查 | 只有数据库不可用才返回 503；Redis 不可用返回 200 + `"status":"degraded"`，不再导致全站 502。匿名访问不再返回 JVM / 连接池明细 |
| Webhook | 默认禁止推送到平台自身所在的容器网段；监控栈容器（prometheus / alertmanager / grafana 等）加入禁止名单 |
| 并发冲突 | 唯一键冲突返回 409「数据已存在（字段=值）」，锁等待 / 超时返回 503 并提示重试；未知错误附带请求号 |
| 工单接口 | `assetNos` / `assetNoList` 作为 `relatedAssets` 的别名被接受（v3.2 中被静默丢弃） |

---

## 四、新增与改进

- **会话吊销**：users.token_valid_after 水位线 + jti 黑名单；refresh token 原子一次性使用、SHA-256 摘要存储，多标签页并发续期有 30 秒宽限
- **集群定时任务锁**：12 个定时任务（SLA 升级、告警自动派单、巡检生成、Webhook 投递、折旧计提等）加 job_lock 互斥，后端可多实例部署
- **加密密钥轮换**：`DCIM_CRYPTO_KEY_OLD` + 启动时自动重加密
- **登录防爆破计数进 Redis**：多实例共享；未知用户名也执行同成本 bcrypt，消除用户名枚举时序差
- **10 万级性能**：资产列表排序索引、asset_no / IP 模糊搜索 trgm 索引；服务发现结果预序列化 + gzip + 防击穿；总览与运营大盘短时缓存
- **运营总览补齐**：资产财务价值、容量规划（U 位 / 功率）、运维成本结构、TCO、供应商绩效、到期预警、U 位容量预测（v3.2 前端调用的接口在后端不存在，面板恒为空）。电价可在「容量 → 参数」配置，用于电费估算
- **事件 → 问题**：工单详情显示关联问题单，可一键「升级为问题单」并自动双向关联
- **暗色模式**：桌面端刷新后保持主题；Element Plus 组件与图表跟随明暗切换
- **库存水位页**：修复无筛选条件时 SQL 拼接错误导致的 500（PostgreSQL 15+）
- **备份恢复**：`try_inet` 函数 schema 限定，新备份可干净恢复；`./dcimctl restore` 兼容旧备份
- **部署**：缺少 collector-agent 源码时自动跳过采集器（v3.2 新装默认启用会构建失败），升级时沿用旧目录中的采集器源码；容器加 no-new-privileges；`dcimctl` 写 `.env` 时正确转义；删除未使用且 CORS 全开的 Kong 配置

---

## 五、源码与补丁

v3.2 安装包只附带编译后的后端 jar。本版本的后端改动是在反编译还原的源码上完成的：

- `source/backend/`：反编译还原并修复后的完整后端源码（可编译，见其中 README）
- `patches/`：相对 v3.2 的源码改动补丁（后端 / 前端 / 部署），**建议合入你们自己的源码仓库**
- 交付的 jar 采用“混合打包”：未改动的类保持 v3.2 原始字节码，只有受改动影响的类重新编译，避免反编译误差扩散

---

## 六、回归测试

`tests/` 下的脚本可在任意测试环境复跑（需 Python 3 + requests）：

```bash
BASE=http://<后端>:8081/api ADMIN_PW=<admin口令> python3 tests/sec_tests.py    # 10 项安全/并发回归
BASE=... python3 tests/crawl.py tests/endpoints.tsv out.json                     # 全部 GET 接口冒烟
psql ... -f tests/seed_scale.sql && python3 tests/perf_tests.py                   # 10 万资产规模压测
```
