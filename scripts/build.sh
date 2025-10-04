#!/usr/bin/env bash
# Mavenパッケージングとプラグインコピーの実行
set -e

# Mavenでプロジェクトをパッケージ化し、成功すればプラグインをコピー
mvn package && copy_plugin.sh
