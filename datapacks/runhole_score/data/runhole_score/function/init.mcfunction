# スコアボード初期化
scoreboard objectives add runhole_score_base dummy "基本スコア"
scoreboard objectives add runhole_score_perfect dummy "パーフェクトボーナス"
scoreboard objectives add runhole_score_total dummy "合計スコア"

# 定数の初期化
scoreboard players set #100 runhole_score_base 100
scoreboard players set #300 runhole_score_perfect 300

tellraw @a [{"text":"[RunHole Score] ","color":"light_purple","bold":true},{"text":"スコア計算システムを読み込みました","color":"white","bold":false}]
