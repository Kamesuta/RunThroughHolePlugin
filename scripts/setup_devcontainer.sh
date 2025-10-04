#!/usr/bin/env bash
# DevContainer環境のセットアップ
set -e

# scriptsフォルダ内のスクリプトに実行権限を付与
echo "★ scriptsフォルダ内のスクリプトに実行権限を付与中..."
chmod +x scripts/*
# gitでパーミッションを無視するように設定
git config core.fileMode false

# ヘッドレスJava GUIの依存関係インストール（Minecraftサーバー起動に必要）
echo "★ Minecraftサーバー起動に必要なパッケージをインストール中..."
sudo apt-get update
sudo apt-get install -y libxext6 libxrender1 libxtst6 libxi6

# Gemini CLIインストール
echo "★ Gemini CLIをインストール中..."
npm install -g @google/gemini-cli

# Gemini CLIがVSCodeに接続できない問題のワークアラウンド適用
# 詳細: https://github.com/google-gemini/gemini-cli/issues/6297#issuecomment-3227097271
echo "127.0.0.1 host.docker.internal" | sudo tee -a /etc/hosts

# Minecraftサーバー環境のセットアップ実行
setup_server.sh

echo "★ セットアップスクリプト完了。"
