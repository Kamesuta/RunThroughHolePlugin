package mods.kpw.runthroughhole.player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Collection;

/**
 * プレイヤーデータの管理を行うクラス
 * MainクラスからPlayerData管理機能を分離
 */
public class PlayerDataManager {
    
    private final Map<UUID, PlayerData> playerDataMap = new HashMap<>();
    
    /**
     * プレイヤーデータを取得する
     * @param playerId プレイヤーのUUID
     * @return PlayerData、存在しない場合はnull
     */
    public PlayerData getPlayerData(UUID playerId) {
        return playerDataMap.get(playerId);
    }
    
    /**
     * プレイヤーデータを取得する。存在しない場合は新規作成する
     * @param playerId プレイヤーのUUID
     * @return PlayerData
     */
    public PlayerData getOrCreatePlayerData(UUID playerId) {
        return playerDataMap.computeIfAbsent(playerId, k -> new PlayerData());
    }
    
    /**
     * プレイヤーデータを追加する
     * @param playerId プレイヤーのUUID
     * @param data PlayerData
     */
    public void addPlayerData(UUID playerId, PlayerData data) {
        playerDataMap.put(playerId, data);
    }
    
    /**
     * プレイヤーデータを削除する
     * @param playerId プレイヤーのUUID
     * @return 削除されたPlayerData、存在しない場合はnull
     */
    public PlayerData removePlayerData(UUID playerId) {
        return playerDataMap.remove(playerId);
    }
    
    /**
     * プレイヤーデータが存在するかチェックする
     * @param playerId プレイヤーのUUID
     * @return 存在する場合はtrue
     */
    public boolean hasPlayerData(UUID playerId) {
        return playerDataMap.containsKey(playerId);
    }
    
    /**
     * 全プレイヤーデータのMapを取得する
     * @return PlayerDataのMap
     */
    public Map<UUID, PlayerData> getPlayerDataMap() {
        return playerDataMap;
    }
    
    /**
     * 全プレイヤーデータのコレクションを取得する
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
     * @return プレイヤー数
     */
    public int getPlayerCount() {
        return playerDataMap.size();
    }
}
