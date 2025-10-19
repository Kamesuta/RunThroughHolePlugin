package mods.kpw.runthroughhole;

import org.bukkit.plugin.java.JavaPlugin;
import java.util.logging.Logger;

import mods.kpw.runthroughhole.player.PlayerDataManager;
import mods.kpw.runthroughhole.player.PlayerGameListener;
import mods.kpw.runthroughhole.game.GameManager;

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

    // マネージャークラスへのアクセサメソッド
    public PlayerDataManager getPlayerDataManager() {
        return playerDataManager;
    }

    public GameManager getGameManager() {
        return gameManager;
    }

    public PlayerGameListener getGameListener() {
        return gameListener;
    }
}
