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
        updateDisplayPosition();
    }
    
    // 回転を適用
    public void applyRotation(Quaternionf newRotation) {
        // 現在の回転に新しい回転を適用
        Quaternionf rotation = new Quaternionf()
                .mul(newRotation)
                .mul(this.rotation);
        this.rotation = rotation;
        
        updateDisplayTransformation();
    }
    
    // BlockDisplayの位置を更新
    private void updateDisplayPosition() {
        double worldX = baseLocation.getX() + gridPosition.x;
        double worldY = baseLocation.getY() + 2.0 + gridPosition.y; // 基準位置の上2ブロック + グリッド位置
        double worldZ = baseLocation.getZ() + gridPosition.z;
        
        // Interpolationを設定（スムーズな移動）
        display.setInterpolationDuration(10); // 10tick = 0.5秒
        display.setInterpolationDelay(0);
        
        // BlockDisplayを移動
        Location newLocation = new Location(baseLocation.getWorld(), worldX, worldY, worldZ, 0, 0);
        display.teleport(newLocation);
    }
    
    // BlockDisplayのTransformationを更新（回転のみ）
    private void updateDisplayTransformation() {
        Transformation transformation = display.getTransformation();
        
        // アニメーション設定
        display.setInterpolationDuration(5); // 5ティックでアニメーション
        display.setInterpolationDelay(0); // 遅延なし
        
        // BlockDisplayの中心オフセット
        Vector3f offset = new Vector3f(-0.5f, -0.5f, -0.5f);
        offset.rotate(rotation); // 回転を考慮してオフセットを回転
        
        // Transformationに設定
        transformation.getLeftRotation().set(rotation);
        transformation.getTranslation().set(offset);
        
        display.setTransformation(transformation);
    }
    
    // クリーンアップ
    public void remove() {
        if (display != null) {
            display.remove();
        }
    }
}

