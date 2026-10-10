# 智算资产管理平台 v3.5.0 升级说明

v3.5.0 以**联动闭环、SSE 实时推送、可视化增强与事件结构强化**为主。可从 v3.2.x / v3.3.x / v3.4.x 直接升级。
全部改动经 `tests/` 下 92 个自动化用例（9 个套件）在 10 万资产环境上回归通过。

> 从 v3.2.0 升级的，请先阅读 `docs/UPGRADE-3.3.md` 第一节（v3.2.0 安装包密钥外泄与轮换）。

---

## 一、⚠ 升级前必读：反向代理与 SSE

本版新增 SSE（Server-Sent Events）实时推送，浏览器与后端之间保持长连接。
**默认的 Docker Compose 部署（Caddy 入口）已自动配置，无需任何操作。** 但以下情况需要注意：

1. **自建 / 自维护反向代理**（nginx、已有 F5 等）必须为 `GET /api/notifications/sse` 关闭响应缓冲，否则事件帧会被代理攒着不转发，前端表现为"通知不实时"：
   - nginx 示例（`deploy/native/nginx-dcim.conf` 已内置，供参照）：
     ```nginx
     location = /api/notifications/sse {
         proxy_pass http://127.0.0.1:8081;
         proxy_http_version 1.1;
         proxy_set_header Connection '';
         proxy_buffering off;
         proxy_read_timeout 24h;   # 后端每 25s 发心跳帧，连接不会空闲
     }
     ```
   - Caddy：`flush_interval -1`（本包 `deploy/Caddyfile` 已含）。
2. **SSE 认证方式**：浏览器前端走**同源 cookie 自动认证**（`access_token` cookie，EventSource 自动携带），令牌**不进 URL**、不进代理日志。后端仅为 SSE 端点额外兼容 `?token=<JWT>` 查询参数（供非浏览器客户端 / 脚本接入使用），此时：
   - Caddy 访问日志已配置删除 query 中的 `token` 参数，JWT 不落盘；
   - 自建代理若放行 query token 接入，请同样确保访问日志脱敏；
   - 该兼容通道意味着 JWT 可能出现在浏览器历史 / 代理日志里，属内网部署可接受的权衡；若合规要求更高，把 `SSE_ENABLED=false` 关闭 SSE（前端自动降级 30s 轮询，行为回到 v3.4）。
3. **联动闭环默认值**：升级后立即生效的三条联动（盘点差异工单、低库存通知、SLA 升级投递），默认配置见第四节；不想启用某条联动，改环境变量重启即可，无需改库。

---

## 二、升级步骤

### Docker 部署

```bash
# 在旧安装目录
./dcimctl backup                                 # 先手工备份一次
./dcimctl upgrade /path/to/dcim-system-3.5.0     # 复制 .env/证书 → 自动备份 → 构建 → 启动
```

- 首次启动执行数据库迁移 V46（秒级，仅向 `workflow_config` 注册「盘点差异」「补货」两个免审工单类型，幂等 ON CONFLICT DO NOTHING，无表结构变更）。
- `.env` 会被沿用；新增配置项都有默认值，不改也能运行（见第四节）。
- 安装程序会把 `artifacts/backend/` 中残留的旧版 jar 移到 `artifacts/stale-jars-<时间>/`，避免「覆盖解压后仍跑旧包」。
- **升级 Caddy 配置**：新 `deploy/Caddyfile` 增加 SSE 分流与日志脱敏。`dcimctl upgrade` 会整体替换 `deploy/` 目录；若你手工维护过 Caddyfile，请把本包中的 `handle /api/notifications/sse` 块与日志 `format filter` 段合并进去，然后 `docker exec dcim-caddy caddy reload --config /etc/caddy/Caddyfile`。

### 原生部署（systemd）

替换 `artifacts/` 后执行 `./install.sh --native`。启动脚本会在多个 jar 并存时选择**版本号最高**的一个。
`deploy/native/nginx-dcim.conf` 新增 SSE location，安装时会重新铺开；自行维护 nginx 的请按第一节手工合并。

### 升级后自检

```bash
ADMIN_PW='<admin口令>' EXPECT_VERSION=3.5.0 python3 tests/run_all.py -s smoke
```

界面右上角「关于本系统」会显示前后端版本（3.5.0），不一致时会标红。
登录后打开「通知中心」页面，或直接验证 SSE 通路：

```bash
curl -N -H 'Accept: text/event-stream' "http://<host>/api/notifications/sse?token=<登录JWT>"
# 建连成功会立即收到：event:connected 与 data:{"ok":true,...}；每 25 秒一条 :ping 心跳
```

### 回滚

V46 只向 `workflow_config` INSERT 两条工单类型定义（幂等，无 DDL），v3.4 后端可在 V46 之后的库上继续运行
（Flyway 默认忽略「未来版本」的迁移）。回滚时换回旧目录即可；SSE 与联动即使残留配置也随旧后端不生效。
稳妥起见回滚后可把 `SSE_ENABLED=false`、`LOW_STOCK_MODE=off`、`INVENTORY_DIFF_TICKET_ENABLED=false` 写入 `.env`，彻底回到 v3.4 行为。

---

## 三、行为变化（请通知用户）

