package mods.kpw.runthroughhole;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.util.Transformation;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 前方の壁の穴に対してプレビュー表示を行うクラス
 */
public class HolePreview {
    private World world;
    
    // 位置をキーとしてパネルを管理（差分更新用）
    private Map<String, BlockDisplay> previewPanelMap;
    
    public HolePreview(World world) {
        this.world = world;
        this.previewPanelMap = new HashMap<>();
    }
    
    /**
     * プレビューを更新
     * @param cube プレイヤーのキューブ
     * @param baseLocation 基準位置
     */
    public void update(PlayerCube cube, Location baseLocation) {
        // 前方の壁を探索
        double currentZ = baseLocation.getZ() + cube.gridPosition.z + cube.getForwardProgress();
        
        // キューブの前方1ブロック先から20ブロック先まで探索（キューブが壁に入るまで検出できるように）
        Location wallLocation = findNextWall(cube, baseLocation, currentZ + 1, currentZ + 20);
        
        if (wallLocation == null) {
            // 壁が見つからなかった場合、プレビューをクリア
            if (!previewPanelMap.isEmpty()) {
                clear();
            }
            return;
        }
        
        // 壁のZ座標
        int wallZ = wallLocation.getBlockZ();
        
        // キューブの中心位置（XY）を計算
        double centerX = baseLocation.getX() + cube.gridPosition.x;
        double centerY = baseLocation.getY() + 1.0 + cube.gridPosition.y;
        int centerBlockX = (int) Math.floor(centerX);
        int centerBlockY = (int) Math.floor(centerY);
        
        // まず、キューブの全ブロックが壁を通れるかチェック
        boolean canPassThrough = true;
        
        // キューブの形状と回転を使って、各ブロックの位置をチェック
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
                        
                        // 1つでも壁にぶつかる場合は通れない
                        if (material != Material.AIR && material != Material.CAVE_AIR && material != Material.VOID_AIR) {
                            canPassThrough = false;
                            break;
                        }
                    }
                }
                if (!canPassThrough) break;
            }
            if (!canPassThrough) break;
        }
        
        // パネルの色を決定（通れるなら緑、通れないなら白）
        Material panelMaterial = canPassThrough ? Material.LIME_STAINED_GLASS : Material.WHITE_STAINED_GLASS;
        
        // 現在必要なパネル位置のセットを作成
        Set<String> currentPositions = new HashSet<>();
        
        // キューブの全ブロック位置にプレビューパネルを表示
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
                        
                        // この位置にプレビューパネルが必要（壁の1マス手前）
                        int previewZ = wallZ - 1;
                        String posKey = blockX + "," + blockY + "," + previewZ;
                        currentPositions.add(posKey);
                        
                        // 既存のパネルがある場合は色を更新、ない場合は作成
                        if (previewPanelMap.containsKey(posKey)) {
                            BlockDisplay existingDisplay = previewPanelMap.get(posKey);
                            // 色が変わった場合のみ更新
                            if (existingDisplay.getBlock().getMaterial() != panelMaterial) {
                                existingDisplay.setBlock(panelMaterial.createBlockData());
                            }
                        } else {
                            // 新規作成
                            Location previewLoc = new Location(world, blockX, blockY, previewZ);
                            BlockDisplay display = createPreviewPanel(previewLoc, panelMaterial);
                            previewPanelMap.put(posKey, display);
                        }
                    }
                }
            }
        }
        
        // 不要になったパネルを削除
        Set<String> toRemove = new HashSet<>();
        for (String posKey : previewPanelMap.keySet()) {
            if (!currentPositions.contains(posKey)) {
                BlockDisplay display = previewPanelMap.get(posKey);
                display.remove();
                toRemove.add(posKey);
            }
        }
        for (String posKey : toRemove) {
            previewPanelMap.remove(posKey);
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
     * @param material パネルのマテリアル
     * @return 作成されたBlockDisplay
     */
    private BlockDisplay createPreviewPanel(Location location, Material material) {
        // BlockDisplayをスポーン（ブロックの中心）
        Location spawnLoc = location.clone().add(0.5, 0.5, 0.5);
        BlockDisplay display = world.spawn(spawnLoc, BlockDisplay.class);
        display.setBlock(material.createBlockData());
        
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
        
        // Interpolationの設定（即座に表示してチラつきを防止）
        display.setInterpolationDuration(0);
        display.setInterpolationDelay(0);
        
        return display;
    }
    
    /**
     * プレビューをクリア
     */
    public void clear() {
        for (BlockDisplay display : previewPanelMap.values()) {
            display.remove();
        }
        previewPanelMap.clear();
    }
    
    /**
     * クリーンアップ（ゲーム終了時）
     */
    public void cleanup() {
        clear();
    }
}

