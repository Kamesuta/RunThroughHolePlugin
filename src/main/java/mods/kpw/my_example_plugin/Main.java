package mods.kpw.my_example_plugin;

import org.bukkit.plugin.java.JavaPlugin;

public class Main extends JavaPlugin {

    @Override
    public void onEnable() {
        getLogger().info("Hello from my Minecraft plugin!");
        // Your plugin startup logic here
    }

    @Override
    public void onDisable() {
        getLogger().info("Goodbye.");
        // Your plugin shutdown logic here
    }
}
