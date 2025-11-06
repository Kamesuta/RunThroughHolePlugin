package mods.kpw.runthroughhole.game;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Vector2i;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * プレイヤーキューブの手前になぞったブロックを表示するクラス
 * PlayerCubeの蜂に乗せて、後ろにオフセットして表示
 */
public class CubePreview {
    private World world;
    private PlayerCube cube;
    private Location baseLocation;
    private JavaPlugin plugin;

    // BlockDisplayを管理（Vector2i位置をキーとする）
    private Map<Vector2i, BlockDisplay> displayMap;

    // キューブからの距離（手前に表示する距離、ブロック単位）
    private static final float PREVIEW_Z_OFFSET = -3.0f;

    // 穴通過状態管理
    private HoleState holeState;

    // エンティティの高さオフセット（PlayerCubeから取得）
    private double entityHeightOffset = 0.0;

    // BlockDisplayのTransformation用オフセット
    private static final double BLOCKDISPLAY_HEIGHT_OFFSET = 0;

    // 完了状態管理
    private boolean isCompleted = false;
    private BukkitTask shrinkTask = null; // 縮小アニメーション開始タスク
    private BukkitTask clearTask = null; // クリア実行タスク

    public CubePreview(World world, PlayerCube cube, Location baseLocation, JavaPlugin plugin, HolePreview holePreview) {
        this.world = world;
        this.cube = cube;
        this.baseLocation = baseLocation;
        this.plugin = plugin;
        this.displayMap = new HashMap<>();
        // HolePreviewと同じHoleStateを共有
        this.holeState = holePreview.getHoleState();

        // PlayerCubeの蜂から高さオフセットを取得
        if (cube.getEntity() != null) {
            this.entityHeightOffset = cube.getEntity().getHeight();
        }
    }

    /**
     * プレビューを更新
     *
     * @param tracingManager なぞり管理オブジェクト
     */
    public void update(HoleTracingManager tracingManager) {
        // 穴に入った場合はプレビューを消す
        // ※HoleStateはHolePreviewと共有しており、HolePreviewが更新する
        if (holeState.isInHole()) {
            // プレビューをクリア
            clear();
            // 完了状態をリセット
            isCompleted = false;
            // 通過中はプレビュー処理を停止
            return;
        }

        // 完了アニメーション中は更新処理をスキップ
        if (isCompleted) {
            return;
        }

        // tracedHolesを取得
        Set<Vector2i> tracedHoles = tracingManager.getTracedHoles();

        // 新しく追加されたブロックにBlockDisplayを作成
        for (Vector2i pos : tracedHoles) {
            if (!displayMap.containsKey(pos)) {
                BlockDisplay display = createPreviewPanel(pos, Material.LIGHT_BLUE_STAINED_GLASS);
                displayMap.put(pos, display);
                // PlayerCubeの蜂にマウント
                if (cube.getEntity() != null) {
                    cube.getEntity().addPassenger(display);
                }
            }
        }

        // 完了状態をチェック（通常の更新処理が終わった後に実行）
        boolean completed = tracingManager.isCompleted();
        if (completed && !isCompleted) {
            // 完了した瞬間
            isCompleted = true;
            // すべてのブロックを緑に変更
            changeAllBlocksColor(Material.LIME_STAINED_GLASS);
            // 0.5秒後に消去するタスクをスケジュール
            scheduleClear();
        }
    }

    /**
     * すべてのブロックの色を変更
     *
     * @param material 変更後のマテリアル
     */
    private void changeAllBlocksColor(Material material) {
        for (BlockDisplay display : displayMap.values()) {
            display.setBlock(material.createBlockData());
        }
    }

    /**
     * 0.5秒待ってから0.5秒かけてサイズを0にしてクリアするタスクをスケジュール
     */
    private void scheduleClear() {
        // 既存のタスクがあればキャンセル
        if (shrinkTask != null && !shrinkTask.isCancelled()) {
            shrinkTask.cancel();
        }
        if (clearTask != null && !clearTask.isCancelled()) {
            clearTask.cancel();
        }

        // 1秒後にInterpolationを開始
        shrinkTask = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            // すべてのBlockDisplayにInterpolationを設定してサイズを0にする
            for (Map.Entry<Vector2i, BlockDisplay> entry : displayMap.entrySet()) {
                Vector2i pos = entry.getKey();
                BlockDisplay display = entry.getValue();

                // スケール0のTransformationを作成（中心位置を維持）
                Transformation transformation = createPanelTransformation(pos, 0f, 0f);
                display.setTransformation(transformation);

                // 0.5秒（10tick）かけてスケールを0にする
                display.setInterpolationDuration(10);
                display.setInterpolationDelay(0);
            }
        }, 20L);

        // 1.5秒後にクリア
        clearTask = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            clear();
        }, 30L);
    }

    /**
     * プレビューパネル用のTransformationを作成
     *
     * @param pos 2次元位置（X, Y）
     * @param xyDiameter XY方向の直径（スケール）
     * @param zThickness Z方向の厚み（スケール）
     * @return 作成されたTransformation
     */
    private Transformation createPanelTransformation(Vector2i pos, float xyDiameter, float zThickness) {
        Transformation transformation = new Transformation(
            new Vector3f(0, 0, 0),
            new org.joml.Quaternionf(),
            new Vector3f(1, 1, 1),
            new org.joml.Quaternionf()
        );

        // スケールを設定（薄型パネル、Z方向が薄い）
        transformation.getScale().set(xyDiameter, xyDiameter, zThickness);

        // 位置を調整
        // ブロックの中心は整数座標 + 0.5 なので、0.5を加える
        // スケールに応じてオフセットを調整（中心を維持）
        float relativeX = pos.x + 0.5f - (float) baseLocation.getX();
        float relativeY = pos.y + 0.5f - (float) baseLocation.getY() + (float) (BLOCKDISPLAY_HEIGHT_OFFSET - entityHeightOffset);

        // スケールの半分だけオフセット（中心を揃えるため）
        Vector3f translation = new Vector3f(
                relativeX - xyDiameter / 2,
                relativeY - xyDiameter / 2,
                PREVIEW_Z_OFFSET - zThickness / 2);
        transformation.getTranslation().set(translation);

        return transformation;
    }

    /**
     * プレビューパネルを作成
     *
     * @param pos 2次元位置（X, Y）
     * @param material ブロックのマテリアル
     * @return 作成されたBlockDisplay
     */
    private BlockDisplay createPreviewPanel(Vector2i pos, Material material) {
        // BlockDisplayをスポーン
        BlockDisplay display = world.spawn(baseLocation, BlockDisplay.class);
        display.setBlock(material.createBlockData());
        display.setBrightness(new BlockDisplay.Brightness(15, 15));

        // Transformationを設定（直径0.4、厚み0.1）
        Transformation transformation = createPanelTransformation(pos, 0.4f, 0.1f);
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
        for (BlockDisplay display : displayMap.values()) {
            display.remove();
        }
        displayMap.clear();

        // すべてのタスクをキャンセル
        if (shrinkTask != null && !shrinkTask.isCancelled()) {
            shrinkTask.cancel();
            shrinkTask = null;
        }
        if (clearTask != null && !clearTask.isCancelled()) {
            clearTask.cancel();
            clearTask = null;
        }
    }

    /**
     * クリーンアップ（ゲーム終了時）
     */
    public void cleanup() {
        clear();
    }
}
