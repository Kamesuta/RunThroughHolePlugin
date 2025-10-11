package mods.kpw.runthroughhole;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Bat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.joml.Vector3f;

import java.util.List;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;

import io.papermc.paper.entity.TeleportFlag;

import java.time.Duration;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

public class Main extends JavaPlugin {

    private final Map<UUID, PlayerData> playerDataMap = new HashMap<>();
    private PlayerGameListener gameListener;

    public static Logger logger;

    @Override
    public void onEnable() {
        logger = getLogger();
        logger.info("RunThroughHoleプラグインが有効になりました。");

        // コマンドの登録
        getCommand("rth").setExecutor(new RthCommand(this));

        // イベントリスナーの登録
        gameListener = new PlayerGameListener(this);
        getServer().getPluginManager().registerEvents(gameListener, this);
        
        // 自動前進タスクを開始（1tickごと）
        getServer().getScheduler().runTaskTimer(this, () -> {
            for (PlayerData data : playerDataMap.values()) {
                if (data.cube != null && !data.isGameOver) {
                    // キューブを前進
                    data.cube.autoForward();
                    
                    // 衝突チェック
                    if (data.cube.checkCollision()) {
                        // プレイヤーを取得
                        Player player = getPlayerFromData(data);
                        if (player != null) {
                            // 衝突したブロックを取得
                            List<CubeBlock> collidedBlocks = data.cube.getCollidedBlocks();
                            gameOver(player, "ブロックに衝突しました！", collidedBlocks);
                        }
                        continue;
                    }
                    
                    // カメラ（椅子）をキューブから10マス後ろに追従
                    if (data.entity != null && data.initialLocation != null) {
                        updateCameraPosition(data);
                    }
                }
            }
        }, 1L, 1L); // 1tick遅延、1tickごとに実行
    }

    @Override
    public void onDisable() {
        getLogger().info("RunThroughHoleプラグインが無効になりました。");

        // ゲーム中のプレイヤーを全て終了させる
        for (UUID uuid : playerDataMap.keySet()) {
            Player player = getServer().getPlayer(uuid);
            if (player != null) {
                stopGame(player);
            }
        }
        playerDataMap.clear();
    }

    public Map<UUID, PlayerData> getPlayerDataMap() {
        return playerDataMap;
    }
    
    public PlayerData getPlayerData(UUID playerId) {
        return playerDataMap.get(playerId);
    }
    
    public PlayerData getOrCreatePlayerData(UUID playerId) {
        return playerDataMap.computeIfAbsent(playerId, k -> new PlayerData());
    }
    
    public PlayerGameListener getGameListener() {
        return gameListener;
    }
    
    // PlayerDataからPlayerを取得するヘルパーメソッド
    private Player getPlayerFromData(PlayerData data) {
        if (data.entity != null && !data.entity.getPassengers().isEmpty()) {
            var passenger = data.entity.getPassengers().get(0);
            if (passenger instanceof Player) {
                return (Player) passenger;
            }
        }
        return null;
    }
    
    // ゲームオーバー処理（衝突ブロックあり）
    public void gameOver(Player player, String reason, List<CubeBlock> collidedBlocks) {
        UUID playerId = player.getUniqueId();
        PlayerData data = playerDataMap.get(playerId);
        
        // 既にゲームオーバー処理中の場合は何もしない
        if (data == null || data.isGameOver) {
            return;
        }
        
        // ゲームオーバーフラグを立てて重複呼び出しを防ぐ
        data.isGameOver = true;
        getLogger().info(player.getName() + "のゲームオーバー処理を開始");
        
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
        
        // 少し遅延してゲームを終了
        getServer().getScheduler().runTaskLater(this, () -> {
            stopGame(player);
        }, 60L); // 3秒後（60tick）
    }
    
    // ゲームオーバー処理（衝突ブロックなし - 互換性のため）
    public void gameOver(Player player, String reason) {
        gameOver(player, reason, null);
    }
    
    // カメラ位置を更新（キューブから10マス後ろ）
    private void updateCameraPosition(PlayerData data) {
        Vector3f cubeGridPos = data.cube.gridPosition;
        float cubeForwardProgress = data.cube.getForwardProgress();
        
        // キューブのZ位置から10マス後ろ
        double cameraZ = cubeGridPos.z + cubeForwardProgress - 10.0;
        
        // 初期位置を基準に新しい位置を計算
        Location newCameraLoc = data.initialLocation.clone();
        newCameraLoc.add(0, 0, cameraZ);
        newCameraLoc.setYaw(0f);
        newCameraLoc.setPitch(0f);
        
        // プレイヤーを取得
        Player player = null;
        if (!data.entity.getPassengers().isEmpty()) {
            var passenger = data.entity.getPassengers().get(0);
            if (passenger instanceof Player) {
                player = (Player) passenger;
            }
        }
        
        // 椅子をテレポート
        if (player != null) {
            data.entity.teleport(newCameraLoc, TeleportFlag.EntityState.RETAIN_PASSENGERS);
        } else {
            // プレイヤーがいない場合は普通にテレポート
            data.entity.teleport(newCameraLoc);
        }
    }

    // ゲーム開始処理
    public void startGame(Player player) {
        UUID playerId = player.getUniqueId();
        if (playerDataMap.containsKey(playerId)) {
            player.sendMessage("すでにゲーム中です。");
            return;
        }

        Location loc = player.getLocation();
        loc.setYaw(0f);
        loc.setPitch(0f);

        // PlayerDataを作成
        PlayerData data = getOrCreatePlayerData(playerId);
        data.initialLocation = loc.clone();

        // 透明でNoAIな椅子をスポーン
        Bat entity = (Bat) player.getWorld().spawnEntity(loc, EntityType.BAT);
        entity.setInvulnerable(true);
        entity.setGravity(false);
        entity.setSilent(true);
        entity.setAI(false); // AIを無効化して完全に動かなくする
        entity.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0, false, false));
        
        // プレイヤーを椅子に乗せる
        entity.addPassenger(player);
        data.entity = entity;

        // ホットバーのスロットを5番目（インデックス4）に設定
        player.getInventory().setHeldItemSlot(4);

        // キャラのキューブを作成
        data.cube = new PlayerCube(player.getWorld(), loc.clone());

        player.sendMessage("ゲームを開始しました！WASDで上下左右に移動できます。");
        getLogger().info(player.getName() + "がゲームを開始しました。");
    }

    // ゲーム終了処理
    public void stopGame(Player player) {
        UUID playerId = player.getUniqueId();
        PlayerData data = playerDataMap.remove(playerId);
        
        if (data == null) {
            player.sendMessage("ゲーム中ではありません。");
            return;
        }

        // 椅子から降りる
        Entity entity = data.entity;
        if (entity != null) {
            entity.eject();
            entity.remove();
        }

        // キューブを削除
        if (data.cube != null) {
            data.cube.remove();
        }

        player.sendMessage("ゲームを終了しました。");
        getLogger().info(player.getName() + "がゲームを終了しました。");
    }
}
