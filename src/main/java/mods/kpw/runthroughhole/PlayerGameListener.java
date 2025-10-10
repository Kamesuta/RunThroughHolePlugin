package mods.kpw.runthroughhole;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
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
    private final float GESTURE_THRESHOLD = 10.0f; // ジェスチャー開始・中央判定の閾値（度）
    private final long COOLDOWN_MS = 200; // クールダウン時間（ミリ秒）

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
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        BlockDisplay display = plugin.getPlayerDisplays().get(playerId);
        if (display == null)
            return; // ゲーム中でないプレイヤーは無視

        PlayerData data = getOrInitPlayerData(playerId);

        // クールダウンチェック
        long currentTime = System.currentTimeMillis();
        boolean isInCooldown = (currentTime - data.lastCommandTime) < COOLDOWN_MS;
        if (isInCooldown)
            return; // クールダウン中は無視

        Action action = event.getAction();
        Quaternionf newRotation = new Quaternionf();
        boolean shouldRotate = false;

        // 左クリック：反時計回りRoll回転
        if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            newRotation.rotateAxis((float) Math.toRadians(-90.0f), 0, 0, 1);
            player.sendMessage("反時計回りにRoll回転");
            shouldRotate = true;
        }
        // 右クリック：時計回りRoll回転
        else if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            newRotation.rotateAxis((float) Math.toRadians(90.0f), 0, 0, 1);
            player.sendMessage("時計回りにRoll回転");
            shouldRotate = true;
        }

        if (shouldRotate) {
            applyRotation(playerId, display, newRotation);
        }
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

        // クールダウンチェック
        long currentTime = System.currentTimeMillis();
        boolean isInCooldown = (currentTime - data.lastCommandTime) < COOLDOWN_MS;

        // 左右（Yaw）と上下（Pitch）で独立して判定
        boolean isYawOutside = Math.abs(yawDiff) > GESTURE_THRESHOLD;
        boolean isPitchOutside = Math.abs(pitchDiff) > GESTURE_THRESHOLD;

        // Yaw回転判定
        if (isYawOutside && !isInCooldown && !data.isYawOutside) {
            Quaternionf newRotation = new Quaternionf();
            if (yawDiff > 0) { // 右
                newRotation.rotateAxis((float) Math.toRadians(-90.0f), 0, 1, 0);
                player.sendMessage("右に回転（Yaw軸）");
            } else { // 左
                newRotation.rotateAxis((float) Math.toRadians(90.0f), 0, 1, 0);
                player.sendMessage("左に回転（Yaw軸）");
            }
            applyRotation(playerId, display, newRotation);
            data.isYawOutside = true;
        } else if (!isYawOutside) {
            data.isYawOutside = false;
        }

        // Pitch回転判定
        if (isPitchOutside && !isInCooldown && !data.isPitchOutside) {
            Quaternionf newRotation = new Quaternionf();
            if (pitchDiff > 0) { // 下
                newRotation.rotateAxis((float) Math.toRadians(-90.0f), -1, 0, 0);
                player.sendMessage("下に回転（Pitch軸）");
            } else { // 上
                newRotation.rotateAxis((float) Math.toRadians(90.0f), -1, 0, 0);
                player.sendMessage("上に回転（Pitch軸）");
            }
            applyRotation(playerId, display, newRotation);
            data.isPitchOutside = true;
        } else if (!isPitchOutside) {
            data.isPitchOutside = false;
        }

        // ガイド表示：罫線を使った9パターン
        String currentGuide = null;
        
        if (!isYawOutside && !isPitchOutside) {
            // 上下左右が基準値内
            currentGuide = "┼";
        } else if (!isYawOutside && pitchDiff < 0) {
            // 左右が基準値内、上に外れている
            currentGuide = "┬";
        } else if (!isYawOutside && pitchDiff > 0) {
            // 左右が基準値内、下に外れている
            currentGuide = "┴";
        } else if (yawDiff < 0 && !isPitchOutside) {
            // 左に外れている、上下が基準値内
            currentGuide = "├";
        } else if (yawDiff > 0 && !isPitchOutside) {
            // 右に外れている、上下が基準値内
            currentGuide = "┤";
        } else if (yawDiff < 0 && pitchDiff < 0) {
            // 左上に外れている
            currentGuide = "┌";
        } else if (yawDiff > 0 && pitchDiff < 0) {
            // 右上に外れている
            currentGuide = "┐";
        } else if (yawDiff < 0 && pitchDiff > 0) {
            // 左下に外れている
            currentGuide = "└";
        } else if (yawDiff > 0 && pitchDiff > 0) {
            // 右下に外れている
            currentGuide = "┘";
        }
        
        // ガイドが変更された場合のみ更新
        if (currentGuide != null && !currentGuide.equals(data.currentGuide)) {
            Title title = Title.title(
                    Component.empty(),
                    Component.text(currentGuide),
                    Title.Times.times(Duration.ofMillis(0), Duration.ofDays(1), Duration.ofMillis(0)));
            player.showTitle(title);
            data.currentGuide = currentGuide;
        } else if (currentGuide == null && data.currentGuide != null) {
            // ガイドを非表示
            Title title = Title.title(
                    Component.empty(),
                    Component.empty(),
                    Title.Times.times(Duration.ofMillis(0), Duration.ofMillis(0), Duration.ofMillis(0)));
            player.showTitle(title);
            data.currentGuide = null;
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
        String currentGuide; // 現在表示中のガイド（null = 非表示）
        boolean isYawOutside; // Yaw方向でGESTURE_THRESHOLD外にいるかどうか
        boolean isPitchOutside; // Pitch方向でGESTURE_THRESHOLD外にいるかどうか
        long lastCommandTime; // 最後にコマンドを実行した時刻

        PlayerData() {
            // 初期視点に合わせてBlockDisplayの回転も初期化 (X+方向: Yaw -90, Pitch 0)
            // MinecraftのYawとJOMLのY軸回転の方向を合わせるため+90度
            this.rotation = new Quaternionf().rotateY((float) Math.toRadians(90.0f));
            this.currentGuide = null;
            this.isYawOutside = false;
            this.isPitchOutside = false;
            this.lastCommandTime = 0;
        }
    }
}
