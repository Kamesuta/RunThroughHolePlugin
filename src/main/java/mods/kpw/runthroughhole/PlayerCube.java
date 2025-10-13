package mods.kpw.runthroughhole;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class PlayerCube {
    // 複数のブロックを管理
    private List<CubeBlock> blocks;
    
    public Quaternionf rotation;
    public Vector3f gridPosition; // グリッド位置（ブロック単位）
    
    private Location baseLocation; // 基準位置（プレイヤーの固定位置）
    private World world;
    
    // 3x3x3のブロック配列（テトリミノ風）
    public boolean[][][] blockShape = new boolean[3][3][3];
    
    // 自動前進用
    private static final float FORWARD_SPEED = 0.15f; // 1tickあたりの前進量（ブロック単位）
    private static final float BOOST_SPEED = FORWARD_SPEED * 3; // 加速時の前進量（3倍速）
    private float forwardProgress = 0f; // 前進の進行度（0～1で1マス分）
    private boolean isBoosting = false; // 加速中かどうか
    
    // 穴通過状態管理（他のクラスでも使用可能）
    private boolean isInHole = false; // 現在穴の中にいるかどうか
    private boolean prevIsInHole = false; // 前回の穴通過状態（状態変化検知用）
    private Location lastHoleLocation = null; // 最後に検出した穴の位置
    private static final double HOLE_PASS_MARGIN = 1.0; // 穴通過判定のマージン
    
    public float getForwardProgress() {
        return forwardProgress;
    }
    
    /**
     * 穴通過状態を取得
     * @return 現在穴の中にいるかどうか
     */
    public boolean isInHole() {
        return isInHole;
    }
    
    /**
     * 最後に検出した穴の位置を取得
     * @return 最後の穴位置
     */
    public Location getLastHoleLocation() {
        return lastHoleLocation;
    }
    
    /**
     * 穴通過状態が変化したかどうかを取得
     * @return 前回から状態が変化した場合true
     */
    public boolean hasHoleStateChanged() {
        return isInHole != prevIsInHole;
    }
    
    /**
     * 穴通過状態を更新（カメラ用）
     * @param holeLocation 検出した穴の位置（nullの場合は穴なし）
     * @param cameraAbsoluteZ カメラの絶対Z座標
     */
    public void updateHoleStatusForCamera(Location holeLocation, double cameraAbsoluteZ) {
        // 前回の状態を保存
        prevIsInHole = isInHole;
        
        if (holeLocation != null) {
            // 穴が見つかった場合
            if (!isInHole) {
                // 初めて穴を検出した
                isInHole = true;
            }
            // 穴を検出し続けている間、lastHoleLocationを更新し続ける（長いトンネル対応）
            lastHoleLocation = holeLocation.clone();
        } else {
            // 穴が見つからない場合
            if (isInHole) {
                // カメラが穴のZ座標+余裕マージンを超えたかチェック
                if (lastHoleLocation != null && cameraAbsoluteZ > lastHoleLocation.getZ() + HOLE_PASS_MARGIN) {
                    // カメラが穴を十分通過した→穴モードを解除
                    isInHole = false;
                    lastHoleLocation = null;
                }
            }
        }
    }
    
    public PlayerCube(World world, Location baseLocation) {
        this.world = world;
        this.baseLocation = baseLocation;
        this.gridPosition = new Vector3f(0, 0, 0);
        this.blocks = new ArrayList<>();
        
        // 初期視点に合わせてBlockDisplayの回転も初期化 (Z+方向: Yaw 0, Pitch 0)
        this.rotation = new Quaternionf().rotateY((float) Math.toRadians(90.0f));
        
        // デフォルトでテトリミノ風のブロックを配置
        blockShape[1][0][0] = true;
        blockShape[1][0][1] = true;
        blockShape[0][1][1] = true;
        blockShape[1][1][1] = true;
        blockShape[2][1][1] = true;
        blockShape[2][2][1] = true;
        
        // BlockDisplayを生成
        createDisplays();
    }
    
    // 3x3x3配列に基づいてBlockDisplayを生成
    private void createDisplays() {
        // 既存のブロックをクリア
        for (CubeBlock block : blocks) {
            block.display.remove();
        }
        blocks.clear();
        
        // blockShape配列をスキャンして、trueのブロックに対してDisplayを作成
        for (int x = 0; x < 3; x++) {
            for (int y = 0; y < 3; y++) {
                for (int z = 0; z < 3; z++) {
                    if (blockShape[x][y][z]) {
                        // 相対オフセット（中心を(1,1,1)として、-1～1の範囲）
                        Vector3f offset = new Vector3f(x - 1, y - 1, z - 1);
                        
                        // BlockDisplayをスポーン（基準位置の上1.5ブロック）
                        Location spawnLoc = baseLocation.clone().add(0, 1.5, 0);
                        BlockDisplay display = world.spawn(spawnLoc, BlockDisplay.class);
                        display.setBlock(Material.FLETCHING_TABLE.createBlockData());
                        
                        // Interpolationの初期設定
                        display.setInterpolationDuration(10); // 10tick = 0.5秒でスムーズに移動
                        display.setInterpolationDelay(0);
                        
                        // Blockオブジェクトを作成してリストに追加
                        blocks.add(new CubeBlock(display, offset));
                    }
                }
            }
        }
        
        // 初回配置時に正しい位置に更新
        updateTransformation();
    }
    
    // グリッド位置を移動（XY方向のみ）
    public void move(Vector3f delta) {
        this.gridPosition.add(delta);
        // XY移動はTransformationで更新（Zは触らない）
        updateTransformation();
    }
    
    // 加速状態を設定
    public void setBoosting(boolean boosting) {
        this.isBoosting = boosting;
    }
    
    // 自動前進（毎tick呼び出される）- Z軸のテレポートのみ
    public void autoForward() {
        // 加速中かどうかで速度を変更
        float currentSpeed = isBoosting ? BOOST_SPEED : FORWARD_SPEED;
        forwardProgress += currentSpeed;
        
        // 1マス分進んだらグリッド位置を更新
        if (forwardProgress >= 1.0f) {
            gridPosition.z += 1;
            forwardProgress -= 1.0f;
        }
        
        // Z位置のみテレポートで更新（毎tick）
        updateZPosition();
    }
    
    // Z軸位置のみテレポートで更新
    private void updateZPosition() {
        double worldZ = baseLocation.getZ() + gridPosition.z + forwardProgress;
        
        for (CubeBlock block : blocks) {
            Location currentLoc = block.display.getLocation();
            currentLoc.setZ(worldZ);
            block.display.teleport(currentLoc);
        }
    }
    
    // 回転を適用
    public void applyRotation(Quaternionf newRotation) {
        // 現在の回転に新しい回転を適用
        Quaternionf rotation = new Quaternionf()
                .mul(newRotation)
                .mul(this.rotation);
        this.rotation = rotation;
        
        updateTransformation();
    }
    
    // BlockDisplayのTransformationを更新（XY位置と回転）
    private void updateTransformation() {
        // 各BlockDisplayを更新
        for (CubeBlock block : blocks) {
            Transformation transformation = block.display.getTransformation();
            
            // アニメーション設定
            block.display.setInterpolationDuration(5); // 5tick = 0.25秒
            block.display.setInterpolationDelay(0);
            
            // ブロックのローカルオフセットに回転を適用
            Vector3f rotatedOffset = new Vector3f(block.offset);
            rotatedOffset.rotate(rotation);
            
            // BlockDisplayの中心オフセット（-0.5, -0.5, -0.5）に回転を適用
            Vector3f centerOffset = new Vector3f(-0.5f, -0.5f, -0.5f);
            centerOffset.rotate(rotation);
            
            // XY方向の相対位置（Z=0、Zはテレポートで管理）
            Vector3f translation = new Vector3f(gridPosition.x, gridPosition.y, 0);
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
        return checkCollision(new Vector3f(0, 0, 0));
    }
    
    // 衝突検出（オフセット指定可能）：衝突しているブロックのStreamを返す
    public Stream<CubeBlock> checkCollision(Vector3f positionOffset) {
        return blocks.stream()
            .filter(block -> {
                // ブロックのワールド座標を計算
                Location blockWorldLoc = getBlockWorldLocation(block, positionOffset);
                
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
        return checkCollision(delta).findAny().isPresent();
    }
    
    // 穴開き壁を検出：キューブの中心位置を返す（穴がない場合はnull）
    public Location detectHole() {
        // キューブの現在のZ座標で5x5マスをチェック
        double currentZ = baseLocation.getZ() + gridPosition.z + forwardProgress;
        int checkZ = (int) Math.floor(currentZ);
        
        // キューブの中心位置（XY）を計算
        double centerX = baseLocation.getX() + gridPosition.x;
        double centerY = baseLocation.getY() + 1.0 + gridPosition.y;
        int centerBlockX = (int) Math.floor(centerX);
        int centerBlockY = (int) Math.floor(centerY);
        
        // 5x5範囲でブロックとAIRをカウント
        int blockCount = 0;
        int airCount = 0;
        
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                Location checkLoc = new Location(world, centerBlockX + dx, centerBlockY + dy, checkZ);
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
            return new Location(world, centerX, centerY, checkZ);
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
    
    // 各ブロックのワールド座標を計算（位置オフセット指定可能）
    private Location getBlockWorldLocation(CubeBlock block, Vector3f positionOffset) {
        // ブロックのローカルオフセットに回転を適用
        Vector3f rotatedOffset = new Vector3f(block.offset);
        rotatedOffset.rotate(rotation);
        
        // ワールド座標を計算（ブロックの中心座標に合わせるため0.5を加える）
        double worldX = baseLocation.getX() - 0.5 + gridPosition.x + positionOffset.x + rotatedOffset.x;
        double worldY = baseLocation.getY() + 1.0 + gridPosition.y + positionOffset.y + rotatedOffset.y; // 1マス下げる
        double worldZ = baseLocation.getZ() + 0.5 + gridPosition.z + positionOffset.z + forwardProgress + rotatedOffset.z;
        
        return new Location(world, 
            Math.floor(worldX), 
            Math.floor(worldY), 
            Math.floor(worldZ));
    }
    
    // クリーンアップ
    public void remove() {
        for (CubeBlock block : blocks) {
            if (block.display != null) {
                block.display.remove();
            }
        }
        blocks.clear();
    }
}

