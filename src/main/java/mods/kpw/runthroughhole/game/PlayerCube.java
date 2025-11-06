package mods.kpw.runthroughhole.game;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import io.papermc.paper.entity.TeleportFlag;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector2i;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import java.util.stream.IntStream;

public class PlayerCube {
    // キューブの範囲定数
    private static final int CUBE_RANGE = 1; // キューブは-1から+1まで（3x3x3）

    // 壁判定の範囲定数
    private static final int WALL_RANGE = 2; // 壁判定の範囲（-2から+2まで、5x5範囲）

    // 複数のブロックを管理
    private List<CubeBlock> blocks;

    public Quaternionf rotation;
    public Vector3f gridPosition; // グリッド位置（ブロック単位）

    private Location baseLocation; // 基準位置（プレイヤーの固定位置）
    private World world;

    // 蜂エンティティ（最適化用）
    private LivingEntity entity;

    // エンティティの高さオフセット（プレイヤーが乗る位置を考慮）
    // 将来的にCubeCameraのような高度な位置調整で使用予定
    private double entityHeightOffset = 0.0;

    // エンティティ位置の高さ微調整（正の値で上、負の値で下）
    private static final double ENTITY_HEIGHT_ADJUSTMENT = 0; // エンティティの高さ微調整（ブロック単位）

    // BlockDisplayのTransformation用オフセット（エンティティの高さ分を補正）
    private static final double BLOCKDISPLAY_HEIGHT_OFFSET = 0; // BlockDisplayの高さ補正（ブロック単位）

    // 3x3x3のブロック配列（テトリミノ風）
    public boolean[][][] blockShape = new boolean[CUBE_RANGE * 2 + 1][CUBE_RANGE * 2 + 1][CUBE_RANGE * 2 + 1];

    // Interpolation速度定数
    public static final int MOVE_INTERPOLATION_DURATION = 1; // 移動時のInterpolation時間（tick）
    public static final int ROTATION_INTERPOLATION_DURATION = 2; // 回転時のInterpolation時間（tick）

    // 自動前進用
    private static final float FORWARD_SPEED = 0.35f; // 1tickあたりの前進量（ブロック単位）
    private static final float BOOST_SPEED = FORWARD_SPEED * 3; // 加速時の前進量（3倍速）
    private float forwardProgress = 0f; // 前進の進行度（0～1で1マス分）
    private boolean isBoosting = false; // 加速中かどうか
    private boolean isContinuousBoosting = false; // 連続加速中かどうか

    // 減速タイマー（壁に近づいたときの停止処理用）
    private static final int SLOW_TIMEOUT_TICKS = 40; // 2秒 = 40tick
    private int slowdownTicks = 0; // 減速している時間（tick単位）
    private boolean isSlowedDown = false; // 減速中かどうか

    public float getForwardProgress() {
        return forwardProgress;
    }

    public PlayerCube(World world, Location baseLocation, boolean[][][] pattern) {
        if (pattern == null || pattern.length != 3 || pattern[0].length != 3 || pattern[0][0].length != 3) {
            throw new IllegalArgumentException("パターンは3x3x3の配列である必要があります");
        }

        this.world = world;
        this.baseLocation = baseLocation;
        this.gridPosition = new Vector3f(0, 0, 0);
        this.blocks = new ArrayList<>();
        this.rotation = new Quaternionf();

        // パターンを設定
        for (int x = 0; x < 3; x++) {
            for (int y = 0; y < 3; y++) {
                for (int z = 0; z < 3; z++) {
                    blockShape[x][y][z] = pattern[x][y][z];
                }
            }
        }

        // 蜂エンティティを初期化
        initializeEntity();

        // BlockDisplayを生成
        createDisplays();
    }

