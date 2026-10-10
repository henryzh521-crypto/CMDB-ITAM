#!/bin/bash
# 不依赖 curl/wget：用 bash /dev/tcp 请求 /api/healthz，HTTP 200 即健康（DB 不可用时后端回 503）
exec 3<>/dev/tcp/127.0.0.1/8081 || exit 1
printf 'GET /api/healthz HTTP/1.0\r\nHost: localhost\r\n\r\n' >&3
read -r -t 5 status <&3 || exit 1
case "$status" in *" 200 "*) exit 0 ;; *) exit 1 ;; esac
