# v3.4.0 → v3.5.0 补丁

| 文件 | 内容 |
|---|---|
| backend.diff | 后端源码与资源（含数据库迁移 V46，仅注册联动工单类型、无 DDL） |
| frontend.diff | 前端源码（通知中心 / 工单看板 / 网络拓扑等） |
| deploy.diff | 部署脚本、编排、反向代理配置、文档与包根文件（含 source/backend/build.sh 版本回退值） |
| tests.diff | 回归测试套件（新增 12 个用例，共 92 个） |

路径对应：`backend-src/` → `source/backend/src/`，`backend-resources/` → `source/backend/resources/`，`pkg/` → 安装包根目录。
本包不含密钥与运行数据，四个 diff 可直接合入源码仓库。
