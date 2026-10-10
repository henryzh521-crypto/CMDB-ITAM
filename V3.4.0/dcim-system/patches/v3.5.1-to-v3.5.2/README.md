# v3.5.1 → v3.5.2 补丁

| 文件 | 内容 |
|---|---|
| backend.diff | 后端源码与资源（P3 稳定性加固 11 个文件：调度锁租约上限、带外端点 bmc_ip 并发 409 兜底、XFF 可信代理收敛与标准解析、PAT 吊销 Redis pub/sub 广播、快照保留任务；含 V47 索引迁移） |
| frontend.diff | 前端源码（2 个文件：版本号 3.5.2） |
| deploy.diff | 部署脚本、编排、文档与包根文件（compose 镜像变量化、install.sh 镜像摘要锁定、.env.example 新增 DCIM_TRUSTED_PROXIES 等说明、版本号、升级说明与回归报告） |
| tests.diff | 回归测试套件（新增 CT-17 带外端点并发唯一约束、CL-09 PAT 吊销跨实例生效，共 95 个用例；V47 校验和登记） |

路径对应：`backend-src/` → `source/backend/src/`，`backend-resources/` → `source/backend/resources/`，`pkg/` → 安装包根目录。
本包不含密钥与运行数据，四个 diff 可直接合入源码仓库。
