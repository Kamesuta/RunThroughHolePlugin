# ゴールトリガーが立っているプレイヤーをゴール扱いにする
# （コマンドブロックで `scoreboard players set <player> runhole_goal_trigger 1` を実行）

execute as @a[scores={runhole_goal_trigger=1..}] run function runhole_core:goal/reached
