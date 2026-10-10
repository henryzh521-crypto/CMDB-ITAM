# v3.5.0 → v3.5.1 补丁

| 文件 | 内容 |
|---|---|
| backend.diff | 后端源码与资源（质量强化 45 个文件：资产-财务删除边界、数据范围收口、SSE D1-D3、监控告警、仓库 A1-A6、工单与流程等；无数据库迁移） |
| frontend.diff | 前端源码（24 个文件：tooltip XSS 转义、上传预校验、请求竞态守卫、路由死循环、CSP、source-map-js 固定 1.2.2） |
| deploy.diff | 部署脚本、编排、反向代理配置、文档与包根文件（版本号、升级说明与回归报告） |
| tests.diff | 回归测试套件（新增 CT-16 资产删除财务守卫用例，共 93 个） |

路径对应：`backend-src/` → `source/backend/src/`，`backend-resources/` → `source/backend/resources/`，`pkg/` → 安装包根目录。
本包不含密钥与运行数据，四个 diff 可直接合入源码仓库。
