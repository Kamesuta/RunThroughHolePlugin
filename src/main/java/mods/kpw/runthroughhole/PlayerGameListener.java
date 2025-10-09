package mods.kpw.runthroughhole;

import org.bukkit.Location;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.util.Transformation;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerGameListener implements Listener {

    private final Main plugin;
    private final float VIEW_THRESHOLD = 1.0f; // 視点ずれの許容範囲
    private final Map<UUID, Quaternionf> currentDisplayRotationMap = new HashMap<>();
    private final Vector3f FIXED_DIRECTION = new Vector3f(1.0f, 0.0f, 0.0f); // 固定進行方向 (X+方向)

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

    public Vector3f getOrInitDirection(UUID playerId) {
        // 現時点では進行方向はX+方向で固定
        return new Vector3f(FIXED_DIRECTION);
    }

    /**
     * 進行方向を基準とした回転を適用する
     * @param rotation 現在の回転
     * @param direction 進行方向
     * @param angle 回転角度（ラジアン）
     * @param axis 回転軸（0: X軸, 1: Y軸, 2: Z軸）
     */
    private void rotateAroundDirection(Quaternionf rotation, Vector3f direction, float angle, int axis) {
        // 進行方向を基準とした回転軸を計算
        Vector3f rotationAxis = new Vector3f();
        switch (axis) {
            case 0: // X軸回転（ピッチ）
                // 進行方向をY軸（0,1,0）を中心に90度回転した軸を計算
                // 進行方向をY軸周りに90度回転
                rotationAxis.set(direction);
                rotationAxis.rotateAxis((float) Math.toRadians(90.0f), 0, 1, 0);
                break;
            case 1: // Y軸回転（ヨー）
                // Y軸（0,1,0）を回転軸とする
                rotationAxis.set(0, 1, 0);
                break;
            case 2: // Z軸回転（ロール）
                // 進行方向そのものを回転軸とする
                rotationAxis.set(direction);
                break;
        }
        
        // 回転軸を正規化
        rotationAxis.normalize();
        
        // 指定された軸を中心に回転を適用
        rotation.rotateAxis(angle, rotationAxis);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        if (plugin.getPlayerDisplays().containsKey(playerId)) {
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

            Quaternionf currentRotation = getOrInitRotation(playerId);
            Quaternionf newRotation = new Quaternionf(currentRotation);

            // 進行方向を取得
            Vector3f direction = getOrInitDirection(playerId);

            // Yawのずれが1度以上の場合
            if (Math.abs(yawDiff) > VIEW_THRESHOLD) {
                // プレイヤーの視点移動の方向に応じて、BlockDisplayの目標回転角度を90度単位で計算
                // 進行方向を基準としたY軸回転（ヨー）
                if (yawDiff > 0) { // 右にずれた
                    rotateAroundDirection(newRotation, direction, (float) Math.toRadians(-90.0f), 1);
                } else { // 左にずれた
                    rotateAroundDirection(newRotation, direction, (float) Math.toRadians(90.0f), 1);
                }
                rotated = true;
            }

            // Pitchのずれが1度以上の場合
            if (Math.abs(pitchDiff) > VIEW_THRESHOLD) {
                // プレイヤーの視点移動の方向に応じて、BlockDisplayの目標回転角度を90度単位で計算
                // 進行方向を基準としたX軸回転（ピッチ）
                if (pitchDiff > 0) { // 上にずれた
                    rotateAroundDirection(newRotation, direction, (float) Math.toRadians(-90.0f), 0);
                } else { // 下にずれた
                    rotateAroundDirection(newRotation, direction, (float) Math.toRadians(90.0f), 0);
                }
                rotated = true;
            }

            if (rotated) {
                currentDisplayRotationMap.put(playerId, newRotation);

                // BlockDisplayのTransformationを更新して回転を同期
                Transformation transformation = display.getTransformation();

                // アニメーション設定
                display.setInterpolationDuration(5); // 5ティックでアニメーション
                display.setInterpolationDelay(0); // 遅延なし

                // Matrix4fを使って正しい座標変換を実装
                Matrix4f matrix = new Matrix4f();
                
                // 1. ブロックの中心に移動
                matrix.translate(0, 0, 0);
                
                // 2. Quaternionfで回転を適用
                matrix.rotate(newRotation);                
                
                // 3. ブロックの中心に戻す
                matrix.translate(-0.5f, -0.5f, -0.5f);

                // Matrix4fからTransformationの各要素に分解
                // 回転部分を抽出（回転行列からクォータニオンに変換）
                Matrix4f rotationMatrix = new Matrix4f();
                rotationMatrix.set(matrix);
                // 平行移動部分を除去
                rotationMatrix.setTranslation(0, 0, 0);
                
                // スケール部分を除去（単位行列に正規化）
                Vector3f scale = new Vector3f();
                rotationMatrix.getScale(scale);
                if (scale.x != 0) rotationMatrix.scale(1.0f / scale.x, 1.0f / scale.y, 1.0f / scale.z);
                
                // 回転行列からクォータニオンに変換
                Quaternionf quaternion = new Quaternionf();
                rotationMatrix.getNormalizedRotation(quaternion);
                
                // 平行移動部分を抽出
                Vector3f translation = new Vector3f();
                matrix.getTranslation(translation);
                
                // Transformationに設定
                transformation.getLeftRotation().set(quaternion);
                transformation.getRightRotation().set(new Quaternionf()); // 右回転は使用しない
                transformation.getTranslation().set(translation);

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
    }

    private float normalizeAngle(float angle) {
        angle = angle % 360;
        if (angle > 180) angle -= 360;
        if (angle < -180) angle += 360;
        return angle;
    }
}
