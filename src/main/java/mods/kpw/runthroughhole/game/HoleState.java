package mods.kpw.runthroughhole.game;

import org.bukkit.Location;

/**
 * 穴通過状態を管理するクラス
 * CubeCameraとHolePreviewでそれぞれ独立したインスタンスを持つ
 */
public class HoleState {
    // 状態管理
    private boolean isInHole = false;
    private boolean prevIsInHole = false;
    private Location lastHoleLocation = null;
    private static final double HOLE_PASS_MARGIN = 1.0;
    
    /**
     * 現在穴の中にいるかどうかを取得
     * @return 現在穴の中にいるかどうか
     */
    public boolean isInHole() {
        return isInHole;
    }
    
    /**
     * 最後に検出した穴の位置を取得
     * @return 最後の穴位置
     */
    public Location getLastHoleLocation() {
        return lastHoleLocation;
    }
    
    /**
     * 穴通過状態が変化したかどうかを取得
     * @return 前回から状態が変化した場合true
     */
    public boolean hasHoleStateChanged() {
        return isInHole != prevIsInHole;
    }
    
    /**
     * 穴通過状態を更新
     * @param holeLocation 検出した穴の位置（nullの場合は穴なし）
     * @param targetZ 判定に使用するZ座標（カメラ座標またはキューブ座標）
     */
    public void updateHoleStatus(Location holeLocation, double targetZ) {
        // 前回の状態を保存
        prevIsInHole = isInHole;
        
        if (holeLocation != null) {
            // 穴が見つかった場合
            if (!isInHole) {
                // 初めて穴を検出した
                isInHole = true;
            }
            // 穴を検出し続けている間、lastHoleLocationを更新し続ける（長いトンネル対応）
            lastHoleLocation = holeLocation.clone();
        } else {
            // 穴が見つからない場合
            if (isInHole) {
                // ターゲットが穴のZ座標+余裕マージンを超えたかチェック
                if (lastHoleLocation != null && targetZ > lastHoleLocation.getZ() + HOLE_PASS_MARGIN) {
                    // ターゲットが穴を十分通過した→穴モードを解除
                    isInHole = false;
                }
            }
        }
    }
}
