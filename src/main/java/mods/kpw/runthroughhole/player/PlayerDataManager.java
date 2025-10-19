package mods.kpw.runthroughhole.player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Collection;
import org.bukkit.entity.Player;

/**
 * プレイヤーデータの管理を行うクラス
 * MainクラスからPlayerData管理機能を分離
 */
public class PlayerDataManager {
    // ProtocolLib関連のスレッドからアクセスされるため、ConcurrentHashMapを使用
    private final Map<UUID, PlayerData> playerDataMap = new ConcurrentHashMap<>();

    /**
     * プレイヤーデータを取得する
     * 
     * @param player プレイヤーオブジェクト
     * @return PlayerData、存在しない場合はnull
     */
    public PlayerData getPlayerData(Player player) {
        PlayerData data = playerDataMap.get(player.getUniqueId());
        if (data != null) {
            data.player = player; // Playerオブジェクトを更新（リログ対応）
        }
        return data;
    }

    /**
     * プレイヤーデータを取得する。存在しない場合は新規作成する
     * 
     * @param player プレイヤーオブジェクト
     * @return PlayerData
     */
    public PlayerData getOrCreatePlayerData(Player player) {
        PlayerData data = playerDataMap.computeIfAbsent(player.getUniqueId(), k -> {
            PlayerData newData = new PlayerData(player);
            return newData;
        });
        if (data != null) {
            data.player = player; // Playerオブジェクトを更新（リログ対応）
        }
        return data;
    }

    /**
     * プレイヤーデータを追加する
     * 
     * @param player プレイヤーオブジェクト
     * @param data   PlayerData
     */
    public void addPlayerData(Player player, PlayerData data) {
        data.player = player; // Playerオブジェクトを設定
        playerDataMap.put(player.getUniqueId(), data);
    }

    /**
     * プレイヤーデータを削除する
     * 
     * @param player プレイヤーオブジェクト
     * @return 削除されたPlayerData、存在しない場合はnull
     */
    public PlayerData removePlayerData(Player player) {
        return playerDataMap.remove(player.getUniqueId());
    }

    /**
     * プレイヤーデータが存在するかチェックする
     * 
     * @param player プレイヤーオブジェクト
     * @return 存在する場合はtrue
     */
    public boolean hasPlayerData(Player player) {
        return playerDataMap.containsKey(player.getUniqueId());
    }

    /**
     * 全プレイヤーデータのMapを取得する
     * 
     * @return PlayerDataのMap
     */
    public Map<UUID, PlayerData> getPlayerDataMap() {
        return playerDataMap;
    }

    /**
     * 全プレイヤーデータのコレクションを取得する
     * 
     * @return PlayerDataのコレクション
     */
    public Collection<PlayerData> getAllPlayerData() {
        return playerDataMap.values();
    }

    /**
     * 全プレイヤーデータをクリアする
     */
    public void clearAllPlayerData() {
        playerDataMap.clear();
    }

    /**
     * 現在管理されているプレイヤー数を取得する
     * 
     * @return プレイヤー数
     */
    public int getPlayerCount() {
        return playerDataMap.size();
    }
}
