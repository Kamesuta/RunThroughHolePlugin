package mods.kpw.runthroughhole;

import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Main extends JavaPlugin {

    private final Map<UUID, BlockDisplay> playerDisplays = new HashMap<>();

    @Override
    public void onEnable() {
        getLogger().info("RunThroughHoleプラグインが有効になりました。");

        // コマンドの登録
        getCommand("rth").setExecutor(new RthCommand(this));

        // イベントリスナーの登録
        getServer().getPluginManager().registerEvents(new PlayerGameListener(this), this);
    }

    @Override
    public void onDisable() {
        getLogger().info("RunThroughHoleプラグインが無効になりました。");

        // ゲーム中のプレイヤーを全て終了させる
        for (UUID uuid : playerDisplays.keySet()) {
            Player player = getServer().getPlayer(uuid);
            if (player != null) {
                stopGame(player);
            }
        }
        playerDisplays.clear();
    }

    public Map<UUID, BlockDisplay> getPlayerDisplays() {
        return playerDisplays;
    }

    // ゲーム開始処理
    public void startGame(Player player) {
        if (playerDisplays.containsKey(player.getUniqueId())) {
            player.sendMessage("すでにゲーム中です。");
            return;
        }

        // プレイヤーを透明化
        player.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0, false, false));

        // BlockDisplayをスポーン
        BlockDisplay display = player.getWorld().spawn(player.getLocation().add(0, 2, 0), BlockDisplay.class);
        display.setBlock(org.bukkit.Material.STONE.createBlockData());
        playerDisplays.put(player.getUniqueId(), display);

        player.sendMessage("ゲームを開始しました！");
        getLogger().info(player.getName() + "がゲームを開始しました。");
    }

    // ゲーム終了処理
    public void stopGame(Player player) {
        if (!playerDisplays.containsKey(player.getUniqueId())) {
            player.sendMessage("ゲーム中ではありません。");
            return;
        }

        // BlockDisplayを削除
        BlockDisplay display = playerDisplays.remove(player.getUniqueId());
        if (display != null) {
            display.remove();
        }

        // プレイヤーの透明化を解除
        player.removePotionEffect(org.bukkit.potion.PotionEffectType.INVISIBILITY);

        player.sendMessage("ゲームを終了しました。");
        getLogger().info(player.getName() + "がゲームを終了しました。");
    }
}
