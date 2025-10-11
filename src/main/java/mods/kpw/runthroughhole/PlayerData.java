package mods.kpw.runthroughhole;

import org.bukkit.GameMode;
import org.bukkit.Location;

public class PlayerData {
    public PlayerCube cube; // キャラのキューブ
    public CubeCamera camera; // カメラ
    public HolePreview preview; // 穴のプレビュー表示
    public String currentGuide; // 現在表示中のガイド（null = 非表示）
    public boolean isYawOutside; // Yaw方向でGESTURE_THRESHOLD外にいるかどうか
    public boolean isPitchOutside; // Pitch方向でGESTURE_THRESHOLD外にいるかどうか
    public long lastCommandTime; // 最後にコマンドを実行した時刻
    public boolean isGameOver; // ゲームオーバー処理中かどうか
    public GameMode originalGameMode; // ゲーム開始時のゲームモード
    
    // 位置管理
    public Location initialLocation; // ゲーム開始時の初期位置（不変）
    public long lastMoveTime; // 最後に移動した時刻
    
    // 加速機能
    public boolean isSpacePressed; // Spaceキーが押されているかどうか

    public PlayerData() {
        this.currentGuide = null;
        this.isYawOutside = false;
        this.isPitchOutside = false;
        this.lastCommandTime = 0;
        this.lastMoveTime = 0;
        this.isGameOver = false;
        this.isSpacePressed = false;
    }
}

