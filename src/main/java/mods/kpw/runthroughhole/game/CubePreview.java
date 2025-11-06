package mods.kpw.runthroughhole.game;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.util.Transformation;
import org.joml.Vector2i;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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

    public CubePreview(World world, PlayerCube cube, Location baseLocation) {
        this.world = world;
        this.cube = cube;
        this.baseLocation = baseLocation;
        this.displayMap = new HashMap<>();
        this.holeState = new HoleState();

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
        // キューブの現在位置を取得
        Location currentLocation = cube.getCurrentLocation();

        // 穴を検出
        Location holeLocation = cube.detectHole();

        // 穴通過状態を更新（キューブのZ座標を使用）
        holeState.updateHoleStatus(holeLocation, currentLocation.getZ());

        // 穴に入った場合はプレビューを消す
        if (holeState.isInHole()) {
            // 穴に入った瞬間
            if (holeState.hasHoleStateChanged()) {
                // プレビューをクリア
                clear();
            }
            // 通過中はプレビュー処理を停止
            return;
        }

        // tracedHolesを取得
        Set<Vector2i> tracedHoles = tracingManager.getTracedHoles();

        // 現在必要な位置のセットを作成
        Set<Vector2i> currentPositions = new HashSet<>(tracedHoles);

        // 新しく追加されたブロックにBlockDisplayを作成
        for (Vector2i pos : currentPositions) {
            if (!displayMap.containsKey(pos)) {
                BlockDisplay display = createPreviewPanel(pos);
                displayMap.put(pos, display);
                // PlayerCubeの蜂にマウント
                if (cube.getEntity() != null) {
                    cube.getEntity().addPassenger(display);
                }
            }
        }

        // 不要になったBlockDisplayを削除
        List<Vector2i> toRemove = new ArrayList<>();
        for (Vector2i pos : displayMap.keySet()) {
            if (!currentPositions.contains(pos)) {
                BlockDisplay display = displayMap.get(pos);
                display.remove();
                toRemove.add(pos);
            }
        }
        for (Vector2i pos : toRemove) {
            displayMap.remove(pos);
        }
    }

    /**
     * プレビューパネルを作成
     *
     * @param pos 2次元位置（X, Y）
     * @return 作成されたBlockDisplay
     */
    private BlockDisplay createPreviewPanel(Vector2i pos) {
        // BlockDisplayをスポーン
        BlockDisplay display = world.spawn(baseLocation, BlockDisplay.class);
        display.setBlock(Material.LIGHT_BLUE_STAINED_GLASS.createBlockData());
        display.setBrightness(new BlockDisplay.Brightness(15, 15));

        // Transformationを設定
        Transformation transformation = display.getTransformation();

        // スケールを設定（薄型パネル、Z方向が薄い）
        Vector3f scale = new Vector3f(0.4f, 0.4f, 0.1f);
        transformation.getScale().set(scale);

        // 位置を調整
        // X, Y: ワールド座標のブロック位置から baseLocation のオフセットを引く
        // Z: PREVIEW_Z_OFFSETで後ろにオフセット
        // ブロックの中心は整数座標 + 0.5 なので、0.5を加える
        float relativeX = pos.x + 0.5f - (float) baseLocation.getX();
        float relativeY = pos.y + 0.5f - (float) baseLocation.getY() + (float) (BLOCKDISPLAY_HEIGHT_OFFSET - entityHeightOffset);

        Vector3f translation = new Vector3f(
                relativeX - 0.2f,
                relativeY - 0.2f,
                PREVIEW_Z_OFFSET - 0.05f); // 後ろにオフセット + ブロックの中心（0.1スケールの中心）
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
        for (BlockDisplay display : displayMap.values()) {
            display.remove();
        }
        displayMap.clear();
    }

    /**
     * クリーンアップ（ゲーム終了時）
     */
    public void cleanup() {
        clear();
    }
}
