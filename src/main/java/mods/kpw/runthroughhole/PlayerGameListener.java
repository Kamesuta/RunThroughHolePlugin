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
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.time.Duration;
import java.util.UUID;

public class PlayerGameListener implements Listener {

    private final Main plugin;
    private ProtocolManager protocolManager;

    // ジェスチャー認識用
    private final float GESTURE_THRESHOLD = 10.0f; // ジェスチャー開始・中央判定の閾値（度）
    private final long COOLDOWN_MS = 200; // クールダウン時間（ミリ秒）

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
                if (data == null || data.entity == null) return;
                
                boolean forward, backward, left, right, jump;
                
                try {
                    PacketContainer packet = event.getPacket();
                    var input = packet.getStructures().read(0).getBooleans();
                    forward = input.read(0);   // W
                    backward = input.read(1);  // S
                    left = input.read(2);      // A
                    right = input.read(3);     // D
                    jump = input.read(4);
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
                
                Bukkit.getScheduler().runTask(mainPlugin, () -> {
                    try {
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
                if (data == null || data.entity == null) return;
                
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
        data.lastCommandTime = System.currentTimeMillis();
    }

    @EventHandler
    public void onVehicleExit(VehicleExitEvent event) {
        // プレイヤーが椅子から降りた時
        if (event.getExited() instanceof Player) {
            Player player = (Player) event.getExited();
            PlayerData data = plugin.getPlayerData(player.getUniqueId());
            
            // ゲーム中のプレイヤーが椅子から降りようとした場合
            if (data != null && data.entity != null && event.getVehicle().equals(data.entity)) {
                // イベントをキャンセルして降りれないようにする
                event.setCancelled(true);
                
                // ゲームオーバー
                plugin.gameOver(player, "椅子から降りました！");
            }
        }
    }
    
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        PlayerData data = plugin.getPlayerData(playerId);
        if (data == null || data.cube == null)
            return; // ゲーム中でないプレイヤーは無視

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
            applyRotation(playerId, newRotation);
        }
    }

    // WASD入力を処理
    private void handleWASDInput(Player player, PlayerData data, boolean left, boolean right, boolean forward, boolean backward, boolean jump) {
        long currentTime = System.currentTimeMillis();
        if (currentTime - data.lastMoveTime < COOLDOWN_MS) {
            return; // クールダウン中
        }
        
        Vector3f gridMove = new Vector3f(0, 0, 0);
        String direction = "";
        
        // 左右移動（A/D）
        if (left && !right) {
            gridMove.x = 1; // A
            direction = "左";
        } else if (right && !left) {
            gridMove.x = -1; // D
            direction = "右";
        }
        // 上下移動（W/S）
        else if (forward && !backward) {
            gridMove.y = 1; // W
            direction = "上";
        } else if (backward && !forward) {
            gridMove.y = -1; // S
            direction = "下";
        }
        // ジャンプは今後の加速機能用に予約
        
        // キューブを移動
        if (gridMove.x != 0 || gridMove.y != 0 || gridMove.z != 0) {
            data.cube.move(gridMove);
            data.lastMoveTime = currentTime;
            player.sendMessage(direction + "に移動");
        }
    }
    
    private void handleViewRotation(Player player, UUID playerId, PlayerData data, float currentYaw, float currentPitch) {

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
            applyRotation(playerId, newRotation);
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
            applyRotation(playerId, newRotation);
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
