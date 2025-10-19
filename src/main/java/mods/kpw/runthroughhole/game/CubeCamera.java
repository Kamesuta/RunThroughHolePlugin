package mods.kpw.runthroughhole.game;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import io.papermc.paper.entity.TeleportFlag;
import org.joml.Vector2f;

/**
 * プレイヤーのカメラを管理するクラス
 * Entityを使用してカメラ位置を制御し、
 * スムーズな移動と穴通過時の特殊な動作を実装
 */
public class CubeCamera {

    public static final double CAMERA_DISTANCE_BEHIND = 10.0; // キューブから後ろに離れる距離
    private static final double CAMERA_HEIGHT_OFFSET = 3.0; // 通常時のカメラの高さオフセット
    private static final double CUBE_TARGET_LERP_FACTOR = 0.02; // カメラのスムーズ移動速度
    private static final double SWITCH_TARGET_LERP_FACTOR = 0.2; // カメラターゲットのlerp速度（holeとcubeTargetの切り替え用）
    private static final double CEILING_COLLISION_LERP_FACTOR = 0.3; // 天井衝突時の高速下降lerp速度

    // カメラ位置の微調整用定数（正の値で上、負の値で下）
    private static final double CAMERA_HEIGHT_ADJUSTMENT = -1; // カメラの高さ微調整（ブロック単位）

    // カメラエンティティの高さオフセット（プレイヤーが乗る位置を考慮）
    private double entityHeightOffset = 0.0;

    private final World world;
    private final Location initialLocation; // ゲーム開始時の初期位置（不変）
    private final PlayerCube cube; // キューブへの参照
    private final HoleState holeState; // 穴通過状態管理

    private Entity entity; // カメラ用のエンティティ（基底クラス、将来的に変更可能）
    private Player player; // プレイヤー

    // キューブの現在位置
    private Location cubeLocation;
    // カメラの状態
    private Vector2f cameraTarget; // カメラの現在位置（相対座標）
    // キューブフォーカス用
    private Vector2f cubeTarget; // カメラの目標位置（相対座標）
    // キューブ 穴 切り替え用
    private float switchTargetLerpFactor; // カメラターゲットのlerpファクター（0.0-1.0）

    /**
     * コンストラクタ
     * 
     * @param world           ワールド
     * @param initialLocation 初期位置
     * @param cube            プレイヤーキューブ
     */
    public CubeCamera(World world, Location initialLocation, PlayerCube cube) {
        this.world = world;
        this.initialLocation = initialLocation.clone().add(0, 0, -CubeCamera.CAMERA_DISTANCE_BEHIND);
        this.cube = cube;
        this.holeState = new HoleState();

        // 初期カメラ位置（通常時の位置）
        this.cubeTarget = new Vector2f(0.0f, (float) CAMERA_HEIGHT_OFFSET);
        this.cameraTarget = new Vector2f(0.0f, (float) CAMERA_HEIGHT_OFFSET);
    }

