package mods.kpw.runthroughhole;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.time.Duration;
import java.util.UUID;

public class PlayerGameListener implements Listener {

    private final Main plugin;
    private ProtocolManager protocolManager;

    // ジェスチャー認識用
    private final float GESTURE_THRESHOLD = 10.0f; // ジェスチャー開始・中央判定の閾値（度）

    // 目標視点 (Z+方向: Yaw 0, Pitch 0)
    private static final float TARGET_YAW = 0.0f;
    private static final float TARGET_PITCH = 0.0f;

    public PlayerGameListener(Main plugin) {
        this.plugin = plugin;
        this.protocolManager = ProtocolLibrary.getProtocolManager();
        setupPacketListeners();
    }
    
    private void setupPacketListeners() {
        Main mainPlugin = this.plugin;
        
        // STEER_VEHICLEパケットリスナー（WASD入力）
        protocolManager.addPacketListener(new PacketAdapter(mainPlugin, ListenerPriority.NORMAL, PacketType.Play.Client.STEER_VEHICLE) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                Player player = event.getPlayer();
                if (player == null) return;
                
                PlayerData data = mainPlugin.getPlayerData(player.getUniqueId());
                if (data == null || data.camera == null || data.isGameOver) return;
                
                boolean forward, backward, left, right, jump, shift;
                
                try {
                    PacketContainer packet = event.getPacket();
                    var input = packet.getStructures().read(0).getBooleans();
                    forward = input.read(0);   // W
                    backward = input.read(1);  // S
                    left = input.read(2);      // A
                    right = input.read(3);     // D
                    jump = input.read(4);      // Space
                    shift = input.read(5);     // Shift (スニーク)
                } catch (Exception e) {
                    Main.logger.warning("[STEER_VEHICLE] パケット解析エラー: " + e.getMessage());
                    e.printStackTrace();
                    return;
                }
                
                // メインスレッドで実行
                boolean finalForward = forward;
                boolean finalBackward = backward;
                boolean finalLeft = left;
                boolean finalRight = right;
                boolean finalJump = jump;
                boolean finalShift = shift;
                
                Bukkit.getScheduler().runTask(mainPlugin, () -> {
                    try {
                        // Shiftキーが押された場合はゲームオーバー
                        if (finalShift) {
                            mainPlugin.gameOver(player, "スニークキーが押されたためゲームを中止します");
                            return;
                        }
                        
                        handleWASDInput(player, data, finalLeft, finalRight, finalForward, finalBackward, finalJump);
                    } catch (Exception e) {
                        Main.logger.severe("[STEER_VEHICLE] handleWASDInput実行エラー: " + e.getMessage());
                        e.printStackTrace();
                    }
                });
            }
        });
        
        // LOOKパケットリスナー（視点回転）
        protocolManager.addPacketListener(new PacketAdapter(mainPlugin, ListenerPriority.NORMAL,
                PacketType.Play.Client.LOOK,
                PacketType.Play.Client.POSITION_LOOK) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                Player player = event.getPlayer();
                if (player == null) return;
                
                PlayerData data = mainPlugin.getPlayerData(player.getUniqueId());
                if (data == null || data.camera == null || data.isGameOver) return;
                
                float yaw, pitch;
                
                try {
                    yaw = event.getPacket().getFloat().read(0);
                    pitch = event.getPacket().getFloat().read(1);
                } catch (Exception e) {
                    Main.logger.warning("[LOOK] パケット解析エラー: " + e.getMessage());
                    e.printStackTrace();
                    return;
                }
                
                // メインスレッドで実行
                float finalYaw = yaw;
                float finalPitch = pitch;
                
                Bukkit.getScheduler().runTask(mainPlugin, () -> {
                    try {
                        handleViewRotation(player, player.getUniqueId(), data, finalYaw, finalPitch);
                    } catch (Exception e) {
                        Main.logger.severe("[LOOK] handleViewRotation実行エラー: " + e.getMessage());
                        e.printStackTrace();
                    }
                });
            }
        });
    }
    
    public void cleanup() {
        if (protocolManager != null) {
            protocolManager.removePacketListeners(plugin);
        }
    }

    // 回転を適用してBlockDisplayを更新する共通メソッド
    private void applyRotation(UUID playerId, Quaternionf newRotation) {
        PlayerData data = plugin.getPlayerData(playerId);
        if (data == null || data.cube == null) return;

        // キューブに回転を適用
        data.cube.applyRotation(newRotation);
        
        // クールダウンタイムスタンプを更新
        data.lastCommandTick = plugin.getServer().getCurrentTick();
        
        // プレビューを更新（回転により通過可能な穴が変わる可能性があるため）
        if (data.preview != null && data.initialLocation != null && data.camera != null) {
            Player player = data.camera.getPlayer();
            if (player != null) {
                data.preview.update(data.cube, data.initialLocation, player);
            }
        }
    }

    
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        PlayerData data = plugin.getPlayerData(playerId);
        if (data == null || data.cube == null || data.isGameOver)
            return; // ゲーム中でないプレイヤーまたはゲームオーバー中のプレイヤーは無視

        // クールダウンチェック（tickベース）
        int currentTick = plugin.getServer().getCurrentTick();
        int rotationCooldownTicks = PlayerCube.ROTATION_INTERPOLATION_DURATION * 2; // Interpolation時間の2倍をクールダウンに
        boolean isInCooldown = (currentTick - data.lastCommandTick) < rotationCooldownTicks;
        if (isInCooldown)
            return; // クールダウン中は無視

        Action action = event.getAction();
        Quaternionf newRotation = new Quaternionf();
        boolean shouldRotate = false;

        // 左クリック：反時計回りRoll回転
        if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            newRotation.rotateAxis((float) Math.toRadians(-90.0f), 0, 0, 1);
            shouldRotate = true;
        }
        // 右クリック：時計回りRoll回転
        else if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            newRotation.rotateAxis((float) Math.toRadians(90.0f), 0, 0, 1);
            shouldRotate = true;
        }

        if (shouldRotate) {
            applyRotation(playerId, newRotation);
            data.lastCommandTick = currentTick;
        }
    }

    // WASD入力を処理
    private void handleWASDInput(Player player, PlayerData data, boolean left, boolean right, boolean forward, boolean backward, boolean jump) {
        // Spaceキーの押下状態を更新して速度を制御
        data.isSpacePressed = jump;
        
        // 加速状態をキューブに反映
        if (data.cube != null) {
            data.cube.setBoosting(jump);
        }
        
        int currentTick = plugin.getServer().getCurrentTick();
        int moveCooldownTicks = PlayerCube.MOVE_INTERPOLATION_DURATION * 2; // Interpolation時間の2倍をクールダウンに
        if (currentTick - data.lastMoveTick < moveCooldownTicks) {
            return; // クールダウン中
        }
        
        Vector3f gridMove = new Vector3f(0, 0, 0);
        
        // 左右移動（A/D）
        if (left && !right) {
            gridMove.x = 1; // A
        } else if (right && !left) {
            gridMove.x = -1; // D
        }
        // 上下移動（W/S）
        else if (forward && !backward) {
            gridMove.y = 1; // W
        } else if (backward && !forward) {
            gridMove.y = -1; // S
        }
        
        // キューブを移動（移動先に衝突がない場合のみ）
        if (gridMove.x != 0 || gridMove.y != 0 || gridMove.z != 0) {
            // 移動先で衝突するかチェック
            if (!data.cube.wouldCollideAt(gridMove)) {
                // 衝突しない場合のみ移動
                data.cube.move(gridMove);
                data.lastMoveTick = currentTick;
                
                // プレビューを更新（移動により壁との位置関係が変わる可能性があるため）
                if (data.preview != null && data.initialLocation != null) {
                    data.preview.update(data.cube, data.initialLocation, player);
                }
            }
            // 衝突する場合は移動をキャンセル（何もしない）
        }
    }
    
    private void handleViewRotation(Player player, UUID playerId, PlayerData data, float currentYaw, float currentPitch) {

        // 中央位置からのずれを計算
        float yawDiff = normalizeAngle(currentYaw - TARGET_YAW);
        float pitchDiff = normalizeAngle(currentPitch - TARGET_PITCH);

        // クールダウンチェック（tickベース）
        int currentTick = plugin.getServer().getCurrentTick();
        int rotationCooldownTicks = PlayerCube.ROTATION_INTERPOLATION_DURATION * 2; // Interpolation時間の2倍をクールダウンに
        boolean isInCooldown = (currentTick - data.lastCommandTick) < rotationCooldownTicks;

        // 左右（Yaw）と上下（Pitch）で独立して判定
        boolean isYawOutside = Math.abs(yawDiff) > GESTURE_THRESHOLD;
        boolean isPitchOutside = Math.abs(pitchDiff) > GESTURE_THRESHOLD;

        // Yaw回転判定
        if (isYawOutside && !isInCooldown && !data.isYawOutside) {
            Quaternionf newRotation = new Quaternionf();
            if (yawDiff > 0) { // 右
                newRotation.rotateAxis((float) Math.toRadians(-90.0f), 0, 1, 0);
            } else { // 左
                newRotation.rotateAxis((float) Math.toRadians(90.0f), 0, 1, 0);
            }
            applyRotation(playerId, newRotation);
            data.lastCommandTick = currentTick;
            data.isYawOutside = true;
        } else if (!isYawOutside) {
            data.isYawOutside = false;
        }

        // Pitch回転判定
        if (isPitchOutside && !isInCooldown && !data.isPitchOutside) {
            Quaternionf newRotation = new Quaternionf();
            if (pitchDiff > 0) { // 下
                newRotation.rotateAxis((float) Math.toRadians(-90.0f), -1, 0, 0);
            } else { // 上
                newRotation.rotateAxis((float) Math.toRadians(90.0f), -1, 0, 0);
            }
            applyRotation(playerId, newRotation);
            data.lastCommandTick = currentTick;
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
    }
    
    private float normalizeAngle(float angle) {
        angle = angle % 360;
        if (angle > 180)
            angle -= 360;
        if (angle < -180)
            angle += 360;
        return angle;
    }
}
