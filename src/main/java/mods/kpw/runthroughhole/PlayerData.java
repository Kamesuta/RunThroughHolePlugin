package mods.kpw.runthroughhole;

import org.bukkit.Location;
import org.bukkit.entity.Horse;

public class PlayerData {
    public PlayerCube cube; // キャラのキューブ
    public Horse horse;
    public String currentGuide; // 現在表示中のガイド（null = 非表示）
    public boolean isYawOutside; // Yaw方向でGESTURE_THRESHOLD外にいるかどうか
    public boolean isPitchOutside; // Pitch方向でGESTURE_THRESHOLD外にいるかどうか
    public long lastCommandTime; // 最後にコマンドを実行した時刻
    
    // 位置管理
    public Location initialLocation; // ゲーム開始時の初期位置（不変）
    public long lastMoveTime; // 最後に移動した時刻

    public PlayerData() {
        this.currentGuide = null;
        this.isYawOutside = false;
        this.isPitchOutside = false;
        this.lastCommandTime = 0;
        this.lastMoveTime = 0;
    }
}

