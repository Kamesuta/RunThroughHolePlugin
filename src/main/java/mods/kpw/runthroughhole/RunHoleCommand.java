package mods.kpw.runthroughhole;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

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
            sender.sendMessage("引数が不正です。/runhole <start|stop> [player|@selector]");
            return false;
        }

        String subCommand = args[0].toLowerCase();
        
        if (!subCommand.equals("start") && !subCommand.equals("stop")) {
            sender.sendMessage("引数が不正です。/runhole <start|stop> [player|@selector]");
            return false;
        }

        // ターゲットプレイヤーのリスト
        List<Player> targets = new ArrayList<>();

        if (args.length == 1) {
            // ターゲット指定なし：自分自身
            if (!(sender instanceof Player)) {
                sender.sendMessage("コンソールからはターゲットを指定する必要があります。");
                return true;
            }
            
            Player player = (Player) sender;
            if (!player.hasPermission("runhole.use")) {
                player.sendMessage("このコマンドを実行する権限がありません。");
                return true;
            }
            
            targets.add(player);
        } else {
            // ターゲット指定あり：セレクタまたはプレイヤー名
            String targetArg = args[1];
            
            // 他人を操作する場合はadmin権限が必要
            if (sender instanceof Player) {
                Player player = (Player) sender;
                if (!targetArg.equals(player.getName()) && !player.hasPermission("runhole.admin")) {
                    player.sendMessage("他のプレイヤーを操作する権限がありません。");
                    return true;
                }
            }
            
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
                        return true;
                    }
                } catch (IllegalArgumentException e) {
                    sender.sendMessage("無効なセレクタです: " + targetArg);
                    return true;
                }
            } else {
                // プレイヤー名の場合
                Player target = Bukkit.getPlayer(targetArg);
                if (target == null) {
                    sender.sendMessage("プレイヤー " + targetArg + " が見つかりませんでした。");
                    return true;
                }
                targets.add(target);
            }
        }

        // 各ターゲットに対して実行
        int successCount = 0;
        for (Player target : targets) {
            if (subCommand.equals("start")) {
                plugin.getGameManager().startGame(target);
                successCount++;
            } else if (subCommand.equals("stop")) {
                plugin.getGameManager().stopGame(target);
                successCount++;
            }
        }

        // 結果メッセージ
        if (successCount > 0) {
            if (targets.size() == 1) {
                sender.sendMessage(targets.get(0).getName() + "のゲームを" + 
                    (subCommand.equals("start") ? "開始" : "終了") + "しました。");
            } else {
                sender.sendMessage(successCount + "人のプレイヤーのゲームを" + 
                    (subCommand.equals("start") ? "開始" : "終了") + "しました。");
            }
        }

        return true;
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
        } else if (args.length == 2 && sender.hasPermission("runhole.admin")) {
            // 第2引数のTab補完: プレイヤー名とセレクタ
            String partial = args[1].toLowerCase();
            
            // セレクタの補完
            List<String> selectors = Arrays.asList("@a", "@p", "@r", "@s");
            for (String selector : selectors) {
                if (selector.startsWith(partial)) {
                    completions.add(selector);
                }
            }
            
            // プレイヤー名の補完
            for (Player player : Bukkit.getOnlinePlayers()) {
                String name = player.getName();
                if (name.toLowerCase().startsWith(partial)) {
                    completions.add(name);
                }
            }
        }

        return completions;
    }
}