| 变化 | 说明 |
|---|---|
| 站内信实时化 | 所有站内信（工单流转、告警、低库存、SLA 升级等）经 SSE 即时推送，浏览器不再等轮询；SSE 不可用时前端自动降级 30s 轮询 |
| 铃铛红点 | 顶栏铃铛显示未读数，点开通知中心；点单条通知跳转对应工单；「全部已读」一键清理 |
| 盘点差异工单（默认开） | 盘点任务完成时若存在盘亏 / 盘盈 / 错位差异，自动生成「盘点差异」工单（与完成同事务，建单失败整体回滚），差异明细在工单描述 |
| 低库存联动（默认 notify） | 出库 / 调整 / 调拨使库存低于安全线时：notify 模式向仓库管理员（R-WAREHOUSE 角色用户）发站内信；ticket 模式额外自动创建补货工单（建议补货量 = 安全库存 − 当前库存）。三层幂等保证并发出库只建一张单 |
| SLA 超时升级 | 工单超过 SLA 处理时限自动升级：优先级提高、发站内信、按订阅投递 `workorder.sla_escalated` webhook（升级判定每 60 秒一轮，已有 `job_lock` 互斥） |
| 工单通知补全 | 「开始处理」通知申请人、「关闭」通知申请人与处理人（此前这两步不发通知） |
| 工单看板 | 工单列表页支持「表格 / 看板」切换：按状态分列、拖拽流转，非法流转（409）卡片自动回弹；「处理中 → 已关闭」需根因分类，看板会引导去详情页 |
| 网络拓扑（新页面） | 「配置项 → 网络拓扑」全量关系图（力导向布局，支持机房过滤、关系类型图例、节点跳转） |
| 仪表盘布局 | 运营总览卡片支持显隐与排序配置（localStorage 持久化），刷新后保持 |
| 告警中心节流 | 页面停留期间自动刷新降频，降低无效查询 |

---

## 四、新增配置项（均有默认值，不改也能运行）

| 配置项 | 默认 | 说明 |
|---|---|---|
| `SSE_ENABLED` | `true` | SSE 实时推送总开关；`false` = 端点 503，前端降级 30s 轮询（回到 v3.4 行为） |
| `SSE_MAX_CONNECTIONS` | `200` | 每实例最大并发 SSE 连接，超限返回 503 触发前端降级 |
| `SSE_HEARTBEAT_MS` | `25000` | 心跳间隔（`:ping` 注释帧），维持中间层不掐空闲连接 |
| `INVENTORY_DIFF_TICKET_ENABLED` | `true` | 盘点完成存在差异时自动生成「盘点差异」工单 |
| `LOW_STOCK_MODE` | `notify` | 低库存联动：`off` 仅原有 webhook 事件 / `notify` 站内信通知仓库管理员 / `ticket` 通知并自动创建补货工单 |
| `LOW_STOCK_WO_TYPE` | `补货` | ticket 模式创建的工单类型（须存在于 workflow_config） |

Webhook 订阅者无需配置即可收到两个新事件：`workorder.sla_escalated`（SLA 升级）、`inventory.diff`（盘点差异）。

多实例部署：SSE 广播经 Redis pub/sub 跨实例转发（未配置 Redis 时仅本实例连接可收到推送，站内信落库不受影响）。

---

## 五、新增与改进

- **联动闭环**：盘点差异 → 工单、低库存 → 通知 / 补货单（三层幂等：实例内存防抖 + 行锁 + 开放工单查重）、SLA 升级 → webhook + 站内信
- **SSE 实时推送**：`SseConnectionRegistry` 自管连接（自建心跳线程、不用 @Scheduled，避免被集群定时任务锁拦成单实例）、200 连接 / 实例上限、Redis pub/sub 跨实例分发
- **通知中心**：未读红点、列表、全部已读、点击跳转；30s 轮询降级兜底
- **可视化**：工单看板（DnD 拖拽流转 + 409 回弹）、网络拓扑（ECharts 力导向 + 机房过滤 + 图例）、仪表盘卡片显隐排序（localStorage 持久化）、告警中心节流刷新
- **结构强化**：`UnifiedEventPublisher` 组合门面统一领域事件出口（站内信 + webhook + SSE 频道一次发布），事务提交后才投递（AFTER_COMMIT），业务回滚不产生假事件
- **安全**：Caddy 日志对 SSE 建连 query 脱敏（token 不落盘）；`/api/notifications/sse` 无令牌返回 401
- **测试**：新增 12 个用例（CT-11~15 / CC-07 / PERF-21 / UI-07~09 / CL-07~08），总计 92 个；回归报告见 `docs/TEST-REPORT-3.5.md`

**未纳入本版的决定**：报表定时导出（运营总览定时导出 PDF/Excel 到邮箱）经评估暂不实现——
当前「运营总览 + 导出」手动链路已覆盖需求，自动化的收件人管理、失败重试与敏感数据外发控制需要独立设计；
避免为凑功能而引入半成品。后续版本如需要，将以「报表订阅」形式单独设计。

---

## 六、源码、补丁与测试

- `source/backend/`：完整后端源码（v3.5.0），见其中 README
- `patches/v3.4.0-to-v3.5.0/`：相对 v3.4.0 的补丁（后端 / 前端 / 部署 / 测试）
- `tests/`：回归测试套件 92 用例，用法见 `tests/README.md`；联动用例（CT-13 / CL-08）验证补货单路径需以 `LOW_STOCK_MODE=ticket` 启动被测实例
- 数据库迁移：仅 `V46__v35_linkage_sse.sql`（向 `workflow_config` 注册联动工单类型，无 DDL）
