# 壁到達マイルストーン（日本語）

# 10枚突破（milestone_shown < 10 の時のみ表示）
execute if score @s runhole_walls_passed matches 10.. unless score @s runhole_milestone_shown matches 10.. run tellraw @s {"text":"🎉 10枚の壁を突破！","color":"gold","bold":true}
execute if score @s runhole_walls_passed matches 10.. unless score @s runhole_milestone_shown matches 10.. run playsound minecraft:ui.toast.challenge_complete master @s ~ ~ ~ 1.0 1.0
execute if score @s runhole_walls_passed matches 10.. unless score @s runhole_milestone_shown matches 10.. run scoreboard players set @s runhole_milestone_shown 10

# 20枚突破（milestone_shown < 20 の時のみ表示）
execute if score @s runhole_walls_passed matches 20.. unless score @s runhole_milestone_shown matches 20.. run tellraw @s {"text":"🎉 20枚の壁を突破！","color":"gold","bold":true}
execute if score @s runhole_walls_passed matches 20.. unless score @s runhole_milestone_shown matches 20.. run playsound minecraft:ui.toast.challenge_complete master @s ~ ~ ~ 1.0 1.0
execute if score @s runhole_walls_passed matches 20.. unless score @s runhole_milestone_shown matches 20.. run scoreboard players set @s runhole_milestone_shown 20

# 50枚突破（milestone_shown < 50 の時のみ表示）
execute if score @s runhole_walls_passed matches 50.. unless score @s runhole_milestone_shown matches 50.. run tellraw @s {"text":"🏆 50枚の壁を突破！すごい！","color":"gold","bold":true}
execute if score @s runhole_walls_passed matches 50.. unless score @s runhole_milestone_shown matches 50.. run playsound minecraft:ui.toast.challenge_complete master @s ~ ~ ~ 1.0 1.0
execute if score @s runhole_walls_passed matches 50.. unless score @s runhole_milestone_shown matches 50.. run scoreboard players set @s runhole_milestone_shown 50

# 100枚突破（milestone_shown < 100 の時のみ表示）
execute if score @s runhole_walls_passed matches 100.. unless score @s runhole_milestone_shown matches 100.. run tellraw @s {"text":"🏆🏆 100枚の壁を突破！伝説級！","color":"gold","bold":true}
execute if score @s runhole_walls_passed matches 100.. unless score @s runhole_milestone_shown matches 100.. run playsound minecraft:ui.toast.challenge_complete master @s ~ ~ ~ 1.0 1.0
execute if score @s runhole_walls_passed matches 100.. unless score @s runhole_milestone_shown matches 100.. run scoreboard players set @s runhole_milestone_shown 100
