#!/bin/bash

# このスクリプトはプラグイン名を変更します。
# 使い方: rename_plugin.sh <新しいプラグイン名>

set -e # エラーが発生した時点でスクリプトを終了します

if [ "$#" -ne 1 ]; then
    echo "★ 使い方: $0 <プラグイン名>"
    echo "★ 例: $0 SuperJumpBoots"
    exit 1
fi

OLD_PLUGIN_NAME="MyExamplePlugin"
OLD_ARTIFACT_NAME="my_example_plugin"
NEW_PLUGIN_NAME=$1

# --- リネーム済みかチェック ---
if ! grep -q "$OLD_PLUGIN_NAME" pom.xml; then
    echo "★ プラグインは既にリネーム済みです。処理をスキップします。"
    exit 0
fi

# --- バリデーション処理 ---
# 1. 英数字のみかチェック
if [[ ! "$NEW_PLUGIN_NAME" =~ ^[a-zA-Z0-9]+$ ]]; then
    echo "★ エラー: プラグイン名には英数字のみ使用できます。" >&2
    exit 1
fi

# 2. 数字で始まっていないかチェック
if [[ "$NEW_PLUGIN_NAME" =~ ^[0-9] ]]; then
    echo "★ エラー: プラグイン名を数字から始めることはできません。" >&2
    exit 1
fi
# --- バリデーション終了 ---

# アーティファクト名を生成します（英字以外を削除し、小文字に変換）
NEW_ARTIFACT_NAME=$(echo "$NEW_PLUGIN_NAME" | sed 's/[^a-zA-Z]//g' | tr '[:upper:]' '[:lower:]')

echo "★ 新しいプラグイン名: $NEW_PLUGIN_NAME"
echo "★ 新しいアーティファクト名: $NEW_ARTIFACT_NAME"
echo ""

# プラグイン名を置換します
echo "★ '$OLD_PLUGIN_NAME' を '$NEW_PLUGIN_NAME' に置換しています..."
grep -rl "$OLD_PLUGIN_NAME" src pom.xml | xargs --no-run-if-empty sed -i "s/$OLD_PLUGIN_NAME/$NEW_PLUGIN_NAME/g"

# アーティファクト名を置換します
echo "★ '$OLD_ARTIFACT_NAME' を '$NEW_ARTIFACT_NAME' に置換しています..."
grep -rl "$OLD_ARTIFACT_NAME" src pom.xml | xargs --no-run-if-empty sed -i "s/$OLD_ARTIFACT_NAME/$NEW_ARTIFACT_NAME/g"

# ソースディレクトリ名を変更します
OLD_DIR="src/main/java/mods/kpw/$OLD_ARTIFACT_NAME"
NEW_DIR="src/main/java/mods/kpw/$NEW_ARTIFACT_NAME"

if [ -d "$OLD_DIR" ]; then
    echo ""
    echo "★ ディレクトリ名を変更しています: '$OLD_DIR' -> '$NEW_DIR'..."
    rm -rf "$NEW_DIR" # 既存の新ディレクトリがあれば削除
    mv "$OLD_DIR" "$NEW_DIR"
else
    echo ""
    echo "★ ディレクトリ '$OLD_DIR' が見つからないため、リネームをスキップします。"
fi

# サーバーが起動中ならビルド＆リロード
# プラグインがリネームされると、AutoReloadプラグインが効かなくなる。そのためスクリプトでリロードする。
if rcon.sh "list" > /dev/null 2>&1; then
    echo ""
    echo "★ Minecraftサーバーが起動中なので、プラグインをビルド＆リロードします..."

    # アンロード
    rcon.sh "plugman unload $OLD_PLUGIN_NAME"

    # ビルドとロード
    if build.sh; then
        echo "★ ビルド成功。プラグインをリロードします..."
        rcon.sh "plugman load $NEW_PLUGIN_NAME"
        echo "★ プラグインをリロードしました。"
    else
        echo "★ ビルドに失敗しました。リロードは行いません。"
    fi
fi

echo ""
echo "★ プラグイン名の変更が完了しました。変更内容を確認してください。"