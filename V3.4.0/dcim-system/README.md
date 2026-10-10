# 智算资产管理平台 v3.5.2 安装包

```bash
./install.sh                                   # 全新安装（Docker Compose）
./dcimctl upgrade /path/to/本安装包目录          # 在旧版本目录执行，从 v3.2 / v3.3 / v3.4 / v3.5 升级
./install.sh --native                          # 原生部署（systemd + 已有 PostgreSQL/Redis）
```

- **从 v3.5.0 / v3.5.1 升级的**：v3.5.2 为 P3 稳定性加固版，含 1 个自动执行的数据库迁移（V47 快照索引）与镜像摘要锁定等配置变化，见 `docs/UPGRADE-3.5.2.md`。
- 从 v3.4.0 及更早版本升级的，请先阅读 `docs/UPGRADE-3.5.md` 第一节（v3.5 新增联动与 SSE 的配置开关、反向代理需为 `/api/notifications/sse` 关闭缓冲）；v3.4 的注意事项见 `docs/UPGRADE-3.4.md`（非管理员角色的「删除」勾选会被复位）；从 v3.2.0 升级的，还需阅读 `docs/UPGRADE-3.3.md` 第一节（v3.2.0 安装包密钥外泄与轮换）。
- 安装包不含任何密钥、证书和运行数据；`deploy/.env` 与自签证书在安装时随机生成。
- 运维命令：`./dcimctl help`；升级后自检：`ADMIN_PW=... EXPECT_VERSION=3.5.2 python3 tests/run_all.py -s smoke`

| 目录 | 内容 |
|---|---|
| `artifacts/` | 后端 jar（含依赖）与前端构建产物，目标机无需 Maven / Node |
| `deploy/` | Docker Compose、Caddy、监控栈、原生部署模板 |
| `scripts/` | 备份 / 恢复 / 恢复演练 / 密钥工具 |
| `platform-web/` | 前端源码 |
| `source/backend/` | 后端源码（见其中 README） |
| `patches/` | 相邻版本补丁：`v3.5.1-to-v3.5.2/`（本版）、`v3.5.0-to-v3.5.1/`；更早版本见 `patches/v3.4.0-to-v3.5.0/` |
| `tests/` | 回归测试套件：9 个套件 95 个用例，见 `tests/README.md` |
| `docs/` | 用户手册、升级说明（UPGRADE-3.5.2.md）、本版回归测试报告（TEST-REPORT-3.5.2.md） |
| `SHA256SUMS` | 全部文件的校验和：`sha256sum -c SHA256SUMS` |
