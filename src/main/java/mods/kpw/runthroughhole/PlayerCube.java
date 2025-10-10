package mods.kpw.runthroughhole;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class PlayerCube {
    public BlockDisplay display;
    public Quaternionf rotation;
    public Vector3f gridPosition; // グリッド位置（ブロック単位）
    
    private Location baseLocation; // 基準位置（プレイヤーの固定位置）
    
    // 自動前進用
    private static final float FORWARD_SPEED = 0.05f; // 1tickあたりの前進量（ブロック単位）
    private float forwardProgress = 0f; // 前進の進行度（0～1で1マス分）
    
    public float getForwardProgress() {
        return forwardProgress;
    }
    
    public PlayerCube(World world, Location baseLocation) {
        this.baseLocation = baseLocation;
        this.gridPosition = new Vector3f(0, 0, 0);
        // 初期視点に合わせてBlockDisplayの回転も初期化 (Z+方向: Yaw 0, Pitch 0)
        this.rotation = new Quaternionf().rotateY((float) Math.toRadians(90.0f));
        
        // BlockDisplayをスポーン（基準位置の上2ブロック）
        Location spawnLoc = baseLocation.clone().add(0, 2, 0);
        this.display = world.spawn(spawnLoc, BlockDisplay.class);
        this.display.setBlock(Material.FLETCHING_TABLE.createBlockData());
        
        // Interpolationの初期設定
        this.display.setInterpolationDuration(10); // 10tick = 0.5秒でスムーズに移動
        this.display.setInterpolationDelay(0);
    }
    
    // グリッド位置を移動（XY方向のみ）
    public void move(Vector3f delta) {
        this.gridPosition.add(delta);
        // XY移動はTransformationで更新（Zは触らない）
        updateTransformation();
    }
    
    // 自動前進（毎tick呼び出される）- Z軸のテレポートのみ
    public void autoForward() {
        forwardProgress += FORWARD_SPEED;
        
        // 1マス分進んだらグリッド位置を更新
        if (forwardProgress >= 1.0f) {
            gridPosition.z += 1;
            forwardProgress -= 1.0f;
        }
        
        // Z位置のみテレポートで更新（毎tick）
        updateZPosition();
    }
    
    // Z軸位置のみテレポートで更新
    private void updateZPosition() {
        Location currentLoc = display.getLocation();
        double worldZ = baseLocation.getZ() + gridPosition.z + forwardProgress;
        
        currentLoc.setZ(worldZ);
        display.teleport(currentLoc);
    }
    
    // 回転を適用
    public void applyRotation(Quaternionf newRotation) {
        // 現在の回転に新しい回転を適用
        Quaternionf rotation = new Quaternionf()
                .mul(newRotation)
                .mul(this.rotation);
        this.rotation = rotation;
        
        updateTransformation();
    }
    
    // BlockDisplayのTransformationを更新（XY位置と回転）
    private void updateTransformation() {
        Transformation transformation = display.getTransformation();
        
        // アニメーション設定
        display.setInterpolationDuration(5); // 5tick = 0.25秒
        display.setInterpolationDelay(0);
        
        // BlockDisplayの中心オフセット（-0.5, -0.5, -0.5）に回転を適用
        Vector3f offset = new Vector3f(-0.5f, -0.5f, -0.5f);
        offset.rotate(rotation);
        
        // XY方向の相対位置（Z=0、Zはテレポートで管理）
        Vector3f translation = new Vector3f(gridPosition.x, gridPosition.y, 0);
        translation.add(offset);
        
        // Transformationに設定
        transformation.getLeftRotation().set(rotation);
        transformation.getTranslation().set(translation);
        
        display.setTransformation(transformation);
    }
    
    // クリーンアップ
    public void remove() {
        if (display != null) {
            display.remove();
        }
    }
}

