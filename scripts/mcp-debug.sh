#!/usr/bin/env bash
# GitHub Codespaces とローカルの DevContainer 環境の両方で VSCode拡張機能「Claude Debugs For You」を動作させるためのラッパースクリプト
set -e

# 環境に応じてVSCodeの拡張機能が格納されているベースディレクトリを特定する
# Codespacesの場合は ~/.vscode-remote, ローカルのDevContainerの場合は ~/.vscode-server を使用する
BASE_DIR=""
if [ -d "${HOME}/.vscode-server" ]; then
  BASE_DIR="${HOME}/.vscode-server"
elif [ -d "${HOME}/.vscode-remote" ]; then
  BASE_DIR="${HOME}/.vscode-remote"
else
  echo "error: neither ~/.vscode-server nor ~/.vscode-remote exists" >&2
  exit 1
fi

# 「Claude Debugs For You」拡張機能のJavaScriptファイルへのパスを構築
JS="${BASE_DIR}/data/User/globalStorage/jasonmcghee.claude-debugs-for-you/mcp-debug.js"

# スクリプトファイルが存在するか確認
if [ ! -f "${JS}" ]; then
  echo "error: not found: ${JS}" >&2
  echo "       拡張機能（Claude Debugs For You）がインストールされ、1度は有効化されているか確認してください。" >&2
  exit 2
fi

# mcp-debug.js をnodeで実行し、このスクリプトに渡された引数をすべて引き継ぐ
exec node "${JS}" "$@"