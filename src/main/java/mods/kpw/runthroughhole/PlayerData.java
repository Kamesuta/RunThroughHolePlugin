package mods.kpw.runthroughhole;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

public class PlayerData {
    public PlayerCube cube; // キャラのキューブ
    public Entity entity;
    public String currentGuide; // 現在表示中のガイド（null = 非表示）
    public boolean isYawOutside; // Yaw方向でGESTURE_THRESHOLD外にいるかどうか
    public boolean isPitchOutside; // Pitch方向でGESTURE_THRESHOLD外にいるかどうか
    public long lastCommandTime; // 最後にコマンドを実行した時刻
    public boolean isGameOver; // ゲームオーバー処理中かどうか
    
    // 位置管理
    public Location initialLocation; // ゲーム開始時の初期位置（不変）
    public long lastMoveTime; // 最後に移動した時刻

    public PlayerData() {
        this.currentGuide = null;
        this.isYawOutside = false;
        this.isPitchOutside = false;
        this.lastCommandTime = 0;
        this.lastMoveTime = 0;
        this.isGameOver = false;
    }
}

