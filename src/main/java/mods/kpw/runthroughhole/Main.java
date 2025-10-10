package mods.kpw.runthroughhole;

import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.joml.Vector3f;

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
                if (data.cube != null) {
                    // キューブを前進
                    data.cube.autoForward();
                    
                    // カメラ（馬）をキューブから10マス後ろに追従
                    if (data.horse != null && data.initialLocation != null) {
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
        if (!data.horse.getPassengers().isEmpty()) {
            var passenger = data.horse.getPassengers().get(0);
            if (passenger instanceof Player) {
                player = (Player) passenger;
            }
        }
        
        // プレイヤーを一旦降ろして、馬をテレポート、再度乗せる
        if (player != null) {
            data.horse.eject();
            data.horse.teleport(newCameraLoc);
            data.horse.addPassenger(player);
        } else {
            // プレイヤーがいない場合は普通にテレポート
            data.horse.teleport(newCameraLoc);
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

        // 透明でNoAIな馬をスポーン
        Horse horse = (Horse) player.getWorld().spawnEntity(loc, EntityType.HORSE);
        horse.setInvulnerable(true);
        horse.setGravity(false);
        horse.setSilent(true);
        horse.setAI(false); // AIを無効化して完全に動かなくする
        horse.setTamed(true); // 飼いならす
        horse.setAdult(); // 大人の馬
        horse.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0, false, false));
        
        // プレイヤーを馬に乗せる
        horse.addPassenger(player);
        data.horse = horse;

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

        // 馬から降りる
        Horse horse = data.horse;
        if (horse != null) {
            horse.eject();
            horse.remove();
        }

        // キューブを削除
        if (data.cube != null) {
            data.cube.remove();
        }

        player.sendMessage("ゲームを終了しました。");
        getLogger().info(player.getName() + "がゲームを終了しました。");
    }
}
