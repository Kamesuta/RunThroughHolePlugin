#!/bin/bash

# データパックを run/world/datapacks に同期するスクリプト

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SOURCE_DIR="$REPO_ROOT/datapacks"
TARGET_DIR="$REPO_ROOT/run/world/datapacks"

echo "データパックを同期しています..."
echo "  元: $SOURCE_DIR"
echo "  先: $TARGET_DIR"

# ターゲットディレクトリが存在しない場合は作成
mkdir -p "$TARGET_DIR"

# 既存のデータパックを削除
rm -rf "$TARGET_DIR"/*

# rsyncでコピー（ハードリンクを使用）
rsync -a --link-dest="$SOURCE_DIR" "$SOURCE_DIR/" "$TARGET_DIR/"

echo "✓ データパックの同期が完了しました"
