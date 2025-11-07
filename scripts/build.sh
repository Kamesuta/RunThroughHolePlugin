#!/usr/bin/env bash
# Mavenパッケージングとプラグインコピーの実行
set -e

# Mavenでプロジェクトをパッケージ化し、成功すればプラグインをコピー
mvn clean package && copy_plugin.sh

# データパックを同期
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
"$SCRIPT_DIR/sync_datapacks.sh"
