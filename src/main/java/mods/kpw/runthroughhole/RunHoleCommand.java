package mods.kpw.runthroughhole;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;

import mods.kpw.runthroughhole.game.GameScoreTracker;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class RunHoleCommand implements CommandExecutor, TabCompleter {

    private final Main plugin;
    private static final List<String> SUBCOMMANDS = Arrays.asList("start", "stop");

    public RunHoleCommand(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("引数が不正です。/runhole <start|stop> [pattern] [player|@selector]");
            return false;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "start":
                return handleStartCommand(sender, args);
            case "stop":
                return handleStopCommand(sender, args);
            default:
                sender.sendMessage("引数が不正です。/runhole <start|stop> [pattern] [player|@selector]");
                return false;
        }
    }

    /**
     * startコマンドの処理
     */
    private boolean handleStartCommand(CommandSender sender, String[] args) {
        // startコマンドは最低2引数必要（start + pattern）
        if (args.length < 2) {
            sender.sendMessage("使用方法: /runhole start <pattern> [player|@selector]");
            return true;
        }

        String patternArg = args[1];

        // パターンのバリデーション
        if (!patternArg.matches("[01]{27}")) {
            sender.sendMessage("パターンは27桁の0と1で指定してください。");
            return true;
        }

        // ターゲットの解析
        List<Player> targets;
        if (args.length >= 3) {
            // ターゲット指定あり
            targets = resolveTargets(sender, args[2]);
        } else {
            // ターゲット指定なし（自分自身）
            targets = resolveSelfTarget(sender);
        }

        if (targets == null) {
            return true; // エラーメッセージは各メソッド内で表示済み
        }

        // パターンをboolean配列に変換
        boolean[][][] pattern = parsePattern(patternArg);

        // 各ターゲットに対してゲームを開始
        for (Player target : targets) {
            plugin.getGameManager().startGame(target, pattern);
        }

        // 結果メッセージ
        sendSuccessMessage(sender, targets, "開始");
        return true;
    }

    /**
     * stopコマンドの処理
     */
    private boolean handleStopCommand(CommandSender sender, String[] args) {
        // ターゲットの解析
        List<Player> targets;
        if (args.length >= 2) {
            // ターゲット指定あり
            targets = resolveTargets(sender, args[1]);
        } else {
            // ターゲット指定なし（自分自身）
            targets = resolveSelfTarget(sender);
        }

        if (targets == null) {
            return true; // エラーメッセージは各メソッド内で表示済み
        }

        // 各ターゲットに対してゲームを停止
        for (Player target : targets) {
            plugin.getGameManager().stopGame(target, GameScoreTracker.END_TYPE_COMMAND_STOP);
        }

        // 結果メッセージ
        sendSuccessMessage(sender, targets, "終了");
        return true;
    }

    /**
     * 自分自身をターゲットとして解析
     */
    private List<Player> resolveSelfTarget(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("コンソールからはターゲットを指定する必要があります。");
            return null;
        }

        Player player = (Player) sender;
        if (!player.hasPermission("runhole.use")) {
            player.sendMessage("このコマンドを実行する権限がありません。");
            return null;
        }

        List<Player> targets = new ArrayList<>();
        targets.add(player);
        return targets;
    }

    /**
     * ターゲットを解析（セレクタまたはプレイヤー名）
     */
    private List<Player> resolveTargets(CommandSender sender, String targetArg) {
        // 権限チェック
        if (sender instanceof Player) {
            Player player = (Player) sender;
            if (!targetArg.equals(player.getName()) && !player.hasPermission("runhole.admin")) {
                player.sendMessage("他のプレイヤーを操作する権限がありません。");
                return null;
            }
        }

        List<Player> targets = new ArrayList<>();

        // セレクタの場合
        if (targetArg.startsWith("@")) {
            try {
                List<Entity> entities = Bukkit.selectEntities(sender, targetArg);
                for (Entity entity : entities) {
                    if (entity instanceof Player) {
                        targets.add((Player) entity);
                    }
                }

                if (targets.isEmpty()) {
                    sender.sendMessage("セレクタ " + targetArg + " に一致するプレイヤーが見つかりませんでした。");
                    return null;
                }
            } catch (IllegalArgumentException e) {
                sender.sendMessage("無効なセレクタです: " + targetArg);
                return null;
            }
        } else {
            // プレイヤー名の場合
            Player target = Bukkit.getPlayer(targetArg);
            if (target == null) {
                sender.sendMessage("プレイヤー " + targetArg + " が見つかりませんでした。");
                return null;
            }
            targets.add(target);
        }

        return targets;
    }

    /**
     * 成功メッセージを送信
     */
    private void sendSuccessMessage(CommandSender sender, List<Player> targets, String action) {
        if (targets.size() == 1) {
            sender.sendMessage(targets.get(0).getName() + "のゲームを" + action + "しました。");
        } else {
            sender.sendMessage(targets.size() + "人のプレイヤーのゲームを" + action + "しました。");
        }
    }

    /**
     * 27桁の01文字列を3x3x3のboolean配列に変換
     * 順序: X→Y→Z（X座標が最も早く変化）
     *
     * @param pattern 27桁の01文字列
     * @return 3x3x3のboolean配列
     */
    private boolean[][][] parsePattern(String pattern) {
        boolean[][][] result = new boolean[3][3][3];
        int index = 0;
        for (int z = 0; z < 3; z++) {
            for (int y = 0; y < 3; y++) {
                for (int x = 0; x < 3; x++) {
                    result[x][y][z] = pattern.charAt(index) == '1';
                    index++;
                }
            }
        }
        return result;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // 第1引数のTab補完: start, stop
            String partialCommand = args[0].toLowerCase();
            for (String subcommand : SUBCOMMANDS) {
                if (subcommand.startsWith(partialCommand)) {
                    completions.add(subcommand);
                }
            }
        } else if (args.length == 2) {
            String subCommand = args[0].toLowerCase();

            if (subCommand.equals("start")) {
                // startコマンドの第2引数: パターン（プレイヤーの視線先から生成）
                if (sender instanceof Player) {
                    Player player = (Player) sender;
                    String pattern = generatePatternFromTargetBlock(player);
                    if (pattern != null) {
                        completions.add(pattern);
                    }
                }
            } else if (subCommand.equals("stop")) {
                // stopコマンドの第2引数: プレイヤー名とセレクタ
                addPlayerCompletions(completions, args[1], sender);
            }
        } else if (args.length == 3) {
            String subCommand = args[0].toLowerCase();

            if (subCommand.equals("start")) {
                // startコマンドの第3引数: プレイヤー名とセレクタ
                addPlayerCompletions(completions, args[2], sender);
            }
        }

        return completions;
    }

    /**
     * プレイヤーの視線先のブロックを中心に3x3x3範囲のガラスブロックからパターンを生成
     *
     * @param player プレイヤー
     * @return 27桁の01文字列、視線先にブロックがない場合はnull
     */
    private String generatePatternFromTargetBlock(Player player) {
        // プレイヤーの視線先のブロックを取得（最大100ブロック先まで）
        RayTraceResult rayTrace = player.rayTraceBlocks(100.0);
        if (rayTrace == null || rayTrace.getHitBlock() == null) {
            return null;
        }

        Block centerBlock = rayTrace.getHitBlock();
        int centerX = centerBlock.getX();
        int centerY = centerBlock.getY();
        int centerZ = centerBlock.getZ();

        // 3x3x3範囲のガラスブロックを検出
        StringBuilder pattern = new StringBuilder();

        // X→Y→Zの順序でスキャン
        for (int z = -1; z <= 1; z++) {
            for (int y = -1; y <= 1; y++) {
                for (int x = -1; x <= 1; x++) {
                    Block block = player.getWorld().getBlockAt(
                        centerX + x,
                        centerY + y,
                        centerZ + z
                    );

                    // ガラスブロックの場合は'1'、それ以外は'0'
                    if (block.getType() == Material.GLASS) {
                        pattern.append('1');
                    } else {
                        pattern.append('0');
                    }
                }
            }
        }

        return pattern.toString();
    }

    /**
     * プレイヤー名とセレクタの補完を追加
     */
    private void addPlayerCompletions(List<String> completions, String partial, CommandSender sender) {
        String lowerPartial = partial.toLowerCase();

        // admin権限がある場合のみセレクタとプレイヤー名を提案
        if (sender.hasPermission("runhole.admin")) {
            // セレクタの補完
            List<String> selectors = Arrays.asList("@a", "@p", "@r", "@s");
            for (String selector : selectors) {
                if (selector.startsWith(lowerPartial)) {
                    completions.add(selector);
                }
            }

            // プレイヤー名の補完
            for (Player player : Bukkit.getOnlinePlayers()) {
                String name = player.getName();
                if (name.toLowerCase().startsWith(lowerPartial)) {
                    completions.add(name);
                }
            }
        } else if (sender instanceof Player) {
            // 自分自身のみ補完
            Player player = (Player) sender;
            if (player.getName().toLowerCase().startsWith(lowerPartial)) {
                completions.add(player.getName());
            }
        }
    }
}
