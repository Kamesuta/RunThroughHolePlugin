package mods.kpw.runthroughhole.game;

import org.joml.Vector2i;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 穴なぞり状態を管理するクラス（純粋な状態管理のみ）
 *
 * 責任:
 * - 現在の壁の穴位置を記録
 * - なぞった穴の追跡
 * - 完了状態の管理
 *
 * エフェクトや音の再生はHolePreviewが行う
 */
public class HoleTracingManager {
    // 現在の壁のZ座標
    private Integer currentWallZ;

    // 現在の壁の全穴位置（壁上の2次元座標）
    private Set<Vector2i> allHoles;

    // なぞった穴の位置（壁上の2次元座標）
    private Set<Vector2i> tracedHoles;

    public HoleTracingManager() {
        this.currentWallZ = null;
        this.allHoles = new HashSet<>();
        this.tracedHoles = new HashSet<>();
    }

    /**
     * 現在の壁を設定
     *
     * @param wallZ 壁のZ座標
     * @param holes 壁の全穴位置（2次元座標）
     */
    public void setCurrentWall(int wallZ, Set<Vector2i> holes) {
        this.currentWallZ = wallZ;
        this.allHoles = new HashSet<>(holes);
        this.tracedHoles = new HashSet<>();
    }

    /**
     * 指定した壁が現在の壁か
     *
     * @param wallZ 壁のZ座標
     * @return 現在の壁の場合true
     */
    public boolean isCurrentWall(int wallZ) {
        return currentWallZ != null && currentWallZ == wallZ;
    }

    /**
     * 穴をなぞったとマーク
     *
     * @param holePos 穴の位置（壁上の2次元座標）
     */
    public void markHoleTraced(Vector2i holePos) {
        tracedHoles.add(holePos);
    }

    /**
     * すべての穴をなぞったか
     *
     * @return 完了している場合true
     */
    public boolean isCompleted() {
        if (allHoles.isEmpty()) {
            return false;
        }
        return tracedHoles.size() == allHoles.size() && allHoles.containsAll(tracedHoles);
    }

    /**
     * 全穴の位置を取得
     *
     * @return 全穴の2次元座標のセット
     */
    public Set<Vector2i> getAllHoles() {
        return Collections.unmodifiableSet(allHoles);
    }

    /**
     * なぞった穴の位置を取得
     *
     * @return なぞった穴の2次元座標のセット
     */
    public Set<Vector2i> getTracedHoles() {
        return Collections.unmodifiableSet(tracedHoles);
    }

    /**
     * 現在の壁のなぞり状態をリセット（再挑戦用）
     */
    public void reset() {
        tracedHoles.clear();
    }

    /**
     * すべての状態をクリア
     */
    public void clear() {
        currentWallZ = null;
        allHoles.clear();
        tracedHoles.clear();
    }
}
