# v3.3.0 → v3.4.0 补丁

| 文件 | 内容 |
|---|---|
| backend.diff | 后端源码与资源（含数据库迁移 V45；包含 v3.3.1 热补丁） |
| frontend.diff | 前端源码 |
| deploy.diff | 部署脚本、编排、文档 |
| tests.diff | 回归测试套件（tests/ 整体替换旧脚本） |

`scripts/lib-secrets.sh` 与 `docs/UPGRADE-3.3.md` 不在 deploy.diff 中：它们的旧版本含有 v3.2.0 外泄的口令明文，
补丁的删除行同样会带出原值。请直接用本安装包中的这两个文件整体替换。
