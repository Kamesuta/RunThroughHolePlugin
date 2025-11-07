# パーフェクト通過時の速度上昇（5回連続、10回連続）

# 5回連続パーフェクト → Speed I を5秒
execute if score @s runhole_perfect_walls matches 5 run effect give @s minecraft:speed 5 0 true

# 10回連続パーフェクト → Speed II を10秒
execute if score @s runhole_perfect_walls matches 10 run effect give @s minecraft:speed 10 1 true
