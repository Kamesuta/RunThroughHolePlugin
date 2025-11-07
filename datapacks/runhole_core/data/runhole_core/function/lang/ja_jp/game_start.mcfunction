# ゲーム開始メッセージ（日本語）
tellraw @s {"text":"========== 穴抜けゲーム開始 ==========","color":"gold","bold":true}
tellraw @s {"text":"目標: ","color":"red","bold":true,"extra":[{"text":"壁に当たらないように進み続けよう！","color":"white","bold":false}]}
tellraw @s {"text":"【移動操作】","color":"yellow","bold":true}
tellraw @s {"text":"  W/A/S/D ","color":"green","extra":[{"text":"- 上下左右に移動","color":"white"}]}
tellraw @s {"text":"  Space ","color":"green","extra":[{"text":"- 加速（長押し可能）","color":"white"}]}
tellraw @s {"text":"【回転操作】","color":"yellow","bold":true}
tellraw @s {"text":"  視点を上下左右に動かす ","color":"green","extra":[{"text":"- キューブをX/Y回転","color":"white"}]}
tellraw @s {"text":"  左右クリック ","color":"green","extra":[{"text":"- キューブをZ回転","color":"white"}]}
tellraw @s {"text":"=====================================","color":"gold","bold":true}

# 効果音
playsound minecraft:entity.player.levelup master @s ~ ~ ~ 1.0 1.0

# タイトル表示
title @s title {"text":"スタート！","color":"green","bold":true}
title @s subtitle {"text":"壁を避けて進もう！","color":"white"}
title @s times 10 40 10
