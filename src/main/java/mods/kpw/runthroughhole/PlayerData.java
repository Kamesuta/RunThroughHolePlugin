package mods.kpw.runthroughhole;

import org.bukkit.Location;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Horse;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class PlayerData {
    public BlockDisplay display;
    public Horse horse;
    public Quaternionf rotation;
    public String currentGuide; // 現在表示中のガイド（null = 非表示）
    public boolean isYawOutside; // Yaw方向でGESTURE_THRESHOLD外にいるかどうか
    public boolean isPitchOutside; // Pitch方向でGESTURE_THRESHOLD外にいるかどうか
    public long lastCommandTime; // 最後にコマンドを実行した時刻
    
    // WASD移動用
    public Location fixedPlayerLocation; // プレイヤーの固定位置
    public Vector3f displayGridPosition; // BlockDisplayのグリッド位置（ブロック単位）
    public long lastMoveTime; // 最後に移動した時刻

    public PlayerData() {
        // 初期視点に合わせてBlockDisplayの回転も初期化 (Z+方向: Yaw 0, Pitch 0)
        this.rotation = new Quaternionf().rotateY((float) Math.toRadians(90.0f));
        this.currentGuide = null;
        this.isYawOutside = false;
        this.isPitchOutside = false;
        this.lastCommandTime = 0;
        this.displayGridPosition = new Vector3f(0, 0, 0);
        this.lastMoveTime = 0;
    }
}

