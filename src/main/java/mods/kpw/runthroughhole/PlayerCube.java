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
    private static final float FORWARD_SPEED = 0.05f; // 1tickあたりの前進量（ブロック単位）
    private float forwardProgress = 0f; // 前進の進行度（0～1で1マス分）
    
    public float getForwardProgress() {
        return forwardProgress;
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
    }
    
    // グリッド位置を移動（XY方向のみ）
    public void move(Vector3f delta) {
        this.gridPosition.add(delta);
        // XY移動はTransformationで更新（Zは触らない）
        updateTransformation();
    }
    
    // 自動前進（毎tick呼び出される）- Z軸のテレポートのみ
    public void autoForward() {
        forwardProgress += FORWARD_SPEED;
        
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
    
    // 衝突検出：キューブがMinecraftのブロックと衝突しているかチェック
    public boolean checkCollision() {
        for (CubeBlock block : blocks) {
            // ブロックのワールド座標を計算
            Location blockWorldLoc = getBlockWorldLocation(block);
            
            // その座標のブロックをチェック
            Block blockAt = world.getBlockAt(blockWorldLoc);
            Material material = blockAt.getType();
            if (material != Material.AIR && material != Material.CAVE_AIR && material != Material.VOID_AIR && material != Material.GLASS) {
                // blockAt.setType(Material.GLASS); // デバッグ用可視化
                return true; // 衝突あり
            }
        }
        return false; // 衝突なし
    }
    
    // 衝突したブロックのリストを取得
    public List<CubeBlock> getCollidedBlocks() {
        List<CubeBlock> collidedBlocks = new ArrayList<>();
        for (CubeBlock block : blocks) {
            // ブロックのワールド座標を計算
            Location blockWorldLoc = getBlockWorldLocation(block);
            
            // その座標のブロックをチェック
            Block blockAt = world.getBlockAt(blockWorldLoc);
            Material material = blockAt.getType();
            if (material != Material.AIR && material != Material.CAVE_AIR && material != Material.VOID_AIR && material != Material.GLASS) {
                collidedBlocks.add(block);
            }
        }
        return collidedBlocks;
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
    
    // 各ブロックのワールド座標を計算
    private Location getBlockWorldLocation(CubeBlock block) {
        return getBlockWorldLocation(block, 0.0);
    }
    
    // 各ブロックのワールド座標を計算（Zオフセット指定可能）
    private Location getBlockWorldLocation(CubeBlock block, double zOffset) {
        // ブロックのローカルオフセットに回転を適用
        Vector3f rotatedOffset = new Vector3f(block.offset);
        rotatedOffset.rotate(rotation);
        
        // ワールド座標を計算（ブロックの中心座標に合わせるため0.5を加える）
        double worldX = baseLocation.getX() - 0.5 + gridPosition.x + rotatedOffset.x;
        double worldY = baseLocation.getY() + 1.0 + gridPosition.y + rotatedOffset.y; // 1マス下げる
        double worldZ = baseLocation.getZ() + 0.5 + gridPosition.z + forwardProgress + rotatedOffset.z + zOffset;
        
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

