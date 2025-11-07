# stopGameリクエストフラグを立てる（プラグイン側が監視）
scoreboard players set @s runhole_stop_request 1

# タイマーを61以上にして再度呼ばれないようにする
scoreboard players set @s runhole_spectator_timer 100

# ログ出力（デバッグ用）
tellraw @s [{"text":"[RunHole Core] ","color":"aqua"},{"text":"ゲーム終了処理をリクエストしました","color":"gray"}]
