# minecraft-plugin-maker

<p align="center"><a href="https://zenn.dev/kamesuta/books/minecraft-plugin-tutorial">
<img src="https://github.com/Kamesuta/zenn/blob/main/books/minecraft-plugin-tutorial/cover.png?raw=true" alt="5分で始められる初心者向けMinecraftプラグイン開発" width="256" height="auto" />
</a></p>

<p align="center">
本リポジトリは Zenn 本『<a href="https://zenn.dev/kamesuta/books/minecraft-plugin-tutorial">5分で始められる初心者向けMinecraftプラグイン開発</a>』で使用しているテンプレートです。
記事に沿って進めると、GitHub Codespaces と Gemini CLI を使った Minecraft プラグイン開発を気軽にで体験できます。
</p>

## 概要

GitHub Codespaces と Gemini CLI を使って、Minecraft Paper プラグインを「考える・作る・動かす・テスト・配る」まで一気に体験できるテンプレートです。  
Java 21 / Paper 1.21.8 の開発環境やポート開放スクリプト、AI との共創ワークフローが最初から整っているので、ブラウザを開いて数分待つだけでサーバーと AI との開発を始められます。

## このテンプレートの特徴
- GitHub Codespaces 専用に最適化された開発環境（JDK・Maven・Paper 1.21.8・VS Code 拡張）
   - `F5` ひとつで Paper サーバーを起動できるデバッグ設定済み
   - PlugManX と AutoReload を同梱し、ホットリロードと自動反映をサポート
   - Secure Share Net でポートを外部公開し、友だちと一緒にテストプレイ可能
- Gemini CLI と連携し、チャット形式で仕様策定から実装・修正までを AI に依頼可能
   - `src/` と `spec/` を自動生成・更新しながら開発を進めるワークフローをサポート
   - AI がデバッグしやすいよう、RCON 経由でコマンド送信が可能、MCP 経由でブレークポイント設定も可能
- GitHub Releases で jar を配布するまでのハンズオン手順をガイド

## 必要なもの
- GitHub アカウント（Codespaces 利用のため）
- 18 歳以上に設定された Google アカウント（Gemini CLI 認証用）
- Minecraft: Java Edition 1.21.8 クライアント
- ブラウザと安定したネットワーク環境

## クイックスタート
[Zenn 本](https://zenn.dev/kamesuta/books/minecraft-plugin-tutorial) に沿って進める場合も同じ手順です。

1. **テンプレートを開く / 作成する**  
   リポジトリ右上の `Use this template` から自分のアカウントへコピーするか、体験用として `Code > Codespaces > +` で Codespace を新規作成します。
2. **自動セットアップを待つ**  
   Dev Container 作成後、`scripts/setup_devcontainer.sh` が走り、スクリプトへの実行権付与・必要パッケージの apt インストール・Gemini CLI の npm インストール・Paper サーバーのダウンロードまで自動で完了します。  
   ターミナルに `★ セットアップスクリプト完了。` と出れば準備OKです。
3. **プラグイン名を決める（任意）**  
   AI に任せる場合は後述の `/setup` で自動リネームされます。  
   自分で決めたい場合は `rename_plugin.sh SuperJumpBoots` のように実行してください。  
   `pom.xml` や `src/main/java/mods/kpw/...` が一括で更新されます。
4. **サーバーとポートを起動**  
   `F5` でデバッグを開始すると、`Build Plugin` → `Start Server` → `Open Port` の順でタスクが並列に動きます。  
   `build.sh` が最新 jar を `run/plugins/Plugin.jar` にコピーし、Paper が JVM デバッガ (5005) 付きで立ち上がり、Secure Share Net CLI が 25565 番を外部公開します。  
   Webパネルから入手できるIPをマイクラに貼り付けて入室できます。
5. **Gemini CLI にログイン**  
   ターミナルの `+` メニューから「Gemini CLI」プロファイルを開くと `gemini -i --yolo` が起動します。  
   表示される URL を Ctrl+クリックして Google アカウントで認証し、コードをターミナルに貼り付けてログインしてください。
6. **AI に `/setup` を依頼**  
   `GEMINI.md` に沿って、ヒアリング → 仕様書生成 (`spec/仕様書.md`) → MVP 実装 → TODO ループまで自動で進みます。仕様変更や追加依頼は `/spec` から再調整できます。
7. **テストと改善**  
   実装後はゲーム内で動作確認し、修正点を Gemini に伝えてください。  
   `rcon.sh "plugman reload <プラグイン名>"` でホットリロードできます。  
   成果物を残したい場合は VS Code 左の「ソース管理」からコミット＆プッシュ、`target/*.jar` をダウンロードして GitHub Releases へアップロードしましょう。

## 付属スクリプト（`scripts/`）
- `build.sh` : `mvn package` を実行し、最新 jar を `run/plugins/Plugin.jar` へコピー
- `copy_plugin.sh` : `target` 直下の最新 jar を `Plugin.jar` として配置
- `rename_plugin.sh <Name>` : クラス名や artifactId を英数字 PascalCase 名にリネーム。実行中サーバーがあれば PlugManX でリロードまで自動化
- `rcon.sh "command"` : RCON 25575 (`password: gemini`) へ安全にコマンド送信。`reload`, `stop` は禁止にしているので PlugManX 経由で操作してください
- `setup_server.sh` : `run-template` からサーバー環境を再構築し、Paper jar を再取得
- `securesharenet` : Secure Share Net の CLI クライアント。`Open Port` タスクが内部で利用します
- `mcp-debug.sh` : VS Code 拡張「Claude Debugs For You」が生成する `mcp-debug.js` をラップし、Codespaces / Dev Container のどちらでも動作するようにする
- `NOTICE.md` : 同梱バイナリ (mcrcon, securesharenet) の配布元とライセンスを明記

`scripts` ディレクトリは PATH に追加済みなので、プロジェクトルート以外からでもコマンド名だけで呼び出せます。

## ディレクトリ構成の目安
- `src/` — プラグインの Java ソースコード。Gemini CLI が自動生成し、手動編集も可能。
- `spec/` — AI が提案する仕様書。プレビューで読みやすく確認できます。
- `run/` : Paper サーバーが起動するディレクトリ
- `run-template/` : サーバー初期化テンプレート。初回セットアップ時に `run` フォルダにコピーされます。
- `target/` : Maven ビルド成果物。`build.sh` で自動的に `run/plugins/Plugin.jar` へコピー
- `.gemini/commands/setup.toml` : `/setup` コマンドの進行台本
- `GEMINI.md` : Gemini CLI への役割説明と運用ルール

## サードパーティについて
- `scripts/NOTICE.md` に、同梱している mcrcon / Secure Share Net CLI の配布元とライセンスをまとめています。必要に応じてご確認ください。
- `run-template/plugins/NOTICE.md` に、同梱している PlugManX / AutoReload の配布元とライセンスをまとめています。必要に応じてご確認ください。
- Gemini CLI は Google の商用サービスであり、利用には Google の利用規約とプライバシーポリシーへの同意が必要です。
- 手順簡略化のため Minecraft の `eula.txt` の `eula=true` をあらかじめ設定しています。実際にサーバーを公開する場合は、必ず [EULA](https://account.mojang.com/documents/minecraft_eula) を確認し、同意した上で運用してください。

## ライセンス
MIT License (c) 2025 Kamesuta

---

質問や改善提案があれば Issue や Pull Request を歓迎します。AI と一緒に Minecraft プラグイン開発を楽しみましょう！