    // 蜂エンティティを初期化
    private void initializeEntity() {
        // 蜂エンティティをスポーン
        Location spawnLocation = baseLocation.clone().add(0, ENTITY_HEIGHT_ADJUSTMENT, 0);
        entity = (LivingEntity) world.spawnEntity(spawnLocation, EntityType.BEE);

        // エンティティの設定（最適化案に従って）
        entity.setInvulnerable(true);
        entity.setGravity(false);
        entity.setSilent(true);
        entity.setAI(false);
        entity.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0, false, false));

        // エンティティの高さを取得（プレイヤーが乗る位置のオフセット）
        this.entityHeightOffset = entity.getHeight();
    }

    // 3x3x3配列に基づいてBlockDisplayを生成
    private void createDisplays() {
        // 既存のブロックをクリア
        for (CubeBlock block : blocks) {
            block.display.remove();
        }
        blocks.clear();

        // blockShape配列をスキャンして、trueのブロックに対してDisplayを作成
        getCubeOffsets()
                .forEach(offset -> {
                    // BlockDisplayをスポーン
                    BlockDisplay display = world.spawn(baseLocation, BlockDisplay.class);
                    display.setBlock(Material.GLASS.createBlockData());
                    display.setBrightness(new BlockDisplay.Brightness(15, 15));

                    // Interpolationの初期設定
                    display.setInterpolationDuration(10); // 10tick = 0.5秒でスムーズに移動
                    display.setInterpolationDelay(0);

                    // Blockオブジェクトを作成してリストに追加
                    blocks.add(new CubeBlock(display, offset));
                });

        // 各BlockDisplayを蜂エンティティにマウント
        for (CubeBlock block : blocks) {
            entity.addPassenger(block.display);
        }

        // 初回配置時に正しい位置に更新
        updateTransformation();
    }

    // グリッド位置を移動（XY方向のみ）
    public boolean move(Vector3f delta) {
        // 移動先で衝突するかチェック
        if (wouldCollideAt(delta)) {
            // 衝突する場合は移動をキャンセル
            return false;
        }
        
        this.gridPosition.add(delta);
        // XY移動はTransformationで更新（Zは触らない）
        updateTransformation();
        return true; // 移動成功
    }

    // 加速状態を設定
    public void setBoosting(boolean boosting) {
        this.isBoosting = boosting;
    }

    public boolean isContinuousBoosting() {
        return isContinuousBoosting;
    }

    public void startContinuousBoosting() {
        // 連続加速中の場合のみ開始
        if (!isContinuousBoosting) {
            this.isContinuousBoosting = true;
            this.isBoosting = true;
        }
    }

    public void stopContinuousBoosting() {
        // 連続加速中の場合のみ停止
        if (isContinuousBoosting) {
            this.isContinuousBoosting = false;
            this.isBoosting = false;
        }
    }

    // 自動前進（毎tick呼び出される）- Z軸のテレポートのみ
    public void autoForward() {
        // 前方の壁との距離を取得
        double distanceToWall = getDistanceToNextWall();

        // 速度を決定
        float currentSpeed;

        if (distanceToWall < 0.0) {
            // 通れる壁の場合は通常速度（減速なし）
            currentSpeed = isBoosting ? BOOST_SPEED : FORWARD_SPEED;
            // 減速タイマーをリセット
            isSlowedDown = false;
            slowdownTicks = 0;
        } else if (distanceToWall >= 0 && distanceToWall <= 3.0) {
            // 3ブロック以内の通れない壁 → 指数関数的減速
            isSlowedDown = true;
            slowdownTicks++;

            // 2秒（40tick）経過したら減速解除
            if (slowdownTicks >= SLOW_TIMEOUT_TICKS) {
                currentSpeed = isBoosting ? BOOST_SPEED : FORWARD_SPEED;
                // タイマーはリセットせず、カウントを継続（壁を抜けるまで）
            } else {
                // 線形減速: 距離に応じて一定割合で速度が落ちる
                // distance=3.0で100%、distance=0.0で約3%の速度
                // slowFactor = distance / 3.0
                double slowFactor = distanceToWall / 3.0;

                // 基本速度に減速係数を適用
                float baseSpeed = isBoosting ? BOOST_SPEED : FORWARD_SPEED;
                currentSpeed = (float) (baseSpeed * slowFactor);

                // 最低速度を設定（完全に止まらないように）
                float minSpeed = 0.01f;
                if (currentSpeed < minSpeed) {
                    currentSpeed = minSpeed;
                }
            }
        } else {
            // 壁が遠い or 壁がない → 通常速度
            currentSpeed = isBoosting ? BOOST_SPEED : FORWARD_SPEED;
            // 減速タイマーをリセット
            isSlowedDown = false;
            slowdownTicks = 0;
        }

        forwardProgress += currentSpeed;

        // 1マス分進んだらグリッド位置を更新
        if (forwardProgress >= 1.0f) {
            gridPosition.z += 1;
            forwardProgress -= 1.0f;
        }

        // Z位置のみテレポートで更新（毎tick）
        updateZPosition();
    }

    // Z軸位置のみテレポートで更新（最適化版：蜂エンティティのみテレポート）
    private void updateZPosition() {
        double worldZ = baseLocation.getZ() + gridPosition.z + forwardProgress;

        // 蜂エンティティのみをテレポート（BlockDisplayは自動追従）
        Location entityLoc = entity.getLocation();
        entityLoc.setZ(worldZ);
        entity.teleport(entityLoc, TeleportFlag.EntityState.RETAIN_PASSENGERS);
    }

    // 回転を適用
    public boolean applyRotation(Quaternionf newRotation) {
        // 回転後に衝突するかチェック
        if (wouldCollideAfterRotation(newRotation)) {
            // 衝突する場合は回転をキャンセル
            return false;
        }

        // 現在の回転に新しい回転を適用
        Quaternionf rotation = new Quaternionf()
                .mul(newRotation)
                .mul(this.rotation);
        this.rotation = rotation;

        updateTransformation();
        return true; // 回転成功
    }

    // BlockDisplayのTransformationを更新（XY位置と回転）
    private void updateTransformation() {
        // 各BlockDisplayを更新
        for (CubeBlock block : blocks) {
            Transformation transformation = block.display.getTransformation();

            // アニメーション設定（移動・回転共通）
            block.display
                    .setInterpolationDuration(Math.max(MOVE_INTERPOLATION_DURATION, ROTATION_INTERPOLATION_DURATION)); // 移動・回転時のInterpolation時間
            block.display.setInterpolationDelay(0);

            // ブロックのローカルオフセットに回転を適用
            Vector3f rotatedOffset = new Vector3f(block.offset);
            rotatedOffset.rotate(rotation);

            // BlockDisplayの中心オフセット（-0.5, -0.5, -0.5）に回転を適用
            Vector3f centerOffset = new Vector3f(-0.5f, -0.5f, -0.5f);
            centerOffset.rotate(rotation);

            // XY方向の相対位置（Z=0、Zはテレポートで管理）
            // BlockDisplayの高さオフセットを適用（エンティティの高さ分を補正）
            Vector3f translation = new Vector3f(gridPosition.x,
                    gridPosition.y + (float) (BLOCKDISPLAY_HEIGHT_OFFSET - entityHeightOffset), 0);
            translation.add(rotatedOffset);
            translation.add(centerOffset);

            // Transformationに設定
            transformation.getLeftRotation().set(rotation);
            transformation.getTranslation().set(translation);

            block.display.setTransformation(transformation);
        }
    }

    // 衝突検出：衝突しているブロックのStreamを返す
    public Stream<CubeBlock> checkCollision() {
        return checkCollision(new Vector3f(0, 0, 0), null);
    }

    // 衝突検出（オフセット指定可能）：衝突しているブロックのStreamを返す
    public Stream<CubeBlock> checkCollision(Vector3f positionOffset) {
        return checkCollision(positionOffset, null);
    }

    // 衝突検出（位置オフセットと回転指定可能）：衝突しているブロックのStreamを返す
    public Stream<CubeBlock> checkCollision(Vector3f positionOffset, Quaternionf rotationOffset) {
        // 使用する回転を決定（回転オフセットが指定されていない場合は現在の回転を使用）
        Quaternionf testRotation = rotationOffset != null ? new Quaternionf().mul(rotationOffset).mul(this.rotation)
                : this.rotation;

        return blocks.stream()
                .filter(block -> {
                    // ブロックのワールド座標を計算（回転も考慮）
                    Location blockWorldLoc = getBlockWorldLocation(block, positionOffset, testRotation);

                    // その座標のブロックをチェック
                    Block blockAt = world.getBlockAt(blockWorldLoc);
                    Material material = blockAt.getType();

                    // 衝突判定（AIR系とGLASS以外に衝突）
                    return material != Material.AIR
                            && material != Material.CAVE_AIR
                            && material != Material.VOID_AIR
                            && material != Material.GLASS;
                });
    }

    // 移動先で衝突するかチェック（移動前の判定用）
    public boolean wouldCollideAt(Vector3f delta) {
        // checkCollisionを流用して、移動先で衝突するブロックがあるかチェック
        return checkCollision(delta, null).findAny().isPresent();
    }

    // 回転後に衝突するかチェック（回転前の判定用）
    public boolean wouldCollideAfterRotation(Quaternionf newRotation) {
        // checkCollisionを流用して、回転後に衝突するブロックがあるかチェック
        return checkCollision(new Vector3f(0, 0, 0), newRotation).findAny().isPresent();
    }

    // 穴開き壁を検出：キューブの中心位置を返す（穴がない場合はnull）
    public Location detectHole() {
        // キューブの現在位置を取得
        Location currentLocation = getCurrentLocation();
        Location currentBlockLocation = currentLocation.toBlockLocation();

        // 5x5範囲でブロックとAIRをカウント
        int blockCount = 0;
        int airCount = 0;

        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                Location checkLoc = currentBlockLocation.clone().add(dx, dy, 0);
                Block block = world.getBlockAt(checkLoc);
                Material material = block.getType();

                if (material == Material.AIR || material == Material.CAVE_AIR || material == Material.VOID_AIR) {
                    airCount++;
                } else {
                    blockCount++;
                }
            }
        }

        // ブロックが10個以上あり、AIRが3個以上あれば「穴開き壁」と判定
        if (blockCount >= 10 && airCount >= 3) {
            // キューブの中心位置を返す（プレイヤーの頭の位置がここに来るように調整される）
            return currentLocation;
        }

        return null;
    }

    // 特定のブロックの色を変更
    public void changeBlockColor(CubeBlock block, Material material) {
        if (block != null && block.display != null) {
            block.display.setBlock(material.createBlockData());
        }
    }

    // 特定のブロックの位置を取得（演出用）
    public Location getBlockDisplayLocation(CubeBlock block) {
        if (block != null && block.display != null) {
            return block.display.getLocation();
        }
        return null;
    }

    // 各ブロックのワールド座標を計算（位置オフセットと回転指定可能）
    private Location getBlockWorldLocation(CubeBlock block, Vector3f positionOffset, Quaternionf rotation) {
        // ブロックのローカルオフセットに回転を適用
        Vector3f rotatedOffset = new Vector3f(block.offset);
        rotatedOffset.rotate(rotation);

        // ワールド座標を計算（ブロックの中心座標に合わせるため0.5を加える）
        // BlockDisplayの高さオフセットを適用（エンティティの高さ分を補正）
        Vector3f location = new Vector3f(baseLocation.toVector().toVector3f())
                .add(gridPosition)
                .add(positionOffset)
                .add(rotatedOffset)
                .add(0.0f, (float) BLOCKDISPLAY_HEIGHT_OFFSET, forwardProgress);

        return Vector.fromJOML(location).toLocation(world).toBlockLocation();
    }

    /**
     * キューブの現在位置を取得（カメラ用）
     *
     * @return キューブの現在位置
     */
    public Location getCurrentLocation() {
        Vector3f currentPos = baseLocation.toVector().toVector3f()
                .add(gridPosition)
                .add(0, 0, forwardProgress);
        return Vector.fromJOML(currentPos).toLocation(world);
    }

    /**
     * 蜂エンティティを取得（他のシステムがBlockDisplayをマウントするため）
     *
     * @return 蜂エンティティ
     */
    public LivingEntity getEntity() {
        return entity;
    }

    // クリーンアップ
    public void remove() {
        for (CubeBlock block : blocks) {
            if (block.display != null) {
                block.display.remove();
            }
        }
        blocks.clear();

        // 蜂エンティティも削除
        if (entity != null) {
            entity.remove();
        }
    }

    /**
     * キューブの有効なブロックのoffsetをStreamとして返す
     * 
     * @return 有効なブロックのVector3f offsetのStream
     */
    public Stream<Vector3f> getCubeOffsets() {
        return IntStream.rangeClosed(-CUBE_RANGE, CUBE_RANGE)
                .boxed()
                .flatMap(x -> IntStream.rangeClosed(-CUBE_RANGE, CUBE_RANGE)
                        .boxed()
                        .flatMap(y -> IntStream.rangeClosed(-CUBE_RANGE, CUBE_RANGE)
                                .filter(z -> blockShape[x + CUBE_RANGE][y + CUBE_RANGE][z + CUBE_RANGE])
                                .mapToObj(z -> {
                                    Vector3f offset = new Vector3f(x, y, z);
                                    offset.rotate(rotation);
                                    return offset;
                                })));
    }

    /**
     * キューブの有効なブロックの世界座標をStreamとして返す
     * 
     * @param cubeLocation 中心位置 (baseLocationやwallLocation)
     * @return 有効なブロックの世界座標LocationのStream
     */
    public Stream<Location> getCubeWorldPositions() {
        Location currentLocation = getCurrentLocation();

        return getCubeOffsets().map(offset -> currentLocation.toBlockLocation()
                .add(Math.round(offset.x), Math.round(offset.y), Math.round(offset.z)));
    }

    /**
     * キューブを投影した壁ブロックの世界座標をStreamとして返す
     * 
     * @param wallLocation 壁の位置
     * @return 有効なブロックの世界座標LocationのStream
     */
    public Stream<Location> getCubeWallPositions(Location wallLocation) {
        Location currentWallLocation = getCurrentLocation().toBlockLocation();
        currentWallLocation.setZ(wallLocation.getBlockZ());

        return getCubeOffsets()
                .map(offset -> new Vector2i(Math.round(offset.x), Math.round(offset.y)))
                .distinct()
                .map(offset -> currentWallLocation.clone().add(offset.x, offset.y, 0));
    }

    /**
     * マテリアルが空気かどうかを判定
     * 
     * @param material チェックするマテリアル
     * @return 空気の場合はtrue
     */
    public static boolean isAir(Material material) {
        return material == Material.AIR || material == Material.CAVE_AIR || material == Material.VOID_AIR;
    }

    /**
     * 指定範囲の壁ブロックの位置をStreamとして返す
     * 
     * @param center 中心位置
     * @return 壁ブロックの位置のStream
     */
    public Stream<Location> getWallBlocks(Location center) {
        Location blockLocation = center.toBlockLocation();

        return IntStream.rangeClosed(-WALL_RANGE, WALL_RANGE)
                .boxed()
                .flatMap(dx -> IntStream.rangeClosed(-WALL_RANGE, WALL_RANGE)
                        .mapToObj(dy -> blockLocation.clone().add(dx, dy, 0)));
    }

    /**
     * 前方の壁を探索
     * 
     * @param startZ 探索開始Z座標
     * @param endZ   探索終了Z座標
     * @return 壁の位置（見つからなければnull）
     */
    public Location findNextWall(double startZ, double endZ) {
        // キューブの現在位置を取得
        Location currentLocation = getCurrentLocation();
        Location currentBlockLocation = currentLocation.toBlockLocation();

        // Z座標を前方に探索
        for (int z = (int) Math.floor(startZ); z <= (int) Math.floor(endZ); z++) {
            Location checkLocation = currentBlockLocation.clone();
            checkLocation.setZ(z);

            // 5x5範囲でブロックとAIRをカウント
            long blockCount = getWallBlocks(checkLocation)
                    .filter(checkLoc -> !isAir(world.getBlockAt(checkLoc).getType()))
                    .count();

            // ブロックが10個以上あれば「穴開き壁」と判定
            if (blockCount >= 10) {
                return checkLocation;
            }
        }

        return null;
    }

    /**
     * 連続加速機能の処理
     *
     * @param preview プレビュー表示オブジェクト
     */
    public void handleContinuousBoosting(HolePreview preview) {
        // 連続加速中の場合、通り過ぎるまで加速を継続
        if (isContinuousBoosting) {
            // 穴から出たかチェック
            if (preview != null) {
                Boolean isGreen = preview.isPreviewGreen();
                // 穴の中にいない場合（通り過ぎた場合）
                if (isGreen == null || !isGreen) {
                    // 連続加速を停止
                    stopContinuousBoosting();
                }
            }
        }
    }

    /**
     * 次の壁との最短距離を取得（減速処理用）
     *
     * @return 壁との最短距離（壁が見つからない場合は-1、通れる壁の場合は-2）
     */
    public double getDistanceToNextWall() {
        // 現在位置から前方5ブロック以内の壁を探索
        Location currentLocation = getCurrentLocation();
        double startZ = currentLocation.getZ();
        double endZ = startZ + 5.0;

        // 前方の壁を探索
        Location wallLocation = findNextWall(startZ, endZ);

        // 壁が見つからなければ-1を返す
        if (wallLocation == null) {
            return -1.0;
        }

        // 壁のZ座標
        double wallZ = wallLocation.getBlockZ();

        // キューブの全ブロックと壁の最短距離を計算
        double minDistance = Double.MAX_VALUE;

        for (CubeBlock block : blocks) {
            // ブロックのローカルオフセットに回転を適用
            Vector3f rotatedOffset = new Vector3f(block.offset);
            rotatedOffset.rotate(rotation);

            // ブロックのワールドZ座標を計算
            double blockWorldZ = startZ + rotatedOffset.z;

            // 壁との距離
            double distance = wallZ - blockWorldZ;

            // 最短距離を更新
            if (distance < minDistance) {
                minDistance = distance;
            }
        }

        // 壁が通れるかチェック（キューブの形状が壁の穴と一致するか）
        boolean canPassThrough = getCubeWallPositions(wallLocation)
                .allMatch(location -> isAir(world.getBlockAt(location).getType()));

        // 通れる壁の場合は-2を返す
        if (canPassThrough) {
            return -2.0;
        }

        return minDistance;
    }

    /**
     * 壁接近警告が必要かどうかを判定
     *
     * @return 警告が必要な場合はtrue
     */
    public boolean shouldShowWarning() {
        double distance = getDistanceToNextWall();
        // 1.5ブロック以内 & 通れない壁の場合
        return distance >= 0 && distance <= 1.5;
    }
}
