# 壁到達マイルストーン（日本語）

execute if score @s runhole_walls_passed matches 10 run tellraw @s {"text":"🎉 10枚の壁を突破！","color":"gold","bold":true}
execute if score @s runhole_walls_passed matches 10 run playsound minecraft:ui.toast.challenge_complete master @s ~ ~ ~ 1.0 1.0

execute if score @s runhole_walls_passed matches 20 run tellraw @s {"text":"🎉 20枚の壁を突破！","color":"gold","bold":true}
execute if score @s runhole_walls_passed matches 20 run playsound minecraft:ui.toast.challenge_complete master @s ~ ~ ~ 1.0 1.0

execute if score @s runhole_walls_passed matches 50 run tellraw @s {"text":"🏆 50枚の壁を突破！すごい！","color":"gold","bold":true}
execute if score @s runhole_walls_passed matches 50 run playsound minecraft:ui.toast.challenge_complete master @s ~ ~ ~ 1.0 1.0

execute if score @s runhole_walls_passed matches 100 run tellraw @s {"text":"🏆🏆 100枚の壁を突破！伝説級！","color":"gold","bold":true}
execute if score @s runhole_walls_passed matches 100 run playsound minecraft:ui.toast.challenge_complete master @s ~ ~ ~ 1.0 1.0
