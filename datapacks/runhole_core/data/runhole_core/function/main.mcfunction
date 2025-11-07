# スペクテーターモードタイマー
function runhole_core:spectator/timer

# ゲーム時間計測
function runhole_core:timer/tick

# ゴール判定
function runhole_core:goal/check

# メッセージ表示
# ゲーム開始メッセージ（game_state=1でまだ表示していない場合）
execute as @a[scores={runhole_game_state=1}] unless score @s runhole_msg_shown matches 1.. run function runhole_core:message/game_start
execute as @a[scores={runhole_game_state=1}] unless score @s runhole_msg_shown matches 1.. run scoreboard players set @s runhole_msg_shown 1

# ゲームオーバーメッセージ（game_state=2でまだ表示していない場合）
execute as @a[scores={runhole_game_state=2}] unless score @s runhole_msg_shown matches 2.. run function runhole_core:message/game_over
execute as @a[scores={runhole_game_state=2}] unless score @s runhole_msg_shown matches 2.. run scoreboard players set @s runhole_msg_shown 2

# 壁通過マイルストーンメッセージ
execute as @a[scores={runhole_game_state=1}] run function runhole_core:message/wall_milestone

# パーフェクト通過メッセージ
execute as @a[scores={runhole_game_state=1}] run function runhole_core:message/perfect_wall

# ゲーム開始時にメッセージフラグをリセット（game_state=1でmsg_shown>=2の場合）
execute as @a[scores={runhole_game_state=1,runhole_msg_shown=2..}] run scoreboard players reset @s runhole_msg_shown
execute as @a[scores={runhole_game_state=1,runhole_msg_shown=2..}] run scoreboard players reset @s runhole_milestone_shown
