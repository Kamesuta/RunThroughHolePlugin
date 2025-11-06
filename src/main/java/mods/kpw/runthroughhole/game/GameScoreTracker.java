package mods.kpw.runthroughhole.game;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;

import mods.kpw.runthroughhole.Main;

public class GameScoreTracker {

    private Scoreboard scoreboard;
    private Player player;

    // Objective名を定数として定義
    public static final String OBJECTIVE_GAME_STATE = "runhole_game_state";
    public static final String OBJECTIVE_END_TYPE = "runhole_end_type";
    public static final String OBJECTIVE_WALLS_PASSED = "runhole_walls_passed";
    public static final String OBJECTIVE_HOLES_TRACED = "runhole_holes_traced";
    public static final String OBJECTIVE_PERFECT_WALLS = "runhole_perfect_walls";
    public static final String OBJECTIVE_MOVE_COUNT = "runhole_move_count";
    public static final String OBJECTIVE_ROTATION_COUNT = "runhole_rotation_count";
    public static final String OBJECTIVE_IS_BOOSTING = "runhole_is_boosting";

    // ゲーム状態の定数
    public static final int GAME_STATE_NOT_PLAYING = 0;
    public static final int GAME_STATE_PLAYING = 1;
    public static final int GAME_STATE_GAME_END = 2;

    // 終了タイプの定数
    public static final int END_TYPE_NONE = 0;
    public static final int END_TYPE_GAME_OVER = 1;
    public static final int END_TYPE_COMMAND_STOP = 2;
    public static final int END_TYPE_PLAYER_QUIT = 3;
    public static final int END_TYPE_LOGOUT = 4;


    public GameScoreTracker(Player player) {
        this.player = player;
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager != null) {
            this.scoreboard = manager.getMainScoreboard();
        } else {
            Main.logger.warning("ScoreboardManager is null. Scoreboard features might not work.");
        }
    }

    /**
     * プラグイン初期化時に1回だけ呼ばれる
     * すべてのObjectiveを登録する
     */
    public static void registerObjectives() {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) {
            Main.logger.warning("ScoreboardManager is null. Cannot register objectives.");
            return;
        }
        Scoreboard scoreboard = manager.getMainScoreboard();

        createObjective(scoreboard, OBJECTIVE_GAME_STATE, "ゲーム状態");
        createObjective(scoreboard, OBJECTIVE_END_TYPE, "終了タイプ");
        createObjective(scoreboard, OBJECTIVE_WALLS_PASSED, "通過壁数");
        createObjective(scoreboard, OBJECTIVE_HOLES_TRACED, "なぞった穴数");
        createObjective(scoreboard, OBJECTIVE_PERFECT_WALLS, "パーフェクト壁数");
        createObjective(scoreboard, OBJECTIVE_MOVE_COUNT, "移動回数");
        createObjective(scoreboard, OBJECTIVE_ROTATION_COUNT, "回転回数");
        createObjective(scoreboard, OBJECTIVE_IS_BOOSTING, "ブースト中");
    }

    private static void createObjective(Scoreboard scoreboard, String name, String displayName) {
        Objective objective = scoreboard.getObjective(name);
        if (objective == null) {
            objective = scoreboard.registerNewObjective(name, "dummy", displayName);
        } else {
            objective.setDisplayName(displayName);
        }
    }

    public void setScore(String objectiveName, int score) {
        if (scoreboard == null) return;
        Objective objective = scoreboard.getObjective(objectiveName);
        if (objective != null) {
            objective.getScore(player.getName()).setScore(score);
        }
    }

    public void addScore(String objectiveName, int amount) {
        if (scoreboard == null) return;
        Objective objective = scoreboard.getObjective(objectiveName);
        if (objective != null) {
            int currentScore = objective.getScore(player.getName()).getScore();
            objective.getScore(player.getName()).setScore(currentScore + amount);
        }
    }

    public int getScore(String objectiveName) {
        if (scoreboard == null) return 0;
        Objective objective = scoreboard.getObjective(objectiveName);
        if (objective != null) {
            return objective.getScore(player.getName()).getScore();
        }
        return 0;
    }

    public void initializeScores() {
        setScore(OBJECTIVE_GAME_STATE, GAME_STATE_PLAYING);
        setScore(OBJECTIVE_END_TYPE, END_TYPE_NONE);
        setScore(OBJECTIVE_WALLS_PASSED, 0);
        setScore(OBJECTIVE_HOLES_TRACED, 0);
        setScore(OBJECTIVE_PERFECT_WALLS, 0);
        setScore(OBJECTIVE_MOVE_COUNT, 0);
        setScore(OBJECTIVE_ROTATION_COUNT, 0);
        setScore(OBJECTIVE_IS_BOOSTING, 0);
    }
}