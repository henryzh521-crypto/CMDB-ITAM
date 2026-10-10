#!/bin/sh
# 原生模式后端启动脚本（systemd ExecStart 调用；也可手工前台运行）
# v3.3.1：存在多个 dcim-platform-*.jar 时取版本号最高的一个并告警（以前按文件名取第一个，会误用旧包）
set -e
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
A="$ROOT/artifacts/backend"
JARS="$(ls "$A"/dcim-platform-*.jar 2>/dev/null | sort -V)"
[ -n "$JARS" ] || { echo "未找到 $A/dcim-platform-*.jar" >&2; exit 1; }
JAR="$(echo "$JARS" | tail -n1)"
if [ "$(echo "$JARS" | wc -l)" -gt 1 ]; then
  echo "! 发现多个后端 jar，使用版本最高的 $(basename "$JAR")；请移除其余旧包：" >&2
  echo "$JARS" | sed '$d' | sed 's|^|    |' >&2
fi
echo "启动后端：$(basename "$JAR")"
if [ -d "$A/lib" ]; then
  exec java $JAVA_OPTS -cp "$JAR:$A/lib/*" com.dcim.PlatformApplication "$@"
else
  exec java $JAVA_OPTS -jar "$JAR" "$@"
fi
