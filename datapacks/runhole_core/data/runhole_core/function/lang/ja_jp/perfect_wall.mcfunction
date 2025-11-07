# パーフェクト通過演出（日本語）

execute if score @s runhole_perfect_walls matches 5 run title @s actionbar {"text":"⭐ 5回連続パーフェクト！","color":"aqua","bold":true}
execute if score @s runhole_perfect_walls matches 5 run playsound minecraft:entity.experience_orb.pickup master @s ~ ~ ~ 1.0 1.5

execute if score @s runhole_perfect_walls matches 10 run title @s actionbar {"text":"⭐⭐ 10回連続パーフェクト！","color":"gold","bold":true}
execute if score @s runhole_perfect_walls matches 10 run playsound minecraft:entity.player.levelup master @s ~ ~ ~ 1.0 2.0
