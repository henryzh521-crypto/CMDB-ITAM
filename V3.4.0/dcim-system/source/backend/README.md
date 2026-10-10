# 后端源码说明（v3.4.0）

v3.2 安装包只包含编译后的 `dcim-platform-3.2.0.jar`，没有 Java 源码。本目录是：

1. 用 Vineflower 1.10.1 将 v3.2.0 jar 反编译得到的源码（编译期带有 `-parameters -g`，变量名/参数名完整保留）；
2. 修正反编译产物中无法直接编译的写法（MyBatis-Plus Lambda 泛型擦除、switch 局部变量重名等，均不改变语义）；
3. 在此基础上完成 v3.3.0、v3.3.1 与 v3.4.0 的全部修复与升级（v3.4 相对 v3.3.0 的改动见 `patches/v3.3.0-to-v3.4.0/backend.diff`）。

## 编译

```bash
./build.sh            # 需要 JDK 17+；依赖使用 ../../artifacts/backend/lib/*.jar，无需联网
# 产物：build/dcim-platform-3.4.0.jar（全量重编译版）
```

## v3.4 新增的主要代码位置

| 能力 | 位置 |
|---|---|
| 字段级权限（目录、合并、路由规则、读写拦截） | `src/com/dcim/security/field/` |
| 按用户限流 | `src/com/dcim/security/UserRateLimitFilter.java` |
| 账号锁定 / 可信 IP | `src/com/dcim/security/LoginThrottle.java`、`AuthController` |
| 删除权限、授权上限 | `RbacService`、`PrivilegeGuard` |
| OpenAPI 生成 | `src/com/dcim/system/openapi/OpenApiController.java` |
| Webhook 事务性发件箱 | `src/com/dcim/webhook/service/WebhookDispatcher.java` |
| 多实例定时任务锁 | `src/com/dcim/common/cluster/` |
| 数据库迁移 | `resources/db/migration/V45__v34_permissions_perf.sql` |

## 与交付 jar 的关系（重要）

交付的 `artifacts/backend/dcim-platform-3.4.0.jar` 采用**混合打包**：
未受改动影响的 378 个 class 文件保持 v3.2.0 原始字节码；受 v3.3 / v3.4 改动影响或新增的 55 个类（102 个 class 文件）重新编译。

原因：反编译器无法 100% 还原控制流。v3.3 时对全部 8087 个方法做过「原始字节码 ↔ 重编译字节码」的语义比对
（调用序列、常量、循环回边、异常处理表），并用 CFR 交叉复核可疑方法，发现并修正了 1 处反编译错误
（`WebhookDeliveryWorker.tick()` 的 do-while 被还原成死循环）。混合打包把这类风险限制在确实改动过的类上。

全量重编译版本同样通过了全部 80 项回归，但我们仍建议：**把 `patches/` 中的改动合入你们自己的原始源码仓库**，以原始源码为准继续维护。
