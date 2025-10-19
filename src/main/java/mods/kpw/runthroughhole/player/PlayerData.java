package mods.kpw.runthroughhole.player;

import org.bukkit.GameMode;
import org.bukkit.Location;

import mods.kpw.runthroughhole.game.PlayerCube;
import mods.kpw.runthroughhole.game.CubeCamera;
import mods.kpw.runthroughhole.game.HolePreview;

public class PlayerData {
    public PlayerCube cube; // キャラのキューブ
    public CubeCamera camera; // カメラ
    public HolePreview preview; // 穴のプレビュー表示
    public String currentGuide; // 現在表示中のガイド（null = 非表示）
    public boolean isYawOutside; // Yaw方向でGESTURE_THRESHOLD外にいるかどうか
    public boolean isPitchOutside; // Pitch方向でGESTURE_THRESHOLD外にいるかどうか
    public int lastCommandTick; // 最後にコマンドを実行したtick
    public boolean isGameOver; // ゲームオーバー処理中かどうか
    public GameMode originalGameMode; // ゲーム開始時のゲームモード
    
    // 位置管理
    public Location initialLocation; // ゲーム開始時の初期位置（不変）
    public int lastMoveTick; // 最後に移動したtick
    
    // 加速機能
    public boolean isSpacePressed; // Spaceキーが押されているかどうか
    
    // 視線追従機能
    public float currentTargetYaw; // 現在追従している目標Yaw
    public float currentTargetPitch; // 現在追従している目標Pitch

    public PlayerData() {
        this.currentGuide = null;
        this.isYawOutside = false;
        this.isPitchOutside = false;
        this.lastCommandTick = 0;
        this.lastMoveTick = 0;
        this.isGameOver = false;
        this.isSpacePressed = false;
        
        // 視線追従の初期値（デフォルトは前方を向く）
        this.currentTargetYaw = 0.0f;
        this.currentTargetPitch = 0.0f;
    }
}

