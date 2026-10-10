# ============================================================
# 前端运行时镜像（使用安装包内预编译产物 artifacts/web/dist，无需 Node / 联网）
# ============================================================
ARG BASE_IMAGE=docker.m.daocloud.io/library/nginx:1.27-alpine
FROM ${BASE_IMAGE}
COPY platform-web/nginx.conf /etc/nginx/conf.d/default.conf
COPY artifacts/web/dist /usr/share/nginx/html
EXPOSE 80
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD wget -q --spider http://127.0.0.1/ || exit 1
CMD ["nginx", "-g", "daemon off;"]
