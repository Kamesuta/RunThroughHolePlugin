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
    
    // グリッド位置を移動
    public void move(Vector3f delta) {
        this.gridPosition.add(delta);
        updateTransformation();
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
    
    // BlockDisplayのTransformationを更新（位置と回転）
    private void updateTransformation() {
        Transformation transformation = display.getTransformation();
        
        // Interpolation設定（移動と回転を同時にスムーズに）
        display.setInterpolationDuration(5); // 5tick = 0.25秒
        display.setInterpolationDelay(0);
        
        // BlockDisplayの中心オフセット（-0.5, -0.5, -0.5）に回転を適用
        Vector3f centerOffset = new Vector3f(-0.5f, -0.5f, -0.5f);
        centerOffset.rotate(rotation);
        
        // グリッド位置による移動オフセット
        Vector3f translation = new Vector3f(gridPosition.x, gridPosition.y, gridPosition.z);
        translation.add(centerOffset);
        
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

