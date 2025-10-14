package mods.kpw.runthroughhole;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.util.Transformation;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 前方の壁の穴に対してプレビュー表示を行うクラス
 */
public class HolePreview {
    private World world;
    private HoleState holeState; // 穴通過状態管理
    
    // 位置をキーとしてパネルを管理（差分更新用）
    private Map<String, BlockDisplay> previewPanelMap;
    
    // 壁ごとの穴追跡（壁のZ座標をキー）
    private Map<Integer, Set<String>> wallHoles; // 壁の全穴位置
    private Map<Integer, Set<String>> tracedHoles; // なぞった穴位置
    private Set<Integer> completedWalls; // 完了した壁のZ座標
    
    
    // 前回の通過可否状態（白→緑の変化を検出するため）
    private Boolean lastCanPassThrough = null;
    
    
    // 完了した壁のZ座標（カメラと同じように、マージンを超えるまで保持）
    private static final double WALL_PASS_MARGIN = 1.0; // 壁通過判定のマージン

    // 壁を探索する長さ
    private static final int WALL_SEARCH_LENGTH = 100;
    
    
    public HolePreview(World world, Main plugin) {
        this.world = world;
        this.holeState = new HoleState();
        this.previewPanelMap = new HashMap<>();
        this.wallHoles = new HashMap<>();
        this.tracedHoles = new HashMap<>();
        this.completedWalls = new HashSet<>();
    }
    
