package de.priyme.itemlimiter;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.List;

import de.priyme.itemlimiter.listener.ItemLimiterListener;
import de.priyme.itemlimiter.commands.LimitCommand; // <--- Das ist der fehlende Import!

public final class ItemLimiter extends JavaPlugin {

    private final HashMap<String, Integer> limits = new HashMap<>();
    private List<String> enabledWorlds;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfigValues();

        getServer().getPluginManager().registerEvents(new ItemLimiterListener(this), this);
        
        LimitCommand cmd = new LimitCommand(this);
        getCommand("limit").setExecutor(cmd);
        getServer().getPluginManager().registerEvents(cmd, this);
        
        getLogger().info("ItemLimiter loaded!");
    }

    @Override
    public void onDisable() {
        getLogger().info("ItemLimiter disabled.");
    }

    public void loadConfigValues() {
        reloadConfig();
        FileConfiguration config = getConfig();
        limits.clear();

        enabledWorlds = config.getStringList("enabled-worlds");

        if (config.isConfigurationSection("limits")) {
            for (String key : config.getConfigurationSection("limits").getKeys(false)) {
                try {
                    int amount = config.getInt("limits." + key);
                    limits.put(key.toUpperCase(), amount);
                } catch (Exception e) {
                    getLogger().warning("Invalid limit in config: " + key);
                }
            }
        }
    }

    public HashMap<String, Integer> getLimits() {
        return limits;
    }

    public boolean isWorldEnabled(String worldName) {
        if (enabledWorlds == null || enabledWorlds.isEmpty()) {
            return true; 
        }
        return enabledWorlds.contains(worldName);
    }
}
