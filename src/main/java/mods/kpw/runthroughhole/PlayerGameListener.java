package mods.kpw.runthroughhole;

import org.bukkit.Location;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerGameListener implements Listener {

    private final Main plugin;
    private final float VIEW_THRESHOLD = 0.0f; // 視点ずれの許容範囲
    private final Map<UUID, Quaternionf> currentDisplayRotationMap = new HashMap<>();
    
    public PlayerGameListener(Main plugin) {
        this.plugin = plugin;
    }

    public Quaternionf getOrInitRotation(UUID playerId) {
        return currentDisplayRotationMap.computeIfAbsent(playerId, k -> {
            // 初期視点に合わせてBlockDisplayの回転も初期化 (X+方向: Yaw -90, Pitch 0)
            // MinecraftのYawとJOMLのY軸回転の方向を合わせるため+90度
            return new Quaternionf().rotateY((float) Math.toRadians(90.0f));
        });
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        if (!plugin.getPlayerDisplays().containsKey(playerId)) {
            return; // ゲーム中でないプレイヤーは無視
        }

        BlockDisplay display = plugin.getPlayerDisplays().get(playerId);

        Location currentLocation = player.getLocation();
        float currentYaw = currentLocation.getYaw();
        float currentPitch = currentLocation.getPitch();

        // 目標視点 (X+方向: Yaw -90, Pitch 0)
        float targetPlayerYaw = -90.0f;
        float targetPlayerPitch = 0.0f;

        // 視点ずれを計算
        float yawDiff = normalizeAngle(currentYaw - targetPlayerYaw);
        float pitchDiff = normalizeAngle(currentPitch - targetPlayerPitch);

        boolean rotated = false;

        Quaternionf newRotation = new Quaternionf();

        // YawとPitchのずれが両方とも1度以上の場合、より大きい方を優先
        if (Math.abs(yawDiff) > VIEW_THRESHOLD || Math.abs(pitchDiff) > VIEW_THRESHOLD) {
            Main.logger.info("視点ずれ検出: Yaw差=" + yawDiff + ", Pitch差=" + pitchDiff);
            // Yawのずれが1度以上の場合
            if (Math.abs(yawDiff) > Math.abs(pitchDiff)) {
                // プレイヤーの視点移動の方向に応じて、BlockDisplayの目標回転角度を90度単位で計算
                // 進行方向を基準としたY軸回転（ヨー）
                if (yawDiff > 0) { // 右にずれた
                    newRotation.rotateAxis((float) Math.toRadians(90.0f), 0, -1, 0);
                    player.sendMessage("右に回転");
                } else { // 左にずれた
                    newRotation.rotateAxis((float) Math.toRadians(-90.0f), 0, -1, 0);
                    player.sendMessage("左に回転");
                }
            } else {
                // Pitchのずれが1度以上の場合
                if (Math.abs(pitchDiff) > VIEW_THRESHOLD) {
                    // プレイヤーの視点移動の方向に応じて、BlockDisplayの目標回転角度を90度単位で計算
                    // 進行方向を基準としたX軸回転（ピッチ）
                    if (pitchDiff > 0) { // 上にずれた
                        newRotation.rotateAxis((float) Math.toRadians(90.0f), 1, 0, 0);
                        player.sendMessage("上に回転");
                    } else { // 下にずれた
                        newRotation.rotateAxis((float) Math.toRadians(-90.0f), 1, 0, 0);
                        player.sendMessage("下に回転");
                    }
                }
            }
            rotated = true;
        }

        if (rotated) {
            // 現在のBlockDisplayの回転に新しい回転を適用
            Quaternionf rotation = new Quaternionf()
                .mul(newRotation)
                .mul(getOrInitRotation(playerId));
            currentDisplayRotationMap.put(playerId, rotation);

            // BlockDisplayのTransformationを更新して回転を同期
            Transformation transformation = display.getTransformation();

            // アニメーション設定
            display.setInterpolationDuration(5); // 5ティックでアニメーション
            display.setInterpolationDelay(0); // 遅延なし
            
            // BlockDisplayの中心オフセット
            Vector3f offset = new Vector3f(-0.5f, -0.5f, -0.5f);
            offset.rotate(rotation); // 回転を考慮してオフセットを回転
            
            // Transformationに設定
            transformation.getLeftRotation().set(rotation);
            transformation.getTranslation().set(offset);

            display.setTransformation(transformation);
            
            // プレイヤーの視点を元のX+方向に戻す
            Location newLoc = currentLocation.clone();
            newLoc.setYaw(targetPlayerYaw);
            newLoc.setPitch(targetPlayerPitch);
            player.teleport(newLoc);
        }

        // BlockDisplayの位置をプレイヤーの頭上に同期
        display.teleport(player.getLocation().add(0, 2, 0));
    }

    private float normalizeAngle(float angle) {
        angle = angle % 360;
        if (angle > 180) angle -= 360;
        if (angle < -180) angle += 360;
        return angle;
    }
}
