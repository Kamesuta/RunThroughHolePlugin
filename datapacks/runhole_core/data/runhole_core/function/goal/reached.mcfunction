# ゴール到達時の処理

# ゲームを終了（プラグイン側の処理は継続）
scoreboard players set @s runhole_game_state 2
scoreboard players set @s runhole_end_type 5

# スペクテーターモードタイマーを初期化
scoreboard players set @s runhole_spectator_timer 0

# トリガーをリセット
scoreboard players reset @s runhole_goal_trigger

# ログ出力
tellraw @s [{"text":"[RunHole Core] ","color":"aqua"},{"text":"ゴール判定が実行されました","color":"gold"}]
