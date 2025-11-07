# ゲーム終了状態になった時、タイマーを初期化（スコアが未設定の場合のみ）
execute as @a[scores={runhole_game_state=2}] unless score @s runhole_spectator_timer matches 0.. run scoreboard players set @s runhole_spectator_timer 0

# ゲーム終了状態（runhole_game_state=2）のプレイヤーのタイマーを進める
execute as @a[scores={runhole_game_state=2,runhole_spectator_timer=0..59}] run scoreboard players add @s runhole_spectator_timer 1

# 3秒（60tick）経過したらstopGameを呼び出し
execute as @a[scores={runhole_game_state=2,runhole_spectator_timer=60}] run function runhole_core:spectator/call_stop
