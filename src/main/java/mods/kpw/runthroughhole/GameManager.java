package mods.kpw.runthroughhole;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
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
import java.util.UUID;

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
    }
    
    /**
     * ゲームループを開始する
     */
    public void startGameLoop() {
        // 自動前進タスクを開始（1tickごと）
        gameLoopTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (PlayerData data : playerDataManager.getAllPlayerData()) {
                if (data.cube != null && !data.isGameOver) {
                    // キューブを前進
                    data.cube.autoForward();
                    data.cube.handleContinuousBoosting(data.preview);
                    
                    // 衝突チェック（Streamで衝突ブロックを取得）
                    List<CubeBlock> collidedBlocks = data.cube.checkCollision()
                        .collect(Collectors.toList());
                    
                    if (!collidedBlocks.isEmpty()) {
                        // プレイヤーを取得
                        Player player = data.camera != null ? data.camera.getPlayer() : null;
                        if (player != null) {
                            gameOver(player, "ブロックに衝突しました！", collidedBlocks);
                        }
                        continue;
                    }
                    
                    // カメラを更新
                    if (data.camera != null) {
                        data.camera.update();
                    }
                    
                    // プレビューを更新
                    if (data.preview != null && data.camera != null) {
                        Player player = data.camera.getPlayer();
                        if (player != null) {
                            data.preview.update(data.cube, player);
                        }
                    }
                }
            }
        }, 1L, 1L); // 1tick遅延、1tickごとに実行
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
     * @param playerId プレイヤーのUUID
     * @return ゲームがアクティブな場合はtrue
     */
    public boolean isGameActive(UUID playerId) {
        PlayerData data = playerDataManager.getPlayerData(playerId);
        return data != null && data.cube != null && !data.isGameOver;
    }
    
    /**
     * ゲーム開始処理
     * @param player プレイヤー
     */
    public void startGame(Player player) {
        UUID playerId = player.getUniqueId();
        if (playerDataManager.hasPlayerData(playerId)) {
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
        PlayerData data = playerDataManager.getOrCreatePlayerData(playerId);
        
        // 現在のゲームモードを保存
        data.originalGameMode = player.getGameMode();
        
        // アドベンチャーモードに変更
        player.setGameMode(GameMode.ADVENTURE);

        // キャラのキューブを作成
        data.cube = new PlayerCube(player.getWorld(), baseLocation.clone());
        
        // カメラを作成してセットアップ
        data.camera = new CubeCamera(player.getWorld(), baseLocation.clone(), data.cube);
        data.camera.setup(player);
        
        // プレビュー表示を作成
        data.preview = new HolePreview(player.getWorld(), (Main) plugin);

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

        // 操作説明を表示
        player.sendMessage("§6§l========== 穴抜けゲーム開始 ==========");
        player.sendMessage("§c§l目標: §f壁に当たらないように進み続けよう！");
        player.sendMessage("§e§l【移動操作】");
        player.sendMessage("§a  W/A/S/D §f- 上下左右に移動");
        player.sendMessage("§a  Space §f- 加速（長押し可能）");
        player.sendMessage("§e§l【回転操作】");
        player.sendMessage("§a  視点を上下左右に動かす §f- キューブをX/Y回転");
        player.sendMessage("§a  左右クリック §f- キューブをZ回転");
        player.sendMessage("§6§l=====================================");
        
        plugin.getLogger().info(player.getName() + "がゲームを開始しました。");
    }

    /**
     * ゲーム終了処理
     * @param player プレイヤー
     */
    public void stopGame(Player player) {
        UUID playerId = player.getUniqueId();
        PlayerData data = playerDataManager.removePlayerData(playerId);
        
        if (data == null) {
            player.sendMessage("ゲーム中ではありません。");
            return;
        }

        // カメラをクリーンアップ
        if (data.camera != null) {
            data.camera.cleanup();
        }

        // キューブを削除
        if (data.cube != null) {
            data.cube.remove();
        }
        
        // プレビューをクリーンアップ
        if (data.preview != null) {
            data.preview.cleanup();
        }
        
        // 左手（オフハンド）の石のボタンを削除
        ItemStack offHandItem = player.getInventory().getItemInOffHand();
        if (offHandItem != null && offHandItem.getType() == Material.STONE_BUTTON) {
            player.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
        }
        
        // 元のゲームモードに戻す
        if (data.originalGameMode != null) {
            player.setGameMode(data.originalGameMode);
            plugin.getLogger().info(player.getName() + "のゲームモードを" + data.originalGameMode + "に戻しました");
        }

        player.sendMessage("ゲームを終了しました。");
        plugin.getLogger().info(player.getName() + "がゲームを終了しました。");
    }
    
    /**
     * ゲームオーバー処理（衝突ブロックあり）
     * @param player プレイヤー
     * @param reason ゲームオーバーの理由
     * @param collidedBlocks 衝突したブロックのリスト
     */
    public void gameOver(Player player, String reason, List<CubeBlock> collidedBlocks) {
        UUID playerId = player.getUniqueId();
        PlayerData data = playerDataManager.getPlayerData(playerId);
        
        // 既にゲームオーバー処理中の場合は何もしない
        if (data == null || data.isGameOver) {
            return;
        }
        
        // ゲームオーバーフラグを立てて重複呼び出しを防ぐ
        data.isGameOver = true;
        plugin.getLogger().info(player.getName() + "のゲームオーバー処理を開始");
        
        // 衝突したブロックに演出を適用
        if (collidedBlocks != null && !collidedBlocks.isEmpty() && data.cube != null) {
            for (CubeBlock block : collidedBlocks) {
                // ブロックを赤いガラスに変更
                data.cube.changeBlockColor(block, Material.RED_STAINED_GLASS);
                
                // ブロックの位置を取得
                Location blockLoc = data.cube.getBlockDisplayLocation(block);
                if (blockLoc != null) {
                    // パーティクルエフェクト（炎とダメージ）
                    player.getWorld().spawnParticle(Particle.FLAME, blockLoc, 20, 0.3, 0.3, 0.3, 0.05);
                    player.getWorld().spawnParticle(Particle.LAVA, blockLoc, 10, 0.2, 0.2, 0.2, 0);
                    player.getWorld().spawnParticle(Particle.SMOKE, blockLoc, 15, 0.3, 0.3, 0.3, 0.05);
                    
                    // 爆発エフェクト（破壊力なし）
                    player.getWorld().createExplosion(blockLoc, 0.0f, false, false);
                    
                    // 爆発音
                    player.getWorld().playSound(blockLoc, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f);
                }
            }
        }
        
        // タイトルに「GAME OVER」を表示
        Title title = Title.title(
            Component.text("GAME OVER", NamedTextColor.RED),
            Component.text(reason, NamedTextColor.YELLOW),
            Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofMillis(500))
        );
        player.showTitle(title);
        
        // メッセージを送信
        player.sendMessage(Component.text("ゲームオーバー: " + reason, NamedTextColor.RED));
        
        // すぐにプレイヤーを降車させて、衝突原因を振り返れるようにする
        if (data.camera != null) {
            // 椅子の現在位置を取得
            Location dismountLoc = data.camera.getEntity().getLocation();
            
            // プレイヤーを降車
            data.camera.eject();
            
            // プレイヤーを椅子の位置にテレポート（地面に落ちないように）
            player.teleport(dismountLoc);
            
            // スペクテーターモードに変更（自由に飛び回れる）
            player.setGameMode(GameMode.SPECTATOR);
            
            plugin.getLogger().info(player.getName() + "を降車させました（スペクテーターモードで振り返り可能）");
        }
        
        // 少し遅延してゲームを終了
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            stopGame(player);
        }, 60L); // 3秒後（60tick）
    }
    
    /**
     * ゲームオーバー処理（衝突ブロックなし - 互換性のため）
     * @param player プレイヤー
     * @param reason ゲームオーバーの理由
     */
    public void gameOver(Player player, String reason) {
        gameOver(player, reason, null);
    }
    
    /**
     * 全アクティブなプレイヤーのゲームを終了する
     */
    public void stopAllGames() {
        for (UUID playerId : playerDataManager.getPlayerDataMap().keySet()) {
            Player player = plugin.getServer().getPlayer(playerId);
            if (player != null) {
                stopGame(player);
            }
        }
    }
}
