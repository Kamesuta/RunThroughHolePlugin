package mods.kpw.runthroughhole;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class RthCommand implements CommandExecutor, TabCompleter {

    private final Main plugin;
    private static final List<String> SUBCOMMANDS = Arrays.asList("start", "stop");

    public RthCommand(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("このコマンドはプレイヤーからのみ実行できます。");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            player.sendMessage("引数が不正です。/rth <start|stop>");
            return false;
        }

        if (args[0].equalsIgnoreCase("start")) {
            // ゲーム開始処理 (後で実装)
            player.sendMessage("ゲームを開始します。");
            plugin.startGame(player);
        } else if (args[0].equalsIgnoreCase("stop")) {
            // ゲーム終了処理 (後で実装)
            player.sendMessage("ゲームを終了します。");
            plugin.stopGame(player);
        } else {
            player.sendMessage("引数が不正です。/rth <start|stop>");
            return false;
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
        }

        return completions;
    }
}
