package mods.kpw.runthroughhole;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

public class Main extends JavaPlugin {

    private PlayerDataManager playerDataManager;
    private GameManager gameManager;
    private PlayerGameListener gameListener;

    public static Logger logger;

    @Override
    public void onEnable() {
        logger = getLogger();
        logger.info("RunThroughHoleプラグインが有効になりました。");

        // マネージャークラスの初期化
        playerDataManager = new PlayerDataManager();
        gameManager = new GameManager(this, playerDataManager);

        // コマンドの登録
        RunHoleCommand rthCommand = new RunHoleCommand(this);
        getCommand("runhole").setExecutor(rthCommand);
        getCommand("runhole").setTabCompleter(rthCommand);

        // イベントリスナーの登録
        gameListener = new PlayerGameListener(this);
        getServer().getPluginManager().registerEvents(gameListener, this);
        
        // ゲームループを開始
        gameManager.startGameLoop();
    }

    @Override
    public void onDisable() {
        getLogger().info("RunThroughHoleプラグインが無効になりました。");

        // ゲームループを停止
        if (gameManager != null) {
            gameManager.stopGameLoop();
        }

        // ゲーム中のプレイヤーを全て終了させる
        if (gameManager != null) {
            gameManager.stopAllGames();
        }
        
        // プレイヤーデータをクリア
        if (playerDataManager != null) {
            playerDataManager.clearAllPlayerData();
        }
    }

    // マネージャークラスへのアクセサメソッド（後方互換性のため）
    public PlayerDataManager getPlayerDataManager() {
        return playerDataManager;
    }
    
    public GameManager getGameManager() {
        return gameManager;
    }
    
    public PlayerGameListener getGameListener() {
        return gameListener;
    }
    
    // 後方互換性のためのメソッド
    public Map<UUID, PlayerData> getPlayerDataMap() {
        return playerDataManager != null ? playerDataManager.getPlayerDataMap() : null;
    }
    
    public PlayerData getPlayerData(UUID playerId) {
        return playerDataManager != null ? playerDataManager.getPlayerData(playerId) : null;
    }
    
    public PlayerData getOrCreatePlayerData(UUID playerId) {
        return playerDataManager != null ? playerDataManager.getOrCreatePlayerData(playerId) : null;
    }
    
    public void startGame(Player player) {
        if (gameManager != null) {
            gameManager.startGame(player);
        }
    }
    
    public void stopGame(Player player) {
        if (gameManager != null) {
            gameManager.stopGame(player);
        }
    }
    
    public void gameOver(Player player, String reason) {
        if (gameManager != null) {
            gameManager.gameOver(player, reason);
        }
    }
}
