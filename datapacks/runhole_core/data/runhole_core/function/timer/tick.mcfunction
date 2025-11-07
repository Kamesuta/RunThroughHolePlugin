# ゲーム中（runhole_game_state=1）のプレイヤーの時間を進める
execute as @a[scores={runhole_game_state=1}] run scoreboard players add @s runhole_game_time 1
