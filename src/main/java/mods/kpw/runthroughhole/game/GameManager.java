package mods.kpw.runthroughhole.game;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import java.util.List;
import java.util.stream.Collectors;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;

import java.time.Duration;
import mods.kpw.runthroughhole.player.PlayerDataManager;
import mods.kpw.runthroughhole.player.PlayerData;

/**
 * ゲームロジックの管理を行うクラス
 * Mainクラスからゲーム関連機能を分離
 */
public class GameManager {

    private final JavaPlugin plugin;
    private final PlayerDataManager playerDataManager;
    private BukkitTask gameLoopTask;

    public GameManager(JavaPlugin plugin, PlayerDataManager playerDataManager) {
        this.plugin = plugin;
        this.playerDataManager = playerDataManager;

        // スコアボードのObjectiveを登録（プラグイン初期化時に1回だけ）
        GameScoreTracker.registerObjectives();
    }

    /**
     * ゲームループを開始する
     */
    public void startGameLoop() {
        // 自動前進タスクを開始（1tickごと）
        gameLoopTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (PlayerData data : playerDataManager.getAllPlayerData()) {
                // データパックからのstopGameリクエストをチェック
                Player player = data.player;
                if (player != null && data.scoreTracker.getScore("runhole_stop_request") == 1) {
                    data.scoreTracker.setScore("runhole_stop_request", 0);
                    stopGame(player, GameScoreTracker.END_TYPE_COMMAND_STOP);
                    continue;
                }

                if (data.cube != null && !data.isGameOver) {
                    // キューブを前進
                    data.cube.autoForward();
                    data.cube.handleContinuousBoosting(data.preview);

                    // 衝突チェック（Streamで衝突ブロックを取得）
                    List<CubeBlock> collidedBlocks = data.cube.checkCollision(PlayerCube.COLLISION_CHECK_OFFSET, null)
                            .collect(Collectors.toList());

                    if (!collidedBlocks.isEmpty()) {
                        // プレイヤーを取得
                        if (player != null) {
                            gameOver(data, collidedBlocks, GameScoreTracker.END_TYPE_GAME_OVER);
                        }
                        continue;
                    }

                    // カメラを更新
                    if (data.camera != null) {
                        data.camera.update();
                    }

                    // プレビューを更新
                    if (data.preview != null && data.camera != null) {
                        data.preview.update(data.cube);
                    }

                    // キューブプレビューを更新
                    if (data.cubePreview != null && data.tracingManager != null) {
                        data.cubePreview.update(data.tracingManager);
                    }

                    // 壁接近警告をチェック
                    updateWarningBossBar(data);
                }
            }
        }, 1L, 1L); // 1tick遅延、1tickごとに実行
    }

    /**
     * 壁接近警告ボスバーを更新
     *
     * @param data プレイヤーデータ
     */
    private void updateWarningBossBar(PlayerData data) {
        Player player = data.player;
        if (player == null || data.cube == null) {
            return;
        }

        // 警告が必要かチェック
        boolean shouldWarn = data.cube.shouldShowWarning();

        if (shouldWarn) {
            // ボスバーがまだない場合は作成
            if (data.warningBossBar == null) {
                data.warningBossBar = Bukkit.createBossBar(
                        "§c§l⚠ 壁接近！ ⚠",
                        BarColor.RED,
                        BarStyle.SOLID);
                data.warningBossBar.setProgress(0.0);
                data.warningBossBar.addPlayer(player);
            }

            // 距離に応じてボスバーの進行度を更新
            double distance = data.cube.getDistanceToNextWall();
            if (distance >= 0) {
                // 距離3ブロック → 0%、距離0.0ブロック → 100%
                double progress = 1.0 - ((distance - 1) / 3.0);
                progress = Math.max(0.0, Math.min(1.0, progress)); // 0.0～1.0にクランプ
                data.warningBossBar.setProgress(progress);
            }

            // ボスバーを表示
            data.warningBossBar.setVisible(true);
        } else {
            // 警告不要の場合はボスバーを非表示
            if (data.warningBossBar != null) {
                data.warningBossBar.setVisible(false);
            }
        }
    }

    /**
     * ゲームループを停止する
     */
    public void stopGameLoop() {
        if (gameLoopTask != null) {
            gameLoopTask.cancel();
            gameLoopTask = null;
        }
    }

    /**
     * ゲームがアクティブかチェックする
     * 
     * @param player プレイヤーオブジェクト
     * @return ゲームがアクティブな場合はtrue
     */
    public boolean isGameActive(Player player) {
        PlayerData data = playerDataManager.getPlayerData(player);
        return data != null && data.cube != null && !data.isGameOver;
    }

    /**
     * ゲーム開始処理
     *
     * @param player プレイヤー
     * @param pattern ブロックパターン（3x3x3の配列）
     */
    public void startGame(Player player, boolean[][][] pattern) {
        if (playerDataManager.hasPlayerData(player)) {
            player.sendMessage("すでにゲーム中です。");
            return;
        }

        // プレイヤーの位置をブロックグリッドにスナップ
        Location initialLocation = player.getLocation().toCenterLocation();
        initialLocation.setYaw(0f);
        initialLocation.setPitch(0f);

        // カメラの位置に合わせてオフセットを追加
        Location baseLocation = initialLocation.clone().add(0, 0, CubeCamera.CAMERA_DISTANCE_BEHIND);

        // PlayerDataを作成
        PlayerData playerData = playerDataManager.getOrCreatePlayerData(player);

        // 現在のゲームモードを保存
        playerData.originalGameMode = player.getGameMode();

        // アドベンチャーモードに変更
        player.setGameMode(GameMode.ADVENTURE);

        // スコアボード管理を作成して初期化
        playerData.scoreTracker = new GameScoreTracker(player);
        playerData.scoreTracker.initializeScores();

        // キャラのキューブを作成
        playerData.cube = new PlayerCube(player.getWorld(), baseLocation.clone(), pattern, playerData.scoreTracker);

        // カメラを作成してセットアップ
        playerData.camera = new CubeCamera(player.getWorld(), baseLocation.clone(), playerData.cube);
        playerData.camera.setup(player);

        // 穴なぞり管理を作成
        playerData.tracingManager = new HoleTracingManager();

        // プレビュー表示を作成
        playerData.preview = new HolePreview(player.getWorld(), player, playerData.tracingManager, playerData.scoreTracker);

        // キューブプレビュー表示を作成（PlayerCubeのHoleStateを使用）
        playerData.cubePreview = new CubePreview(player.getWorld(), playerData.cube, baseLocation.clone(), plugin);

        // ホットバーのスロットを5番目（インデックス4）に設定
        player.getInventory().setHeldItemSlot(4);

        // 左手（オフハンド）に石のボタンを配布
        ItemStack offHandItem = player.getInventory().getItemInOffHand();
        if (offHandItem != null && offHandItem.getType() != Material.AIR) {
            // 石のボタン以外のアイテムを持っていたらドロップ
            if (offHandItem.getType() != Material.STONE_BUTTON) {
                player.getWorld().dropItem(player.getLocation(), offHandItem);
            }
        }
        player.getInventory().setItemInOffHand(new ItemStack(Material.STONE_BUTTON));

        // ゲーム時間タイマーを初期化（データパック側で使用）
        playerData.scoreTracker.setScore("runhole_game_time", 0);

        // 操作説明やメッセージはデータパック側で表示

        plugin.getLogger().info(player.getName() + "がゲームを開始しました。");
    }

    /**
     * ゲーム終了処理
     * 
     * @param player プレイヤー
     * @param endType ゲーム終了タイプ (GameScoreTracker.END_TYPE_*)
     */
    public void stopGame(Player player, int endType) {
        PlayerData playerData = playerDataManager.removePlayerData(player);

        if (playerData == null) {
            player.sendMessage("ゲーム中ではありません。");
            return;
        }

        // スコアボードを更新
        if (playerData.scoreTracker != null) {
            playerData.scoreTracker.setScore(GameScoreTracker.OBJECTIVE_GAME_STATE, GameScoreTracker.GAME_STATE_GAME_END);
            playerData.scoreTracker.setScore(GameScoreTracker.OBJECTIVE_END_TYPE, endType);
        }

        // カメラをクリーンアップ
        if (playerData.camera != null) {
            playerData.camera.cleanup();
        }

        // キューブを削除
        if (playerData.cube != null) {
            playerData.cube.remove();
        }

        // プレビューをクリーンアップ
        if (playerData.preview != null) {
            playerData.preview.cleanup();
        }

        // キューブプレビューをクリーンアップ
        if (playerData.cubePreview != null) {
            playerData.cubePreview.cleanup();
        }

        // 左手（オフハンド）の石のボタンを削除
        ItemStack offHandItem = player.getInventory().getItemInOffHand();
        if (offHandItem != null && offHandItem.getType() == Material.STONE_BUTTON) {
            player.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
        }

        // ボスバーをクリーンアップ
        if (playerData.warningBossBar != null) {
            playerData.warningBossBar.removeAll();
            playerData.warningBossBar = null;
        }

        // 元のゲームモードに戻す
        if (playerData.originalGameMode != null) {
            player.setGameMode(playerData.originalGameMode);
            plugin.getLogger().info(player.getName() + "のゲームモードを" + playerData.originalGameMode + "に戻しました");
        }

        // スコアボードを0にリセット（データパック側の処理を停止）
        if (playerData.scoreTracker != null) {
            playerData.scoreTracker.setScore(GameScoreTracker.OBJECTIVE_GAME_STATE, GameScoreTracker.GAME_STATE_NOT_PLAYING);
            playerData.scoreTracker.setScore("runhole_stop_request", 0);
            playerData.scoreTracker.setScore("runhole_spectator_timer", 0);
        }

        player.sendMessage("ゲームを終了しました。");
        plugin.getLogger().info(player.getName() + "がゲームを終了しました。");
    }

    /**
     * ゲームオーバー処理（衝突ブロックあり）
     *
     * @param playerData     プレイヤーデータ
     * @param collidedBlocks 衝突したブロックのリスト
     * @param endType        終了タイプ
     */
    public void gameOver(PlayerData playerData, List<CubeBlock> collidedBlocks, int endType) {
        Player player = playerData.player;
        if (player == null) {
            plugin.getLogger().warning("PlayerDataにPlayerが設定されていません");
            return;
        }

        // 既にゲームオーバー処理中の場合は何もしない
        if (playerData.isGameOver) {
            return;
        }

        // ゲームオーバーフラグを立てて重複呼び出しを防ぐ
        playerData.isGameOver = true;
        plugin.getLogger().info(player.getName() + "のゲームオーバー処理を開始");

        // 衝突したブロックに演出を適用
        if (collidedBlocks != null && !collidedBlocks.isEmpty() && playerData.cube != null) {
            for (CubeBlock block : collidedBlocks) {
                // ブロックを赤いガラスに変更
                playerData.cube.changeBlockColor(block, Material.RED_STAINED_GLASS);

                // ブロックの位置を取得
                Location blockLoc = playerData.cube.getBlockDisplayLocation(block);
                if (blockLoc != null) {
                    // パーティクルエフェクト（炎とダメージ）
                    player.getWorld().spawnParticle(Particle.FLAME, blockLoc, 20, 0.3, 0.3, 0.3, 0.05);
                    player.getWorld().spawnParticle(Particle.LAVA, blockLoc, 10, 0.2, 0.2, 0.2, 0);
                    player.getWorld().spawnParticle(Particle.SMOKE, blockLoc, 15, 0.3, 0.3, 0.3, 0.05);

                    // 爆発エフェクト（破壊力なし）
                    player.getWorld().createExplosion(blockLoc, 0.0f, false, false);

                    // 爆発音
                    GameSound.GAME_OVER_EXPLOSION.play(player);
                }
            }
        }

        // すぐにプレイヤーを降車させて、衝突原因を振り返れるようにする
        if (playerData.camera != null) {
            // 椅子の現在位置を取得
            Location dismountLoc = playerData.camera.getEntity().getLocation();

            // プレイヤーを降車
            playerData.camera.eject();

            // プレイヤーを椅子の位置にテレポート（地面に落ちないように）
            player.teleport(dismountLoc);

            // スペクテーターモードに変更（自由に飛び回れる）
            player.setGameMode(GameMode.SPECTATOR);

            plugin.getLogger().info(player.getName() + "を降車させました（スペクテーターモードで振り返り可能）");
        }

        // スコアボード更新（データパックがこれをトリガーにする）
        playerData.scoreTracker.setScore(GameScoreTracker.OBJECTIVE_GAME_STATE,
                                         GameScoreTracker.GAME_STATE_GAME_END);
        playerData.scoreTracker.setScore(GameScoreTracker.OBJECTIVE_END_TYPE, endType);

        // タイトル表示、メッセージ送信、stopGame呼び出しはデータパック側で処理
        // データパック側が3秒後に /rth stop <player> を実行する
    }

    /**
     * 全アクティブなプレイヤーのゲームを終了する
     */
    public void stopAllGames() {
        for (PlayerData playerData : playerDataManager.getAllPlayerData()) {
            if (playerData.player != null) {
                stopGame(playerData.player, GameScoreTracker.END_TYPE_COMMAND_STOP);
            }
        }
    }

}
