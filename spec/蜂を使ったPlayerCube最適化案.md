# エンティティを使ったPlayerCube最適化案

## 概要

現在のPlayerCubeは複数のBlockDisplayを個別にスポーンし、毎ティックZ座標をテレポートで更新しています。この実装では以下の問題があります：

- 複数のBlockDisplayを毎ティックテレポートするため処理負荷が高い
- 滑らかな移動が困難
- コードが複雑

## 新しいアプローチ

### 基本コンセプト

1. **中心エンティティ**: PlayerCubeの真ん中にエンティティを一体スポーン（現在は蜂、将来的に差し替え可能）
2. **BlockDisplayマウント**: 全てのBlockDisplayをエンティティにマウント
3. **Z方向移動の最適化**: Z軸のテレポートはエンティティのみに限定
4. **XY移動・回転は既存方式維持**: BlockDisplayのTransformationで処理（Roll回転が必要なため）

### 実装詳細

#### 1. エンティティの設定
```java
// 汎用エンティティをスポーン（現在は蜂、将来的に差し替え可能）
LivingEntity entity = (LivingEntity) world.spawn(spawnLocation, EntityType.BEE);
entity.setInvulnerable(true);
entity.setGravity(false);
entity.setSilent(true);
entity.setAI(false);
entity.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0, false, false));
this.entity = entity;
```

#### 2. BlockDisplayのマウント
```java
// 各BlockDisplayをエンティティにマウント
for (CubeBlock block : blocks) {
    entity.addPassenger(block.display);
}
```

#### 3. 移動処理の最適化
```java
// 現在の実装（毎ティック複数テレポート）
private void updateZPosition() {
    for (CubeBlock block : blocks) {
        Location currentLoc = block.display.getLocation();
        currentLoc.setZ(worldZ);
        block.display.teleport(currentLoc); // 複数回テレポート
    }
}

// 新しい実装（エンティティのみテレポート）
private void updateEntityPosition() {
    Location entityLoc = entity.getLocation();
    entityLoc.setZ(worldZ);
    entity.teleport(entityLoc); // 1回のみテレポート
}
```

#### 4. 回転処理は既存のまま維持
```java
// 回転処理は既存のTransformation方式を維持
// setRotationはyaw/pitchのみでRoll回転ができないため
private void updateTransformation() {
    for (CubeBlock block : blocks) {
        // 既存のTransformation計算と適用（変更なし）
        block.display.setTransformation(transformation);
    }
}

// Z方向移動のみエンティティに委譲、XY移動・回転はTransformationで処理
```

## メリット

### 1. パフォーマンス向上
- **テレポート回数の削減**: 複数BlockDisplay → 1つのエンティティのみ
- **処理負荷の軽減**: 毎ティックの処理が大幅に簡素化
- **メモリ効率**: エンティティ管理の最適化

### 2. 滑らかな移動
- **自然な移動**: エンティティの移動にBlockDisplayが自動追従
- **アニメーション品質向上**: Minecraftの標準的な移動システムを活用
- **フレームレート安定**: 処理負荷軽減による安定性向上

### 3. コードの簡素化
- **保守性向上**: Z軸移動処理の簡素化
- **デバッグ容易**: Z軸移動の処理が1つのエンティティに集約
- **拡張性**: 将来的な機能追加が容易

## 実装手順

### Phase 1: エンティティの導入
1. PlayerCubeクラスに汎用エンティティを追加（Entity基底クラス使用）
2. エンティティの初期設定（透明化、AI無効化等）
3. 既存のBlockDisplay生成ロジックを維持

### Phase 2: BlockDisplayマウント
1. 生成されたBlockDisplayをエンティティにマウント
2. マウント後の動作確認
3. 既存の移動ロジックとの互換性確認

### Phase 3: 移動処理の最適化
1. Z軸移動をエンティティのテレポートに変更
2. 既存のupdateZPosition()メソッドを置き換え
3. 動作テストとパフォーマンス測定

### Phase 4: 最終調整
1. Z軸移動の最適化完了確認
2. XY移動・回転処理の動作確認
3. 最終的な動作テストとパフォーマンス測定

## 注意事項

### 1. 既存機能の保持
- 衝突判定ロジックは変更不要
- 穴検出機能は維持
- プレイヤー操作（WASD、回転）は変更不要

### 2. 互換性
- CubeCameraとの連携は維持
- 既存のゲームロジックは変更なし
- プレイヤー体験の向上のみ

### 3. テスト項目
- Z軸移動の滑らかさ
- XY軸移動・回転の正確性（既存機能の確認）
- 衝突判定の精度
- パフォーマンスの改善

## 参考実装

CubeCameraクラスでのエンティティ使用例を参考に実装：

```java
// CubeCamera.java より（汎用エンティティ版）
LivingEntity entity = (LivingEntity) world.spawnEntity(initialLocation, EntityType.BEE);
entity.setInvulnerable(true);
entity.setGravity(false);
entity.setSilent(true);
entity.setAI(false);
entity.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0, false, false));
this.entity = entity;
```

この実装パターンをPlayerCubeに適用することで、同様の最適化を実現できます。Entity基底クラスを使用することで、将来的にエンティティタイプを変更する際も柔軟に対応できます。

## 期待される効果

1. **処理負荷**: Z軸移動で約50%削減（複数BlockDisplay → 1つのエンティティ）
2. **Z軸移動の滑らかさ**: 大幅改善
3. **コードの保守性**: Z軸移動処理の簡素化
4. **拡張性**: 将来的な機能追加が容易

この最適化により、Z軸移動がより快適で安定したゲーム体験を提供できるようになります。XY移動・回転は既存の高精度なTransformation方式を維持します。
