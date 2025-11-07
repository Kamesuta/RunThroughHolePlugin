# ゲームオーバーメッセージ（日本語、終了理由別）

# 衝突によるゲームオーバー
execute if score @s runhole_end_type matches 1 run title @s title {"text":"ゲームオーバー","color":"red","bold":true}
execute if score @s runhole_end_type matches 1 run title @s subtitle {"text":"壁に衝突しました","color":"yellow"}
execute if score @s runhole_end_type matches 1 run title @s times 10 60 10
execute if score @s runhole_end_type matches 1 run tellraw @s {"text":"ゲームオーバー: 壁に衝突しました","color":"red"}
execute if score @s runhole_end_type matches 1 run playsound minecraft:entity.ender_dragon.death master @s ~ ~ ~ 1.0 1.0

# Shiftキーによる退出
execute if score @s runhole_end_type matches 3 run title @s title {"text":"終了","color":"aqua"}
execute if score @s runhole_end_type matches 3 run title @s subtitle {"text":"Shiftキーが押されました","color":"gray"}
execute if score @s runhole_end_type matches 3 run tellraw @s {"text":"ゲームを終了しました","color":"yellow"}
execute if score @s runhole_end_type matches 3 run playsound minecraft:block.note_block.pling master @s ~ ~ ~ 1.0 0.5

# ログアウト
execute if score @s runhole_end_type matches 4 run tellraw @s {"text":"ログアウトによりゲームが終了しました","color":"gray"}

# ゴール（コマンドブロックトリガー）
execute if score @s runhole_end_type matches 5 run title @s title {"text":"ゴール！","color":"gold","bold":true}
execute if score @s runhole_end_type matches 5 run tellraw @s {"text":"ゴールに到達しました！","color":"gold"}
execute if score @s runhole_end_type matches 5 run playsound minecraft:ui.toast.challenge_complete master @s ~ ~ ~ 1.0 1.0
