package mods.kpw.runthroughhole;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import io.papermc.paper.entity.TeleportFlag;

/**
 * プレイヤーのカメラを管理するクラス
 * Entityを使用してカメラ位置を制御し、
 * スムーズな移動と穴通過時の特殊な動作を実装
 */
public class CubeCamera {
    
    public static final double CAMERA_DISTANCE_BEHIND = 10.0; // キューブから後ろに離れる距離
    private static final double CAMERA_HEIGHT_OFFSET = 3.0; // 通常時のカメラの高さオフセット
    private static final double LERP_FACTOR = 0.2; // カメラのスムーズ移動速度
    
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
    
    // カメラの状態
    private double cameraTargetX; // カメラの目標X位置（相対座標）
    private double cameraTargetY; // カメラの目標Y位置（相対座標）
    private double cameraCurrentX; // カメラの現在X位置（相対座標）
    private double cameraCurrentY; // カメラの現在Y位置（相対座標）
    
    
    /**
     * コンストラクタ
     * @param world ワールド
     * @param initialLocation 初期位置
     * @param cube プレイヤーキューブ
     */
    public CubeCamera(World world, Location initialLocation, PlayerCube cube) {
        this.world = world;
        this.initialLocation = initialLocation.clone().add(0, 0, -CubeCamera.CAMERA_DISTANCE_BEHIND);
        this.cube = cube;
        this.holeState = new HoleState();
        
        // 初期カメラ位置（通常時の位置）
        this.cameraTargetX = 0.0;
        this.cameraTargetY = CAMERA_HEIGHT_OFFSET;
        this.cameraCurrentX = 0.0;
        this.cameraCurrentY = CAMERA_HEIGHT_OFFSET;
    }
    
    /**
     * カメラをセットアップ（エンティティのスポーンとプレイヤーの搭乗）
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
        if (entity == null || cube == null) return;
        
        // キューブの現在位置を取得
        Location cubeLocation = cube.getCurrentLocation();
        
        // カメラの絶対Z座標を計算（キューブのZ位置から10マス後ろ）
        double cameraAbsoluteZ = cubeLocation.getZ() - CAMERA_DISTANCE_BEHIND;
        
        // 穴を検出
        Location holeLocation = cube.detectHole();
        
        // 穴通過状態を更新（カメラのZ座標を使用）
        holeState.updateHoleStatus(holeLocation, cameraAbsoluteZ);
        
        updateCameraTarget(holeLocation, cubeLocation);
        
        // 現在位置を目標位置に向けてスムーズに補間（lerp）
        cameraCurrentX += (cameraTargetX - cameraCurrentX) * LERP_FACTOR;
        cameraCurrentY += (cameraTargetY - cameraCurrentY) * LERP_FACTOR;
        
        // 新しいカメラ位置を計算
        // プレイヤーの視点がcameraCurrentYになるように、エンティティの高さ分を引く
        // さらに微調整用の定数を適用
        Location newCameraLoc = initialLocation.clone();
        newCameraLoc.add(cameraCurrentX, cameraCurrentY + (CAMERA_HEIGHT_ADJUSTMENT - entityHeightOffset), cubeLocation.getZ() - initialLocation.getZ() - CAMERA_DISTANCE_BEHIND);
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
    private void updateCameraTarget(Location holeLocation, Location cubeLocation) {
        if (holeLocation != null) {
            // 穴が見つかった場合
            if (holeState.hasHoleStateChanged() && holeState.isInHole()) {
                // 初めて穴を検出した→目標位置をキューブの中心位置に設定
                cameraTargetX = holeLocation.getX() - initialLocation.getX();
                cameraTargetY = holeLocation.getY() - initialLocation.getY();
            }
            // 穴にフォーカス中は目標位置を更新しない（カメラ固定）
        } else {
            // 穴が見つからない場合
            if (holeState.hasHoleStateChanged() && !holeState.isInHole()) {
                // 穴通過が完了した→通常時の目標位置に戻す
                cameraTargetX = cubeLocation.getX() - initialLocation.getX();
                cameraTargetY = cubeLocation.getY() - initialLocation.getY() + CAMERA_HEIGHT_OFFSET;
            }
        }
    }
    
    /**
     * エンティティを取得
     */
    public Entity getEntity() {
        return entity;
    }
    
    /**
     * 指定されたエンティティがこのカメラのエンティティかどうかを判定
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

