#!/usr/bin/env bash
# ビルド済みプラグインJARファイルのコピー
set -e

# targetディレクトリから最新のJARファイルを検索（original-*.jarは除外）
SOURCE_JAR=$(find target -maxdepth 1 -name "*.jar" ! -name "original-*.jar" -printf '%T@ %p\n' | sort -n -r | head -n 1 | cut -d' ' -f2-)

# コピー先パス
DEST_JAR="run/plugins/Plugin.jar"

# ソースJARファイルの存在確認とコピー実行
if [ -f "$SOURCE_JAR" ]; then
    cp "$SOURCE_JAR" "$DEST_JAR"
    echo "★ プラグインをコピーしました: $SOURCE_JAR -> $DEST_JAR"
else
    # JARファイルが見つからない場合のエラーと終了
    echo "★ エラー: JARファイルがtargetディレクトリに未検出。ビルド実行を推奨。"
    exit 1
fi
