package mods.kpw.runthroughhole.game;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.util.Transformation;
import org.joml.Vector2i;
import org.joml.Vector3f;

import mods.kpw.runthroughhole.Main;

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
    private Player player;
    private HoleState holeState; // 穴通過状態管理

    // 位置をキーとしてパネルを管理（差分更新用）
    private Map<String, BlockDisplay> previewPanelMap;

    // 穴なぞり管理（状態管理のみ）
    private HoleTracingManager tracingManager;

    // 前回の通過可否状態（白→緑の変化を検出するため）
    private Boolean lastCanPassThrough = null;

    // 壁を探索する長さ
    private static final int WALL_SEARCH_LENGTH = 100;

    public HolePreview(World world, Player player) {
        this.world = world;
        this.player = player;
        this.holeState = new HoleState();
        this.previewPanelMap = new HashMap<>();
        this.tracingManager = new HoleTracingManager();
    }

    /**
     * プレビューを更新
     * 
     * @param cube プレイヤーのキューブ
     */
    public void update(PlayerCube cube) {
        // キューブの現在位置を取得
        Location currentLocation = cube.getCurrentLocation();
        // 前方の壁を探索
        double currentZ = currentLocation.getZ();

        // キューブの前方1ブロック先から探索（キューブが壁に入るまで検出できるように）
        Location wallLocation = cube.findNextWall(currentZ + 1, currentZ + WALL_SEARCH_LENGTH);

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
            // 穴に入った瞬間
            if (holeState.hasHoleStateChanged()) {
                // 音を鳴らす
                GameSound.HOLE_ENTER.play(player);
                // 穴の位置にパーティクルエフェクトを表示
                cube.getCubeWallPositions(wallLocation).forEach(worldPos ->
                    world.spawnParticle(Particle.END_ROD, worldPos.toCenterLocation(), 2, 0, 0, 0, 0.05));
                // プレビューを消す
                clear();
            }
            // 通過中はプレビュー処理を停止
            return;
        }

        // まず、キューブの全ブロックが壁を通れるかチェック
        boolean canPassThrough = cube.getCubeWallPositions(wallLocation)
                .allMatch(worldPos -> {
                    Material material = world.getBlockAt(worldPos).getType();
                    return PlayerCube.isAir(material);
                });
        // 現在の状態を記録
        lastCanPassThrough = canPassThrough;

        // パネルの色を決定（通れるなら緑、通れないなら白）
        Material panelMaterial = canPassThrough ? Material.LIME_STAINED_GLASS : Material.WHITE_STAINED_GLASS;

        // 壁が変わった場合はクリア
        if (!tracingManager.isCurrentWall(wallZ)) {
            tracingManager.setCurrentWall(wallZ);
        }

        // 壁の穴は固定位置なので、キューブの回転に関係なく、壁の5x5範囲をチェック
        // この壁の穴位置を記録
        Set<Vector2i> holes = cube.getWallBlocks(wallLocation)
                .filter(checkLoc -> PlayerCube.isAir(world.getBlockAt(checkLoc).getType()))
                .map(checkLoc -> new Vector2i(checkLoc.getBlockX(), checkLoc.getBlockY()))
                .collect(Collectors.toSet());
        tracingManager.addHoles(holes);

        // なぞり判定（HolePreviewが行う）
        // ★重要：緑（通れる）の時だけなぞり判定を行う
        if (canPassThrough && !tracingManager.isCompleted()) {
            Set<Vector2i> allHoles = tracingManager.getAllHoles();

            // キューブの投影位置を取得（壁上の2次元座標）
            Set<Vector2i> cubePositions = cube.getCubeWallPositions(wallLocation)
                    .map(worldPos -> new Vector2i(worldPos.getBlockX(), worldPos.getBlockY()))
                    .collect(Collectors.toSet());
            tracingManager.markHoleTraced(cubePositions);

            // 完了判定もHolePreviewが行う
            if (tracingManager.isCompleted()) {
                // 完了時のエフェクトを表示
                int cubeZ = cube.getCurrentLocation().getBlockZ();
                allHoles.forEach(hole ->
                    world.spawnParticle(Particle.HAPPY_VILLAGER, new Location(world, hole.x, hole.y, cubeZ + 1).toCenterLocation(), 1, 0, 0, 0, 0));

                // 完了音を鳴らす
                GameSound.HOLE_COMPLETE.play(player);
            }
        }

        // 現在必要なパネル位置のセットを作成
        Set<String> currentPositions = new HashSet<>();

        // キューブの全ブロック位置にプレビューパネルを表示
        cube.getCubeWallPositions(wallLocation)
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
     * 
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
     * プレビューをクリア
     */
    public void clear() {
        for (BlockDisplay display : previewPanelMap.values()) {
            display.remove();
        }
        previewPanelMap.clear();

        // 通過可否状態をクリア
        lastCanPassThrough = null;
    }

    /**
     * プレビューパネルが緑（通れる状態）かどうかを取得
     * 
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
