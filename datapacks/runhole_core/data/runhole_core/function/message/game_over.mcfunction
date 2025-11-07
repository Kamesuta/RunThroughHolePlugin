# 言語に応じてメッセージを表示
execute if score @s runhole_lang matches 0 run function runhole_core:lang/ja_jp/game_over
execute if score @s runhole_lang matches 1 run function runhole_core:lang/en_us/game_over
