#!/bin/bash
# 全量编译后端（JDK 17+，离线；依赖取自安装包 artifacts/backend/lib）
set -euo pipefail
cd "$(dirname "$0")"
LIB="${LIB:-../../artifacts/backend/lib}"
VER="$(cat ../../VERSION 2>/dev/null || echo 3.5.2)"
rm -rf build/classes && mkdir -p build/classes
find src -name "*.java" > build/sources.txt
javac -nowarn -parameters -g -encoding UTF-8 --release 17 -cp "$LIB/*" -d build/classes @build/sources.txt
cp -r resources/. build/classes/
(cd build/classes && jar --create --file "../dcim-platform-$VER.jar" --manifest META-INF/MANIFEST.MF -C . .)
echo "✓ build/dcim-platform-$VER.jar"
