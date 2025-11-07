# スコアボード初期化（コア機能）
scoreboard objectives add runhole_spectator_timer dummy "スペクテーターモードタイマー"
scoreboard objectives add runhole_game_time dummy "ゲーム時間"
scoreboard objectives add runhole_goal_trigger dummy "ゴールトリガー"
scoreboard objectives add runhole_stop_request dummy "stopGameリクエスト"

# スコアボード初期化（メッセージ機能）
scoreboard objectives add runhole_lang dummy "言語設定"
scoreboard objectives add runhole_msg_shown dummy "メッセージ表示済み"
scoreboard objectives add runhole_milestone_shown dummy "マイルストーン表示済み"

# デフォルト言語: ja_jp (0), en_us (1)
execute as @a unless score @s runhole_lang matches 0.. run scoreboard players set @s runhole_lang 0

tellraw @a [{"text":"[RunHole Core] ","color":"aqua","bold":true},{"text":"コア機能を読み込みました","color":"white","bold":false}]