    /**
     * プレビューを更新
     * @param cube プレイヤーのキューブ
     * @param baseLocation 基準位置
     * @param player プレイヤー（エフェクト用）
     */
    public void update(PlayerCube cube, Location baseLocation, Player player) {
        // キューブの現在位置を取得
        Location currentLocation = cube.getCurrentLocation();
        // 前方の壁を探索
        double currentZ = currentLocation.getZ();
        
        // キューブの前方1ブロック先から探索（キューブが壁に入るまで検出できるように）
        Location wallLocation = cube.findNextWall(baseLocation, currentZ + 1, currentZ + WALL_SEARCH_LENGTH);
        
        if (wallLocation == null) {
            // 壁が見つからなかった場合 → すべてのデータをクリア
            clear();
            return;
        }
        
        // 壁のZ座標
        int wallZ = wallLocation.getBlockZ();
        
        // 穴を検出
        Location holeLocation = cube.detectHole();
        
        // 穴通過状態を更新（キューブのZ座標を使用）
        holeState.updateHoleStatus(holeLocation, currentLocation.getZ());
        
        // 通過中かチェック
        if (holeState.isInHole()) {
            // 通過中はプレビュー処理を停止（パネルは表示したまま）
            return;
        }
        
        // 完了済みの壁を通過したかチェック（カメラと同じロジック）
        Set<Integer> wallsToCleanup = new HashSet<>();
        for (Integer completedWallZ : completedWalls) {
            if (currentLocation.getZ() > completedWallZ + WALL_PASS_MARGIN) {
                // 完了した壁をマージン分超えて通過した → データをクリア
                wallsToCleanup.add(completedWallZ);
            }
        }
        for (Integer wallZToCleanup : wallsToCleanup) {
            completedWalls.remove(wallZToCleanup);
            wallHoles.remove(wallZToCleanup);
            tracedHoles.remove(wallZToCleanup);
        }
        
        // まず、キューブの全ブロックが壁を通れるかチェック
        boolean canPassThrough = cube.getCubeWorldPositions(wallLocation)
            .allMatch(worldPos -> {
                Material material = world.getBlockAt(worldPos).getType();
                return PlayerCube.isAir(material);
            });
        
        // パネルの色を決定（通れるなら緑、通れないなら白）
        Material panelMaterial = canPassThrough ? Material.LIME_STAINED_GLASS : Material.WHITE_STAINED_GLASS;
        
        // 穴から出たことを検出
        if (holeState.hasHoleStateChanged() && !holeState.isInHole()) {
            // 穴の中にいた → 穴から出た
            // この壁のなぞり状態をリセット（再挑戦可能にする）
            if (tracedHoles.containsKey(wallZ)) {
                tracedHoles.get(wallZ).clear();
            }
            // 完了済みの壁も再挑戦できるようにする
            if (completedWalls.contains(wallZ)) {
                completedWalls.remove(wallZ);
            }
        }
        
        // 白→緑の変化を検出（通れない→通れるに変わった）
        if (lastCanPassThrough != null && !lastCanPassThrough && canPassThrough) {
            // 白から緑に変わった → なぞり直しなので tracedHoles をクリア
            if (tracedHoles.containsKey(wallZ)) {
                tracedHoles.get(wallZ).clear();
            }
            // 完了済みの壁も再挑戦できるようにする
            if (completedWalls.contains(wallZ)) {
                completedWalls.remove(wallZ);
            }
        }
        
        // 現在の状態を記録
        lastCanPassThrough = canPassThrough;
        
        // この壁の穴位置を記録（初回または完了後の再挑戦）
        // 壁の穴は固定位置なので、キューブの回転に関係なく、壁の5x5範囲をチェック
        if (!wallHoles.containsKey(wallZ)) {
            Location wallCenter = wallLocation.clone();
            Set<String> holes = cube.getWallBlocks(wallCenter)
                .filter(checkLoc -> PlayerCube.isAir(world.getBlockAt(checkLoc).getType()))
                .map(checkLoc -> checkLoc.getBlockX() + "," + checkLoc.getBlockY() + "," + checkLoc.getBlockZ())
                .collect(Collectors.toSet());
            
            if (!holes.isEmpty()) {
                wallHoles.put(wallZ, holes);
                tracedHoles.put(wallZ, new HashSet<>());
            }
        }
        
        // 現在プレビュー表示されている穴をなぞったとして記録
        // ★重要：緑（通れる）の時だけなぞり判定を行う
        if (canPassThrough && wallHoles.containsKey(wallZ) && !completedWalls.contains(wallZ)) {
            Set<String> currentlyTracedHoles = tracedHoles.get(wallZ);
            cube.getCubeWorldPositions(wallLocation)
                .filter(worldPos -> PlayerCube.isAir(world.getBlockAt(worldPos).getType()))
                .forEach(worldPos -> {
                    String holeKey = worldPos.getBlockX() + "," + worldPos.getBlockY() + "," + worldPos.getBlockZ();
                    
                    // 初めてなぞった場合のみパーティクルを表示
                    if (!currentlyTracedHoles.contains(holeKey)) {
                        currentlyTracedHoles.add(holeKey);
                        showTraceEffect(worldPos);
                    }
                });
            
            // すべての穴をなぞったかチェック
            Set<String> allHoles = wallHoles.get(wallZ);
            if (currentlyTracedHoles.size() >= allHoles.size() && currentlyTracedHoles.containsAll(allHoles)) {
                // すべての穴をなぞった！まだ完了していなければエフェクトを出す
                if (!completedWalls.contains(wallZ)) {
                    completedWalls.add(wallZ);
                    
                    // 完了音を鳴らす
                    world.playSound(cube.getCurrentLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 2.0f);
                }
                
                // データは残しておく（壁通過まで保持）
            }
        }
        
        // 現在必要なパネル位置のセットを作成
        Set<String> currentPositions = new HashSet<>();
        
        // キューブの全ブロック位置にプレビューパネルを表示
        cube.getCubeWorldPositions(wallLocation)
            .forEach(worldPos -> {
                String posKey = worldPos.getBlockX() + "," + worldPos.getBlockY() + "," + worldPos.getBlockZ();
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
                    BlockDisplay display = createPreviewPanel(worldPos, panelMaterial);
                    previewPanelMap.put(posKey, display);
                }
            });
        
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
     * プレビューパネルを作成
     * @param location パネルの位置
     * @param material パネルのマテリアル
     * @return 作成されたBlockDisplay
     */
    private BlockDisplay createPreviewPanel(Location location, Material material) {
        // BlockDisplayをスポーン（ブロックの中心）
        Location spawnLoc = location.toCenterLocation().add(0, 0, -1);
        BlockDisplay display = world.spawn(spawnLoc, BlockDisplay.class);
        display.setBlock(material.createBlockData());
        display.setBrightness(new BlockDisplay.Brightness(15, 15));
        
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
     * 穴をなぞった瞬間のエフェクトを表示
     * @param location 穴の位置
     */
    private void showTraceEffect(Location location) {
        // パーティクルエフェクト（キラキラ）
        Location particleLoc = location.toCenterLocation();
        world.spawnParticle(Particle.HAPPY_VILLAGER, particleLoc, 3, 0.2, 0.2, 0.2, 0);
        world.spawnParticle(Particle.END_ROD, particleLoc, 2, 0.15, 0.15, 0.15, 0.05);
        
        // パリンという音
        world.playSound(particleLoc, Sound.BLOCK_GLASS_BREAK, 0.3f, 1.8f);
    }
    
    /**
     * プレビューをクリア
     */
    public void clear() {
        for (BlockDisplay display : previewPanelMap.values()) {
            display.remove();
        }
        previewPanelMap.clear();
        
        // 壁の追跡データもクリア
        wallHoles.clear();
        tracedHoles.clear();
        completedWalls.clear();
        lastCanPassThrough = null;
    }
    
    /**
     * プレビューパネルが緑（通れる状態）かどうかを取得
     * @return 通れる場合はtrue、通れない場合はfalse、まだ判定されていない場合はnull
     */
    public Boolean isPreviewGreen() {
        return lastCanPassThrough;
    }

    /**
     * クリーンアップ（ゲーム終了時）
     */
    public void cleanup() {
        clear();
    }
}

