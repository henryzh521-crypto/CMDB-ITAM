#!/bin/sh
# 后端启动：瘦包（含 lib/）走 classpath 启动，胖包走 -jar
# v3.3.1：存在多个 dcim-platform-*.jar 时取版本号最高的一个并告警。
#   v3.3.0 及以前按文件名取第一个——新安装包解压覆盖到旧目录后，旧的 dcim-platform-3.2.0.jar 排在前面，
#   实际运行的仍是旧代码（工单 assetNos 不落库等问题“修了却没生效”）。
set -e
cd /app
JARS="$(ls /app/dcim-platform-*.jar 2>/dev/null | sort -V)"
[ -n "$JARS" ] || { echo "未找到 /app/dcim-platform-*.jar" >&2; exit 1; }
JAR="$(echo "$JARS" | tail -n1)"
if [ "$(echo "$JARS" | wc -l)" -gt 1 ]; then
  echo "! 发现多个后端 jar，使用版本最高的 $(basename "$JAR")；请从 artifacts/backend/ 移除其余旧包：" >&2
  echo "$JARS" | sed '$d' | sed 's|^|    |' >&2
fi
echo "启动后端：$(basename "$JAR")"
if [ -d /app/lib ]; then
  exec java $JAVA_OPTS -cp "$JAR:/app/lib/*" com.dcim.PlatformApplication "$@"
else
  exec java $JAVA_OPTS -jar "$JAR" "$@"
fi
