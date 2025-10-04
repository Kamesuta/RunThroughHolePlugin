package mods.kpw.runthroughhole;

import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

public class PlayerGameListener implements Listener {

    private final Main plugin;

    public PlayerGameListener(Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (plugin.getPlayerDisplays().containsKey(player.getUniqueId())) {
            BlockDisplay display = plugin.getPlayerDisplays().get(player.getUniqueId());

            // プレイヤーの視点 (Yaw/Pitch) を取得
            float yaw = player.getLocation().getYaw();
            float pitch = player.getLocation().getPitch();

            // BlockDisplayのTransformationを更新して回転を同期
            Transformation transformation = display.getTransformation();

            // Yaw (Y軸回転)
            float yawRadians = (float) Math.toRadians(-yaw);
            // Pitch (X軸回転)
            float pitchRadians = (float) Math.toRadians(-pitch);

            // 回転を適用
            transformation.getLeftRotation().set(new AxisAngle4f(yawRadians, 0, 1, 0)); // Y軸回転
            transformation.getRightRotation().set(new AxisAngle4f(pitchRadians, 1, 0, 0)); // X軸回転

            display.setTransformation(transformation);

            // BlockDisplayの位置をプレイヤーの頭上に同期
            display.teleport(player.getLocation().add(0, 2, 0));
        }
    }
}
