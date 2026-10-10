# ============================================================
# 平台后端运行时镜像（使用安装包内预编译产物 artifacts/backend，无需 Maven / 联网）
# 构建上下文：安装包根目录（compose 中 context: ..）
#
# 产物两种形态均支持：
#   * 瘦包：artifacts/backend/dcim-platform-<ver>.jar + artifacts/backend/lib/*.jar（安装包默认）
#   * 胖包：artifacts/backend/dcim-platform-<ver>.jar（mvn package 产出的 Spring Boot 可执行 jar）
# ============================================================
ARG BASE_IMAGE=docker.m.daocloud.io/library/eclipse-temurin:17-jre
FROM ${BASE_IMAGE}

WORKDIR /app
COPY artifacts/backend/ /app/
COPY deploy/docker/backend-entrypoint.sh /app/entrypoint.sh
COPY deploy/docker/backend-healthcheck.sh /app/healthcheck.sh

# 非 root 运行；上传目录可写
RUN chmod 0755 /app/entrypoint.sh /app/healthcheck.sh \
    && mkdir -p /app/data/uploads /tmp && chmod 1777 /tmp \
    && (getent group dcim >/dev/null || groupadd -r -g 10001 dcim) \
    && (getent passwd dcim >/dev/null || useradd -r -u 10001 -g dcim -d /app -s /usr/sbin/nologin dcim) \
    && chown -R dcim:dcim /app/data
USER dcim

ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-Xms512m -Xmx1024m -XX:+UseG1GC -XX:+ExitOnOutOfMemoryError"

EXPOSE 8081
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=5 CMD /app/healthcheck.sh
ENTRYPOINT ["/app/entrypoint.sh"]
