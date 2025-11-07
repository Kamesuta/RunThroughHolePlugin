package mods.kpw.runthroughhole.player;

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
import org.bukkit.event.player.PlayerQuitEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import mods.kpw.runthroughhole.Main;
import mods.kpw.runthroughhole.game.GameScoreTracker;
import mods.kpw.runthroughhole.game.GameSound;
import mods.kpw.runthroughhole.game.PlayerCube;

import java.time.Duration;
import java.util.UUID;

public class PlayerGameListener implements Listener {

    private final Main plugin;
    private ProtocolManager protocolManager;

    // ジェスチャー認識用
    private final float GESTURE_THRESHOLD = 10.0f; // ジェスチャー開始・中央判定の閾値（度）

    // 視線追従設定
    private final float LERP_SPEED = 0.02f; // Lerpの速度（0.0-1.0、小さいほどゆっくり）

    public PlayerGameListener(Main plugin) {
        this.plugin = plugin;
        this.protocolManager = ProtocolLibrary.getProtocolManager();
        setupPacketListeners();
    }

    private void setupPacketListeners() {
        Main mainPlugin = this.plugin;

        // STEER_VEHICLEパケットリスナー（WASD入力）
        protocolManager.addPacketListener(
                new PacketAdapter(mainPlugin, ListenerPriority.NORMAL, PacketType.Play.Client.STEER_VEHICLE) {
                    @Override
                    public void onPacketReceiving(PacketEvent event) {
                        Player player = event.getPlayer();
                        if (player == null)
                            return;

                        PlayerData data = mainPlugin.getPlayerDataManager().getPlayerData(player);
                        if (data == null || data.camera == null || data.isGameOver)
                            return;

                        boolean forward, backward, left, right, jump, shift;

                        try {
                            PacketContainer packet = event.getPacket();
                            var input = packet.getStructures().read(0).getBooleans();
                            forward = input.read(0); // W
                            backward = input.read(1); // S
                            left = input.read(2); // A
                            right = input.read(3); // D
                            jump = input.read(4); // Space
                            shift = input.read(5); // Shift (スニーク)
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
                                handleWASDInput(data, finalLeft, finalRight, finalForward, finalBackward, finalJump,
                                        finalShift);
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
                if (player == null)
                    return;

                PlayerData data = mainPlugin.getPlayerDataManager().getPlayerData(player);
                if (data == null || data.camera == null || data.isGameOver)
                    return;

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
                        handleViewRotation(data, finalYaw, finalPitch);
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
        Player player = plugin.getServer().getPlayer(playerId);
        if (player == null)
            return;
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player);
        if (data == null || data.cube == null)
            return;

        // 回転操作があった場合、連続加速を中止
        data.cube.stopContinuousBoosting();

        // キューブに回転を適用
        boolean rotationSuccess = data.cube.applyRotation(newRotation);

        // 回転が成功した場合のみ効果音を再生
        if (rotationSuccess) {
            GameSound.ROTATION.play(data.player);
        }
        
        // クールダウンタイムスタンプを更新
        data.lastCommandTick = plugin.getServer().getCurrentTick();
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player);

        // ゲーム中のプレイヤーの場合、ゲーム終了処理を実行
        if (data != null && data.cube != null && !data.isGameOver) {
            plugin.getGameManager().stopGame(player, GameScoreTracker.END_TYPE_LOGOUT);
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player);
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
            applyRotation(player.getUniqueId(), newRotation);
            data.lastCommandTick = currentTick;
        }
    }

    // WASD入力を処理
    private void handleWASDInput(PlayerData playerData, boolean left, boolean right, boolean forward, boolean backward,
            boolean jump, boolean shift) {
        // Shiftキーが押された場合はゲームオーバー処理（3秒スペクテーターモード）
        if (shift && !playerData.isGameOver) {
            plugin.getGameManager().gameOver(playerData, new java.util.ArrayList<>(), GameScoreTracker.END_TYPE_PLAYER_QUIT);
            return;
        }

        // 加速が開始されていない場合のみ効果音を再生するため、状態を先に保存
        boolean wasNotBoosting = !playerData.isSpacePressed;

        // Spaceキーの押下状態を更新して速度を制御
        playerData.isSpacePressed = jump;

        if (jump) {
            // プレビューパネルが緑でSpaceキーが押された場合、連続加速を開始
            Boolean isGreen = playerData.preview.isPreviewGreen();
            if (isGreen != null && isGreen) {
                GameSound.CONTINUOUS_BOOST_START.play(playerData.player);
                playerData.cube.startContinuousBoosting();
            }
            // ジャンプキーが押された場合、加速開始
            playerData.cube.setBoosting(jump);

            // 加速開始時の効果音を再生（Spaceキーが押された瞬間のみ）
            if (wasNotBoosting) {
                GameSound.BOOST_START.play(playerData.player);
            }
        } else {
            // 連続加速モードじゃない場合、ジャンプキーを離したら加速停止
            if (!playerData.cube.isContinuousBoosting()) {
                playerData.cube.setBoosting(jump);
            }
        }

        int currentTick = plugin.getServer().getCurrentTick();
        int moveCooldownTicks = PlayerCube.MOVE_INTERPOLATION_DURATION * 2; // Interpolation時間の2倍をクールダウンに
        if (currentTick - playerData.lastMoveTick < moveCooldownTicks) {
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
            // 移動操作があった場合、連続加速を中止
            playerData.cube.stopContinuousBoosting();

            // キューブに移動を適用
            boolean moveSuccess = playerData.cube.move(gridMove);

            // 移動が成功した場合のみ効果音を再生
            if (moveSuccess) {
                GameSound.MOVE.play(playerData.player);
                playerData.lastMoveTick = currentTick;
            }
            // 移動が失敗した場合は何もしない
        }
    }

    private void handleViewRotation(PlayerData playerData, float currentYaw, float currentPitch) {
        // 現在のtickを取得
        int currentTick = plugin.getServer().getCurrentTick();

        // クールダウンチェック（tickベース）
        int rotationCooldownTicks = PlayerCube.ROTATION_INTERPOLATION_DURATION * 2; // Interpolation時間の2倍をクールダウンに
        boolean isInCooldown = (currentTick - playerData.lastCommandTick) < rotationCooldownTicks;

        if (!isInCooldown) {
            // 現在の目標視点をプレイヤーの視線にゆっくりとLerp
            playerData.currentTargetYaw = lerpAngle(playerData.currentTargetYaw, currentYaw, LERP_SPEED);
            playerData.currentTargetPitch = lerpAngle(playerData.currentTargetPitch, currentPitch, LERP_SPEED);
        }

        // 現在の目標視点からのずれを計算
        float yawDiff = normalizeAngle(currentYaw - playerData.currentTargetYaw);
        float pitchDiff = normalizeAngle(currentPitch - playerData.currentTargetPitch);

        // 左右（Yaw）と上下（Pitch）で独立して判定
        boolean isYawOutside = Math.abs(yawDiff) > GESTURE_THRESHOLD;
        boolean isPitchOutside = Math.abs(pitchDiff) > GESTURE_THRESHOLD;

        // Yaw回転判定
        if (isYawOutside && !isInCooldown && !playerData.isYawOutside) {
            Quaternionf newRotation = new Quaternionf();
            if (yawDiff > 0) { // 右
                newRotation.rotateAxis((float) Math.toRadians(-90.0f), 0, 1, 0);
            } else { // 左
                newRotation.rotateAxis((float) Math.toRadians(90.0f), 0, 1, 0);
            }
            applyRotation(playerData.player.getUniqueId(), newRotation);
            playerData.lastCommandTick = currentTick;
            playerData.isYawOutside = true;
        } else if (!isYawOutside) {
            playerData.isYawOutside = false;
        }

        // Pitch回転判定
        if (isPitchOutside && !isInCooldown && !playerData.isPitchOutside) {
            Quaternionf newRotation = new Quaternionf();
            if (pitchDiff > 0) { // 下
                newRotation.rotateAxis((float) Math.toRadians(-90.0f), -1, 0, 0);
            } else { // 上
                newRotation.rotateAxis((float) Math.toRadians(90.0f), -1, 0, 0);
            }
            applyRotation(playerData.player.getUniqueId(), newRotation);
            playerData.lastCommandTick = currentTick;
            playerData.isPitchOutside = true;
        } else if (!isPitchOutside) {
            playerData.isPitchOutside = false;
        }

        // ガイド表示：罫線を使った9パターン
        updateGuideDisplay(playerData, yawDiff, pitchDiff, isYawOutside, isPitchOutside);
    }

    // ガイド表示の更新
    private void updateGuideDisplay(PlayerData playerData, float yawDiff, float pitchDiff, boolean isYawOutside,
            boolean isPitchOutside) {
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
        if (currentGuide != null && !currentGuide.equals(playerData.currentGuide)) {
            Title title = Title.title(
                    Component.empty(),
                    Component.text(currentGuide),
                    Title.Times.times(Duration.ofMillis(0), Duration.ofSeconds(5), Duration.ofSeconds(1)));
            playerData.player.showTitle(title);
            playerData.currentGuide = currentGuide;
        } else if (currentGuide == null && playerData.currentGuide != null) {
            // ガイドを非表示
            Title title = Title.title(
                    Component.empty(),
                    Component.empty(),
                    Title.Times.times(Duration.ofMillis(0), Duration.ofMillis(0), Duration.ofSeconds(1)));
            playerData.player.showTitle(title);
            playerData.currentGuide = null;
        }
    }

    // 角度のLerp（線形補間）
    private float lerpAngle(float current, float target, float speed) {
        float diff = normalizeAngle(target - current);
        return current + diff * speed;
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
