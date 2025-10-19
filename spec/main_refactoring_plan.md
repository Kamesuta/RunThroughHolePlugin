# Mainクラス リファクタリング計画書

## 概要
現在のMainクラス（295行）が多機能すぎるため、責任を分離して保守性を向上させる。

## 現状の問題
- MainクラスにPlayerData管理、ゲームロジック、プラグイン管理が混在
- 単一責任の原則に違反
- テストが困難
- コードの可読性が低い

## 分離計画

### 1. PlayerDataManager クラス
**責任**: プレイヤーデータの管理

**移動する機能**:
- `Map<UUID, PlayerData> playerDataMap` フィールド
- `getPlayerData(UUID playerId)` メソッド
- `getOrCreatePlayerData(UUID playerId)` メソッド
- `getPlayerDataMap()` メソッド

**新しいメソッド**:
- `addPlayerData(UUID playerId, PlayerData data)` - プレイヤーデータの追加
- `removePlayerData(UUID playerId)` - プレイヤーデータの削除
- `hasPlayerData(UUID playerId)` - プレイヤーデータの存在確認
- `getAllPlayerData()` - 全プレイヤーデータの取得
- `clearAllPlayerData()` - 全プレイヤーデータのクリア

**ファイル**: `src/main/java/mods/kpw/runthroughhole/PlayerDataManager.java`

### 2. GameManager クラス
**責任**: ゲームロジックの管理

**移動する機能**:
- `startGame(Player player)` メソッド
- `stopGame(Player player)` メソッド
- `gameOver(Player player, String reason)` メソッド
- `gameOver(Player player, String reason, List<CubeBlock> collidedBlocks)` メソッド
- ゲームループ（自動前進タスク）

**新しいメソッド**:
- `isGameActive(UUID playerId)` - ゲームがアクティブかチェック
- `getActivePlayers()` - アクティブなプレイヤー一覧取得
- `pauseGame(UUID playerId)` - ゲームの一時停止
- `resumeGame(UUID playerId)` - ゲームの再開

**ファイル**: `src/main/java/mods/kpw/runthroughhole/GameManager.java`

### 3. リファクタリング後のMainクラス
**責任**: プラグインの初期化・終了処理のみ

**残る機能**:
- `onEnable()` - プラグイン有効化処理
- `onDisable()` - プラグイン無効化処理
- コマンド・イベントリスナーの登録
- マネージャークラスの初期化

**新しいフィールド**:
- `PlayerDataManager playerDataManager`
- `GameManager gameManager`

## 実装手順

### Phase 1: PlayerDataManager の実装
1. `PlayerDataManager` クラスを作成
2. PlayerData管理機能を移動
3. MainクラスでPlayerDataManagerを使用するよう修正
4. テスト実行

### Phase 2: GameManager の実装
1. `GameManager` クラスを作成
2. ゲームロジック機能を移動
3. MainクラスでGameManagerを使用するよう修正
4. テスト実行

### Phase 3: Mainクラスのクリーンアップ
1. 不要になったメソッドを削除
2. コードの整理・最適化
3. コメントの追加
4. 最終テスト

## 期待される効果

### 保守性の向上
- 各クラスの責任が明確
- バグの原因特定が容易
- 機能追加・修正が簡単

### テスタビリティの向上
- 各マネージャークラスを独立してテスト可能
- モックオブジェクトの使用が容易

### コードの可読性向上
- Mainクラスがシンプルになる（予想: 295行 → 100行以下）
- 機能ごとにファイルが分離される

## 注意事項
- 既存のAPIは維持（後方互換性）
- 段階的な実装でリスクを最小化
- 各段階でビルド・テストを実行

## ファイル構成（実装後）
```
src/main/java/mods/kpw/runthroughhole/
├── Main.java                    # プラグイン管理（約100行）
├── PlayerDataManager.java       # プレイヤーデータ管理（約80行）
├── GameManager.java             # ゲームロジック管理（約150行）
├── PlayerData.java              # 既存（変更なし）
├── PlayerCube.java              # 既存（変更なし）
├── CubeCamera.java              # 既存（変更なし）
├── HolePreview.java             # 既存（変更なし）
├── PlayerGameListener.java      # 既存（変更なし）
└── RunHoleCommand.java          # 既存（変更なし）
```

## 実装完了後の確認項目
- [ ] ビルドが成功する
- [ ] ゲーム開始・終了が正常に動作する
- [ ] プレイヤーデータの管理が正常に動作する
- [ ] ゲームオーバー処理が正常に動作する
- [ ] 既存のコマンドが正常に動作する
