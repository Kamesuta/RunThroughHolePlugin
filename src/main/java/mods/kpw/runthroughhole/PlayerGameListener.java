package mods.kpw.runthroughhole;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerGameListener implements Listener {

    private final Main plugin;
    private final Map<UUID, PlayerData> playerDataMap = new HashMap<>();

    // ジェスチャー認識用
    private final float GESTURE_THRESHOLD = 15.0f; // ジェスチャー開始・中央判定の閾値（度）
    private final long COOLDOWN_MS = 500; // クールダウン時間（ミリ秒）

    // 目標視点 (Z+方向: Yaw 0, Pitch 0)
    private static final float TARGET_YAW = 0.0f;
    private static final float TARGET_PITCH = 0.0f;

    public PlayerGameListener(Main plugin) {
        this.plugin = plugin;
    }

    private PlayerData getOrInitPlayerData(UUID playerId) {
        return playerDataMap.computeIfAbsent(playerId, k -> new PlayerData());
    }

    // 回転を適用してBlockDisplayを更新する共通メソッド
    private void applyRotation(UUID playerId, BlockDisplay display, Quaternionf newRotation) {
        PlayerData data = getOrInitPlayerData(playerId);

        // 現在のBlockDisplayの回転に新しい回転を適用
        Quaternionf rotation = new Quaternionf()
                .mul(newRotation)
                .mul(data.rotation);
        data.rotation = rotation;

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
        
        // クールダウンタイムスタンプを更新
        data.lastCommandTime = System.currentTimeMillis();
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        BlockDisplay display = plugin.getPlayerDisplays().get(playerId);
        if (display == null)
            return; // ゲーム中でないプレイヤーは無視

        PlayerData data = getOrInitPlayerData(playerId);

        Location currentLocation = player.getLocation();
        float currentYaw = currentLocation.getYaw();
        float currentPitch = currentLocation.getPitch();

        // 中央位置からのずれを計算
        float yawDiff = normalizeAngle(currentYaw - TARGET_YAW);
        float pitchDiff = normalizeAngle(currentPitch - TARGET_PITCH);

        float totalDiff = (float) Math.sqrt(yawDiff * yawDiff + pitchDiff * pitchDiff);

        // クールダウンチェック
        long currentTime = System.currentTimeMillis();
        boolean isInCooldown = (currentTime - data.lastCommandTime) < COOLDOWN_MS;

        // GESTURE_THRESHOLD外に出たら、X印で4分割した領域でYaw/Pitch回転
        if (totalDiff > GESTURE_THRESHOLD) {
            // クールダウン中でなく、外に出ていない状態から外に出た場合のみコマンド実行
            if (!isInCooldown && !data.isOutside) {
                // X印で4分割：yawDiffとpitchDiffの絶対値を比較
                if (Math.abs(yawDiff) > Math.abs(pitchDiff)) {
                    // Yaw方向が優勢
                    Quaternionf newRotation = new Quaternionf();
                    if (yawDiff > 0) { // 右
                        newRotation.rotateAxis((float) Math.toRadians(-90.0f), 0, 1, 0);
                        player.sendMessage("右に回転（Yaw軸）");
                    } else { // 左
                        newRotation.rotateAxis((float) Math.toRadians(90.0f), 0, 1, 0);
                        player.sendMessage("左に回転（Yaw軸）");
                    }
                    applyRotation(playerId, display, newRotation);
                } else {
                    // Pitch方向が優勢
                    Quaternionf newRotation = new Quaternionf();
                    if (pitchDiff > 0) { // 下
                        newRotation.rotateAxis((float) Math.toRadians(-90.0f), -1, 0, 0);
                        player.sendMessage("下に回転（Pitch軸）");
                    } else { // 上
                        newRotation.rotateAxis((float) Math.toRadians(90.0f), -1, 0, 0);
                        player.sendMessage("上に回転（Pitch軸）");
                    }
                    applyRotation(playerId, display, newRotation);
                }
                // 外に出た状態にする
                data.isOutside = true;
            }
        } else {
            // GESTURE_THRESHOLD内に戻ったらフラグをリセット
            data.isOutside = false;
        }

        // 基準位置にいる間はガイドを常に表示、離れたら即座に非表示
        boolean isAtCenter = totalDiff < GESTURE_THRESHOLD;

        if (isAtCenter && !data.isShowingGuide) {
            // 基準位置に入ったらガイドを表示（実質無限に表示し続ける）
            Title title = Title.title(
                    Component.empty(),
                    Component.text("←↑↓→"),
                    Title.Times.times(Duration.ofMillis(0), Duration.ofDays(1), Duration.ofMillis(0)));
            player.showTitle(title);
            data.isShowingGuide = true;
        } else if (!isAtCenter && data.isShowingGuide) {
            // 基準位置から離れたらガイドを即座に非表示
            Title title = Title.title(
                    Component.empty(),
                    Component.empty(),
                    Title.Times.times(Duration.ofMillis(0), Duration.ofMillis(0), Duration.ofMillis(0)));
            player.showTitle(title);
            data.isShowingGuide = false;
        }

        // BlockDisplayの位置をプレイヤーの頭上に同期（Yaw/Pitchは0にリセット）
        Location displayLocation = player.getLocation().add(0, 2, 0);
        displayLocation.setYaw(0);
        displayLocation.setPitch(0);
        display.teleport(displayLocation);
    }

    private float normalizeAngle(float angle) {
        angle = angle % 360;
        if (angle > 180)
            angle -= 360;
        if (angle < -180)
            angle += 360;
        return angle;
    }

    // プレイヤーデータクラス
    private static class PlayerData {
        Quaternionf rotation;
        boolean isShowingGuide;
        boolean isOutside; // GESTURE_THRESHOLD外にいるかどうか
        long lastCommandTime; // 最後にコマンドを実行した時刻

        PlayerData() {
            // 初期視点に合わせてBlockDisplayの回転も初期化 (X+方向: Yaw -90, Pitch 0)
            // MinecraftのYawとJOMLのY軸回転の方向を合わせるため+90度
            this.rotation = new Quaternionf().rotateY((float) Math.toRadians(90.0f));
            this.isShowingGuide = false;
            this.isOutside = false;
            this.lastCommandTime = 0;
        }
    }
}
