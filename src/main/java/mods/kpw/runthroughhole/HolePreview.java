package mods.kpw.runthroughhole;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.util.Transformation;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * 前方の壁の穴に対してプレビュー表示を行うクラス
 */
public class HolePreview {
    private World world;
    private List<BlockDisplay> previewPanels;
    
    public HolePreview(World world) {
        this.world = world;
        this.previewPanels = new ArrayList<>();
    }
    
    /**
     * プレビューを更新
     * @param cube プレイヤーのキューブ
     * @param baseLocation 基準位置
     */
    public void update(PlayerCube cube, Location baseLocation) {
        // 既存のプレビューをクリア
        clear();
        
        // 前方の壁を探索（キューブの現在位置から前方5〜10ブロック先）
        double currentZ = baseLocation.getZ() + cube.gridPosition.z + cube.getForwardProgress();
        
        // 5ブロック先から20ブロック先まで探索
        Location wallLocation = findNextWall(cube, baseLocation, currentZ + 5, currentZ + 20);
        
        if (wallLocation == null) {
            return; // 壁が見つからなかった
        }
        
        // 壁のZ座標
        int wallZ = wallLocation.getBlockZ();
        
        // キューブの中心位置（XY）を計算
        double centerX = baseLocation.getX() + cube.gridPosition.x;
        double centerY = baseLocation.getY() + 1.0 + cube.gridPosition.y;
        int centerBlockX = (int) Math.floor(centerX);
        int centerBlockY = (int) Math.floor(centerY);
        
        // キューブの形状と回転を使って、通過可能な穴を検出
        // 3x3x3のキューブの各ブロックが壁のどこに当たるかをチェック
        for (int x = 0; x < 3; x++) {
            for (int y = 0; y < 3; y++) {
                for (int z = 0; z < 3; z++) {
                    if (cube.blockShape[x][y][z]) {
                        // このブロックの回転後の位置を計算
                        Vector3f offset = new Vector3f(x - 1, y - 1, z - 1);
                        offset.rotate(cube.rotation);
                        
                        // 壁上の座標
                        int blockX = centerBlockX + Math.round(offset.x);
                        int blockY = centerBlockY + Math.round(offset.y);
                        
                        // この位置が空気（穴）かチェック
                        Location checkLoc = new Location(world, blockX, blockY, wallZ);
                        Material material = world.getBlockAt(checkLoc).getType();
                        
                        if (material == Material.AIR || material == Material.CAVE_AIR || material == Material.VOID_AIR) {
                            // この位置にプレビューパネルを表示（壁の1マス手前）
                            Location previewLoc = new Location(world, blockX, blockY, wallZ - 1);
                            createPreviewPanel(previewLoc);
                        }
                    }
                }
            }
        }
    }
    
    /**
     * 前方の壁を探索
     * @param cube プレイヤーのキューブ
     * @param baseLocation 基準位置
     * @param startZ 探索開始Z座標
     * @param endZ 探索終了Z座標
     * @return 壁の位置（見つからなければnull）
     */
    private Location findNextWall(PlayerCube cube, Location baseLocation, double startZ, double endZ) {
        // キューブの中心位置（XY）を計算
        double centerX = baseLocation.getX() + cube.gridPosition.x;
        double centerY = baseLocation.getY() + 1.0 + cube.gridPosition.y;
        int centerBlockX = (int) Math.floor(centerX);
        int centerBlockY = (int) Math.floor(centerY);
        
        // Z座標を前方に探索
        for (int z = (int) Math.floor(startZ); z <= (int) Math.floor(endZ); z++) {
            // 5x5範囲でブロックとAIRをカウント
            int blockCount = 0;
            int airCount = 0;
            
            for (int dx = -2; dx <= 2; dx++) {
                for (int dy = -2; dy <= 2; dy++) {
                    Location checkLoc = new Location(world, centerBlockX + dx, centerBlockY + dy, z);
                    Material material = world.getBlockAt(checkLoc).getType();
                    
                    if (material == Material.AIR || material == Material.CAVE_AIR || material == Material.VOID_AIR) {
                        airCount++;
                    } else {
                        blockCount++;
                    }
                }
            }
            
            // ブロックが10個以上あり、AIRが3個以上あれば「穴開き壁」と判定
            if (blockCount >= 10 && airCount >= 3) {
                return new Location(world, centerX, centerY, z);
            }
        }
        
        return null;
    }
    
    /**
     * プレビューパネルを作成
     * @param location パネルの位置
     */
    private void createPreviewPanel(Location location) {
        // BlockDisplayをスポーン（ブロックの中心）
        Location spawnLoc = location.clone().add(0.5, 0.5, 0.5);
        BlockDisplay display = world.spawn(spawnLoc, BlockDisplay.class);
        display.setBlock(Material.LIME_STAINED_GLASS.createBlockData());
        
        // Transformationを設定（0.8x0.8x0.1の薄型パネル）
        Transformation transformation = display.getTransformation();
        
        // スケールを設定（薄型パネル、Z方向が薄い）
        Vector3f scale = new Vector3f(0.8f, 0.8f, 0.1f);
        transformation.getScale().set(scale);
        
        // 位置を調整（壁の1マス手前のブロック空間の奥側に配置）
        // X: -0.4 でブロック中心から0.4左（0.8スケールの中心）
        // Y: -0.4 でブロック中心から0.4下（0.8スケールの中心）
        // Z: 0.45 でブロックの奥側（0.1スケールなので、壁側に薄型パネル）
        Vector3f translation = new Vector3f(-0.4f, -0.4f, 0.45f);
        transformation.getTranslation().set(translation);
        
        display.setTransformation(transformation);
        
        // Interpolationの設定
        display.setInterpolationDuration(5); // 5tick = 0.25秒でスムーズに表示
        display.setInterpolationDelay(0);
        
        previewPanels.add(display);
    }
    
    /**
     * プレビューをクリア
     */
    public void clear() {
        for (BlockDisplay display : previewPanels) {
            display.remove();
        }
        previewPanels.clear();
    }
    
    /**
     * クリーンアップ（ゲーム終了時）
     */
    public void cleanup() {
        clear();
    }
}

