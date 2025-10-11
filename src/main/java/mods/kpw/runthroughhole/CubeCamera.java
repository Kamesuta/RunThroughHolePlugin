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
    
    private static final double CAMERA_DISTANCE_BEHIND = 10.0; // キューブから後ろに離れる距離
    private static final double CAMERA_HEIGHT_OFFSET = 3.0; // 通常時のカメラの高さオフセット
    private static final double LERP_FACTOR = 0.1; // カメラのスムーズ移動速度
    private static final double CAMERA_RETURN_MARGIN = 1.0; // カメラを戻すまでの余裕距離（ブロック単位）
    
    // カメラ位置の微調整用定数（正の値で上、負の値で下）
    private static final double CAMERA_HEIGHT_ADJUSTMENT = -1.0; // カメラの高さ微調整（ブロック単位）
    
    // カメラエンティティの高さオフセット（プレイヤーが乗る位置を考慮）
    private double entityHeightOffset = 0.0;
    
    private final World world;
    private final Location initialLocation; // ゲーム開始時の初期位置（不変）
    private final PlayerCube cube; // キューブへの参照
    
    private Entity entity; // カメラ用のエンティティ（基底クラス、将来的に変更可能）
    private Player player; // プレイヤー
    
    // カメラの状態
    private double cameraTargetX; // カメラの目標X位置（相対座標）
    private double cameraTargetY; // カメラの目標Y位置（相対座標）
    private double cameraCurrentX; // カメラの現在X位置（相対座標）
    private double cameraCurrentY; // カメラの現在Y位置（相対座標）
    
    // 穴通過状態
    private boolean isInHole; // 現在穴の中にいるかどうか
    private Location lastHoleLocation; // 最後に検出した穴の位置
    
    /**
     * コンストラクタ
     * @param world ワールド
     * @param initialLocation 初期位置
     * @param cube プレイヤーキューブ
     */
    public CubeCamera(World world, Location initialLocation, PlayerCube cube) {
        this.world = world;
        this.initialLocation = initialLocation.clone();
        this.cube = cube;
        
        // 初期カメラ位置（通常時の位置）
        this.cameraTargetX = 0.0;
        this.cameraTargetY = CAMERA_HEIGHT_OFFSET;
        this.cameraCurrentX = 0.0;
        this.cameraCurrentY = CAMERA_HEIGHT_OFFSET;
        
        this.isInHole = false;
        this.lastHoleLocation = null;
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
        
        // キューブの位置を取得
        var cubeGridPos = cube.gridPosition;
        float cubeForwardProgress = cube.getForwardProgress();
        
        // キューブのZ位置から10マス後ろ（固定）
        double cameraZ = cubeGridPos.z + cubeForwardProgress - CAMERA_DISTANCE_BEHIND;
        
        // カメラの絶対Z座標を計算
        double cameraAbsoluteZ = initialLocation.getZ() + cameraZ;
        
        // 穴を検出
        Location holeLocation = cube.detectHole();
        
        updateCameraTarget(holeLocation, cubeGridPos, cameraAbsoluteZ);
        
        // 現在位置を目標位置に向けてスムーズに補間（lerp）
        cameraCurrentX += (cameraTargetX - cameraCurrentX) * LERP_FACTOR;
        cameraCurrentY += (cameraTargetY - cameraCurrentY) * LERP_FACTOR;
        
        // 新しいカメラ位置を計算
        // プレイヤーの視点がcameraCurrentYになるように、エンティティの高さ分を引く
        // さらに微調整用の定数を適用
        Location newCameraLoc = initialLocation.clone();
        newCameraLoc.add(cameraCurrentX, cameraCurrentY - entityHeightOffset + CAMERA_HEIGHT_ADJUSTMENT, cameraZ);
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
    private void updateCameraTarget(Location holeLocation, org.joml.Vector3f cubeGridPos, double cameraAbsoluteZ) {
        if (holeLocation != null) {
            // 穴が見つかった場合
            if (!isInHole) {
                // 初めて穴を検出した→目標位置をキューブの中心位置に設定
                isInHole = true;
                cameraTargetX = holeLocation.getX() - initialLocation.getX();
                cameraTargetY = holeLocation.getY() - initialLocation.getY();
            }
            // 穴を検出し続けている間、lastHoleLocationを更新し続ける（長いトンネル対応）
            lastHoleLocation = holeLocation.clone();
            // 穴にフォーカス中は目標位置を更新しない（カメラ固定）
        } else {
            // 穴が見つからない場合
            if (isInHole) {
                // カメラが穴のZ座標+余裕マージンを超えたかチェック
                if (lastHoleLocation != null && cameraAbsoluteZ > lastHoleLocation.getZ() + CAMERA_RETURN_MARGIN) {
                    // カメラが穴を十分通過した→穴モードを解除
                    isInHole = false;
                    lastHoleLocation = null;
                }
                // まだカメラが穴を通過していない場合は、目標位置を変更しない（カメラ固定継続）
            }
            
            if (!isInHole) {
                // 通常時：キューブの位置+2マス上を目標にする
                cameraTargetX = cubeGridPos.x;
                cameraTargetY = cubeGridPos.y + CAMERA_HEIGHT_OFFSET;
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

