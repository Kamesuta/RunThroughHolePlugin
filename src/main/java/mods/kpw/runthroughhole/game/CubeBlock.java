package mods.kpw.runthroughhole.game;

import org.bukkit.entity.BlockDisplay;
import org.joml.Vector3f;

// BlockDisplayとオフセットをまとめて管理するクラス
public class CubeBlock {
    public BlockDisplay display;
    public Vector3f offset; // ローカル座標での相対位置

    public CubeBlock(BlockDisplay display, Vector3f offset) {
        this.display = display;
        this.offset = offset;
    }
}
