# ゲーム中のスコア計算
execute as @a[scores={runhole_game_state=1}] run function runhole_score:calculate/base
execute as @a[scores={runhole_game_state=1}] run function runhole_score:calculate/perfect_bonus
execute as @a[scores={runhole_game_state=1}] run function runhole_score:calculate/total

# 特典付与
execute as @a[scores={runhole_game_state=1}] run function runhole_score:reward/speed_boost
