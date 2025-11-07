# 言語に応じてメッセージを表示
execute if score @s runhole_lang matches 0 run function runhole_core:lang/ja_jp/perfect_wall
execute if score @s runhole_lang matches 1 run function runhole_core:lang/en_us/perfect_wall