    /**
     * カメラをセットアップ（エンティティのスポーンとプレイヤーの搭乗）
     * 
     * @param player プレイヤー
     */
    public void setup(Player player) {
        this.player = player;

        // 透明で動かない椅子をスポーン
        LivingEntity entity = (LivingEntity) world.spawnEntity(initialLocation, EntityType.BEE);
        entity.setInvulnerable(true);
        entity.setGravity(false);
        entity.setSilent(true);
        entity.setAI(false); // AIを無効化して完全に動かなくする
        entity.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0, false, false));
        this.entity = entity;

        // エンティティの高さを取得（プレイヤーが乗る位置のオフセット）
        this.entityHeightOffset = entity.getHeight();

        // プレイヤーを椅子に乗せる
        entity.addPassenger(player);
    }

    /**
     * カメラ位置を更新（毎tick呼び出される）
     */
    public void update() {
        if (entity == null || cube == null)
            return;

        // キューブの現在位置を取得
        cubeLocation = cube.getCurrentLocation();
        // カメラの絶対Z座標を計算
        double cameraAbsoluteZ = cubeLocation.getZ() - CAMERA_DISTANCE_BEHIND;

        // 穴を検出
        Location holeLocation = cube.detectHole();
        // 穴通過状態を更新（カメラのZ座標を使用）
        holeState.updateHoleStatus(holeLocation, cameraAbsoluteZ);

        // カメラの目標位置を更新
        updateCameraTarget();

        // 新しいカメラ位置を計算
        // プレイヤーの視点がcameraCurrent.yになるように、エンティティの高さ分を引く
        // さらに微調整用の定数を適用
        Location newCameraLoc = initialLocation.clone();
        newCameraLoc.add(
                cameraTarget.x,
                cameraTarget.y + (CAMERA_HEIGHT_ADJUSTMENT - entityHeightOffset),
                cameraAbsoluteZ - initialLocation.getZ());
        newCameraLoc.setYaw(0f);
        newCameraLoc.setPitch(0f);

        // 椅子をテレポート
        if (player != null && entity.getPassengers().contains(player)) {
            entity.teleport(newCameraLoc, TeleportFlag.EntityState.RETAIN_PASSENGERS);
        } else {
            entity.teleport(newCameraLoc);
        }
    }

    /**
     * カメラの目標位置を更新
     */
    private void updateCameraTarget() {
        // 穴の位置を取得
        Location holeLocation = holeState.getLastHoleLocation();
        Vector2f holeTarget = holeLocation != null
                ? new Vector2f((float) (holeLocation.getX() - initialLocation.getX()),
                        (float) (holeLocation.getY() - initialLocation.getY()))
                : new Vector2f();

        // キューブの位置を取得（天井制限を考慮）
        double desiredCameraY = cubeLocation.getY() + CAMERA_HEIGHT_OFFSET;
        double ceilingY = getCeilingY();

        // 天井衝突状態を更新
        boolean isCeilingCollision = (ceilingY < desiredCameraY);
        // 天井衝突時の高速下降処理
        double lerpFactor = CUBE_TARGET_LERP_FACTOR;
        if (isCeilingCollision) {
            // 天井衝突時は高速下降
            lerpFactor = CEILING_COLLISION_LERP_FACTOR;
        }

        // キューブの位置を更新
        Vector2f cubePosition = new Vector2f(
                (float) (cubeLocation.getX() - initialLocation.getX()),
                (float) (Math.min(ceilingY, desiredCameraY) - initialLocation.getY()));
        cubeTarget.lerp(cubePosition, (float) lerpFactor);

        // カメラターゲットのlerpファクターを更新
        float targetLerpFactor = holeState.isInHole() ? 1.0f : 0.0f;
        switchTargetLerpFactor += (targetLerpFactor - switchTargetLerpFactor) * SWITCH_TARGET_LERP_FACTOR;

        // cubeフォーカスに完全に切り替え
        // Vector2fを使用して現在位置を目標位置に向けてスムーズに補間（lerp）
        cameraTarget = cameraTarget.set(cubeTarget).lerp(holeTarget, switchTargetLerpFactor);
    }

    /**
     * 天井を検出してカメラの高さを制限する
     * 
     * @return 天井のY座標
     */
    private double getCeilingY() {
        // 現在のY座標から上に向かって天井を検索
        int startY = cubeLocation.getBlockY();
        // 最大検索範囲を設定（カメラの高さオフセット + 余裕分）
        int endY = startY + (int) Math.ceil(CAMERA_HEIGHT_OFFSET);

        for (int y = startY; y <= endY; y++) {
            Location checkLocation = new Location(world, cubeLocation.getX(), y, cubeLocation.getZ());

            // その位置にブロックがあるかチェック
            if (!world.getBlockAt(checkLocation).isEmpty()) {
                // 天井を発見：カメラがその位置より下になるように制限
                return checkLocation.getY() - 1.0;
            }
        }

        // 天井が見つからない場合は最大高さを返す
        return cubeLocation.getY() + CAMERA_HEIGHT_OFFSET;
    }

    /**
     * エンティティを取得
     */
    public Entity getEntity() {
        return entity;
    }

    /**
     * 指定されたエンティティがこのカメラのエンティティかどうかを判定
     * 
     * @param entity 判定するエンティティ
     * @return カメラのエンティティと一致する場合true
     */
    public boolean isEntity(Entity entity) {
        return this.entity != null && this.entity.equals(entity);
    }

    /**
     * プレイヤーを取得
     */
    public Player getPlayer() {
        return player;
    }

    /**
     * プレイヤーをカメラから降ろす
     */
    public void eject() {
        if (entity != null) {
            entity.eject();
        }
    }

    /**
     * クリーンアップ（エンティティの削除）
     */
    public void cleanup() {
        if (entity != null) {
            entity.eject();
            entity.remove();
            entity = null;
        }
        player = null;
    }
}
