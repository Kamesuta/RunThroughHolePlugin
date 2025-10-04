#!/bin/bash
# Minecraftサーバーの実行環境をセットアップする
# テンプレートファイルをコピーし、PaperMCサーバーが存在しない場合にダウンロードする
set -e

# `run` ディレクトリが存在しない場合、テンプレートからセットアップ
if [ ! -d "./run" ]; then
    echo "★ テンプレートからマイクラサーバー実行環境を作成しています..."
    cp -rn ./run-template ./run
fi

# `run/paper.jar` が存在しない場合、PaperMCサーバーのJARファイルをダウンロード
if [ ! -f "./run/paper.jar" ]; then
    echo "★ PaperMCサーバーをダウンロードしています..."
    # 指定されたURLからPaperMCのJARファイルをダウンロードし、`run/paper.jar` として保存
    wget https://api.papermc.io/v2/projects/paper/versions/1.21.8/builds/56/downloads/paper-1.21.8-56.jar -O ./run/paper.jar
else
    echo "★ PaperMCサーバーは既に存在します。"
fi

echo "★ サーバーのセットアップが完了しました。"
