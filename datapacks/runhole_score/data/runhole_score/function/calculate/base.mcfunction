# 基本スコア = 壁通過数 × 100
scoreboard players operation @s runhole_score_base = @s runhole_walls_passed
scoreboard players operation @s runhole_score_base *= #100 runhole_score_base
