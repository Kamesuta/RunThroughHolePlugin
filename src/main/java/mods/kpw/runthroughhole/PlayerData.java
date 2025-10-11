package mods.kpw.runthroughhole;

import org.bukkit.GameMode;
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
    public GameMode originalGameMode; // ゲーム開始時のゲームモード
    
    // 位置管理
    public Location initialLocation; // ゲーム開始時の初期位置（不変）
    public long lastMoveTime; // 最後に移動した時刻
    
    // カメラの状態管理（XY平面のみ、Z座標は固定）
    public double cameraTargetX; // カメラの目標X位置（相対座標）
    public double cameraTargetY; // カメラの目標Y位置（相対座標）
    public double cameraCurrentX; // カメラの現在X位置（相対座標）
    public double cameraCurrentY; // カメラの現在Y位置（相対座標）
    public boolean isInHole; // 現在穴の中にいるかどうか
    public Location lastHoleLocation; // 最後に検出した穴の位置

    public PlayerData() {
        this.currentGuide = null;
        this.isYawOutside = false;
        this.isPitchOutside = false;
        this.lastCommandTime = 0;
        this.lastMoveTime = 0;
        this.isGameOver = false;
        this.cameraTargetX = 0.0; // デフォルトはキューブと同じX位置
        this.cameraTargetY = 2.0; // デフォルトはキューブより2マス上
        this.cameraCurrentX = 0.0;
        this.cameraCurrentY = 2.0; // 開始時から2マス上
        this.isInHole = false;
        this.lastHoleLocation = null;
    }
}

