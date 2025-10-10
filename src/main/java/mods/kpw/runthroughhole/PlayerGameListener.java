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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PlayerGameListener implements Listener {

    private final Main plugin;
    private final Map<UUID, PlayerData> playerDataMap = new HashMap<>();

    // ジェスチャー認識用
    private final float GESTURE_THRESHOLD = 15.0f; // ジェスチャー開始・中央判定の閾値（度）
    private final float GESTURE_MOVE_LENGTH = 2.0f; // Roll判定の最小移動量（度）
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
        // ただし、履歴が3つ以上ある場合はRoll判定を優先するため許可
        if (totalDiff > GESTURE_THRESHOLD) {
            // クールダウン中でなく、履歴が3つ未満で、外に出ていない状態から外に出た場合のみコマンド実行
            if (!isInCooldown && data.directionHistory.size() < 3 && !data.isOutside) {
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
                    data.resetGesture();
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
                    data.resetGesture();
                }
                // 外に出た状態にする
                data.isOutside = true;
            }
            // 履歴が3つ以上の場合やクールダウン中は何もしない（Roll判定を続行）
        } else {
            // GESTURE_THRESHOLD内に戻ったらフラグをリセット
            data.isOutside = false;
        }

        // Roll判定：GESTURE_THRESHOLD内、または履歴が3つ以上の場合（クールダウン中は除く）
        if ((totalDiff <= GESTURE_THRESHOLD || data.directionHistory.size() >= 3) && !isInCooldown) {
            // 移動方向を記録してRoll判定
            if (data.lastYaw == null || data.lastPitch == null) {
                // 初回
                data.lastYaw = yawDiff;
                data.lastPitch = pitchDiff;
            } else {
                // 前回位置からの移動量を計算
                float yawMove = yawDiff - data.lastYaw;
                float pitchMove = pitchDiff - data.lastPitch;
                float moveDistance = (float) Math.sqrt(yawMove * yawMove + pitchMove * pitchMove);

                if (moveDistance > GESTURE_MOVE_LENGTH) {
                    // 移動方向を判定
                    Direction direction = null;
                    if (Math.abs(yawMove) > Math.abs(pitchMove)) {
                        direction = yawMove > 0 ? Direction.RIGHT : Direction.LEFT;
                    } else {
                        direction = pitchMove > 0 ? Direction.DOWN : Direction.UP;
                    }

                    // 同じ向きは省く
                    if (data.directionHistory.isEmpty()
                            || data.directionHistory.get(data.directionHistory.size() - 1) != direction) {
                        // 履歴が破綻していないかチェック
                        if (!data.directionHistory.isEmpty()
                                && !isValidNextDirection(data.directionHistory, direction)) {
                            // 破綻している場合、新しい方向だけを残してクリア
                            Main.logger.info("履歴破綻検出。リセットして新しい方向から開始: " + direction);
                            data.directionHistory.clear();
                        }

                        data.directionHistory.add(direction);
                        Main.logger.info("方向記録: " + direction + " (履歴: " + data.directionHistory.size() + ")");

                        // 3方向以上でRoll回転チェック
                        if (data.directionHistory.size() >= 3) {
                            RollDirection rollDir = checkCircle(data.directionHistory);
                            if (rollDir != RollDirection.NONE) {
                                Quaternionf newRotation = new Quaternionf();
                                if (rollDir == RollDirection.CLOCKWISE) {
                                    newRotation.rotateAxis((float) Math.toRadians(90.0f), 0, 0, 1);
                                    player.sendMessage("時計回りにRoll回転");
                                } else {
                                    newRotation.rotateAxis((float) Math.toRadians(-90.0f), 0, 0, 1);
                                    player.sendMessage("反時計回りにRoll回転");
                                }
                                applyRotation(playerId, display, newRotation);
                                data.resetGesture();
                            }
                        }
                    }

                    // 位置を更新
                    data.lastYaw = yawDiff;
                    data.lastPitch = pitchDiff;
                }
            }
        }

        // 基準位置にいる間はガイドを常に表示、離れたら即座に非表示
        boolean isAtCenter = totalDiff < GESTURE_THRESHOLD;

        if (isAtCenter && !data.isShowingGuide) {
            // 基準位置に入ったらガイドを表示（実質無限に表示し続ける）
            Title title = Title.title(
                    Component.empty(),
                    Component.text("←⟲↑↓⟳→"),
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

    // 新しい方向が履歴に対して有効かチェック（破綻していないか）
    private boolean isValidNextDirection(List<Direction> history, Direction newDirection) {
        if (history.isEmpty())
            return true;

        // 最後の方向
        Direction last = history.get(history.size() - 1);

        if (history.size() == 1) {
            // 2つ目の方向は、1つ目と連続している必要がある
            int lastOrdinal = last.ordinal();
            int newOrdinal = newDirection.ordinal();
            int clockwiseNext = (lastOrdinal + 1) % 4;
            int counterClockwiseNext = (lastOrdinal - 1 + 4) % 4;

            // 時計回りまたは反時計回りで連続していればOK
            return newOrdinal == clockwiseNext || newOrdinal == counterClockwiseNext;
        }

        // 履歴が2つ以上の場合、最初の2つの方向からパターン（時計回り/反時計回り）を判定
        Direction first = history.get(0);
        Direction second = history.get(1);

        boolean isClockwisePattern = (first.ordinal() + 1) % 4 == second.ordinal();
        boolean isCounterClockwisePattern = (first.ordinal() - 1 + 4) % 4 == second.ordinal();

        // パターンが不明な場合（起こらないはずだが念のため）
        if (!isClockwisePattern && !isCounterClockwisePattern) {
            return true;
        }

        // パターンに従った期待される次の方向
        if (isClockwisePattern) {
            int expectedNext = (last.ordinal() + 1) % 4;
            return newDirection.ordinal() == expectedNext;
        } else {
            int expectedNext = (last.ordinal() - 1 + 4) % 4;
            return newDirection.ordinal() == expectedNext;
        }
    }

    // 3方向以上が連続しているかチェック
    private RollDirection checkCircle(List<Direction> history) {
        if (history.size() < 3)
            return RollDirection.NONE;

        // 最後の3つまたは4つを取得（最大4つ）
        int size = history.size();
        int checkSize = Math.min(4, size);
        List<Direction> lastN = history.subList(size - checkSize, size);

        // ordinalを使って連続性をチェック
        boolean isClockwise = true;
        boolean isCounterClockwise = true;

        for (int i = 0; i < lastN.size() - 1; i++) {
            int current = lastN.get(i).ordinal();
            int next = lastN.get(i + 1).ordinal();

            // 時計回り: ordinalが+1ずつ増加（3の次は0）
            int expectedClockwise = (current + 1) % 4;
            if (next != expectedClockwise) {
                isClockwise = false;
            }

            // 反時計回り: ordinalが-1ずつ減少（0の前は3）
            int expectedCounterClockwise = (current - 1 + 4) % 4;
            if (next != expectedCounterClockwise) {
                isCounterClockwise = false;
            }
        }

        if (isClockwise)
            return RollDirection.CLOCKWISE;
        if (isCounterClockwise)
            return RollDirection.COUNTERCLOCKWISE;
        return RollDirection.NONE;
    }

    private float normalizeAngle(float angle) {
        angle = angle % 360;
        if (angle > 180)
            angle -= 360;
        if (angle < -180)
            angle += 360;
        return angle;
    }

    // 移動方向（時計回り順で定義）
    private enum Direction {
        UP, RIGHT, DOWN, LEFT
    }

    // Roll回転方向
    private enum RollDirection {
        NONE, CLOCKWISE, COUNTERCLOCKWISE
    }

    // プレイヤーデータクラス
    private static class PlayerData {
        Quaternionf rotation;
        List<Direction> directionHistory;
        Float lastYaw;
        Float lastPitch;
        boolean isShowingGuide;
        boolean isOutside; // GESTURE_THRESHOLD外にいるかどうか
        long lastCommandTime; // 最後にコマンドを実行した時刻

        PlayerData() {
            // 初期視点に合わせてBlockDisplayの回転も初期化 (X+方向: Yaw -90, Pitch 0)
            // MinecraftのYawとJOMLのY軸回転の方向を合わせるため+90度
            this.rotation = new Quaternionf().rotateY((float) Math.toRadians(90.0f));
            this.directionHistory = new ArrayList<>();
            this.lastYaw = null;
            this.lastPitch = null;
            this.isShowingGuide = false;
            this.isOutside = false;
            this.lastCommandTime = 0;
        }

        void resetGesture() {
            this.directionHistory.clear();
            this.lastYaw = null;
            this.lastPitch = null;
        }
    }
}
