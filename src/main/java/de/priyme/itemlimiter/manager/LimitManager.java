package de.priyme.itemlimiter.manager;

import de.priyme.itemlimiter.ItemLimiter;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

public class LimitManager {

    private final ItemLimiter plugin;
    private final Map<Material, Integer> limits = new HashMap<>();

    public LimitManager(ItemLimiter plugin) {
        this.plugin = plugin;
        loadLimits();
    }

    private void loadLimits() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("limits");
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            try {
                Material mat = Material.valueOf(key);
                int amount = section.getInt(key);
                limits.put(mat, amount);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid material in config: " + key);
            }
        }
    }

    public void saveLimits() {
        plugin.getConfig().set("limits", null); // Reset
        for (Map.Entry<Material, Integer> entry : limits.entrySet()) {
            plugin.getConfig().set("limits." + entry.getKey().name(), entry.getValue());
        }
        plugin.saveConfig();
    }

    public Map<Material, Integer> getLimits() {
        return limits;
    }

    public void setLimit(Material material, int amount) {
        if (amount <= 0) {
            limits.remove(material);
        } else {
            limits.put(material, amount);
        }
        saveLimits();
    }

    public int getLimit(Material material) {
        return limits.getOrDefault(material, -1);
    }

    public int countItems(Player player, Material material) {
        int count = 0;
        
        for (ItemStack is : player.getInventory().getContents()) {
            if (is != null && is.getType() == material) {
                count += is.getAmount();
            }
        }
        
        ItemStack cursor = player.getItemOnCursor();
        if (cursor != null && cursor.getType() == material) {
            count += cursor.getAmount();
        }

        // FIX: GUI berücksichtigt jetzt auch Crafting-Felder
        InventoryView view = player.getOpenInventory();
        if (view.getType() == InventoryType.CRAFTING) {
            Inventory topInv = view.getTopInventory();
            for (int i = 1; i <= 4; i++) {
                ItemStack item = topInv.getItem(i);
                if (item != null && item.getType() == material) count += item.getAmount();
            }
        } else if (view.getType() == InventoryType.WORKBENCH) {
            Inventory topInv = view.getTopInventory();
            for (int i = 1; i <= 9; i++) {
                ItemStack item = topInv.getItem(i);
                if (item != null && item.getType() == material) count += item.getAmount();
            }
        }

        return count;
    }

    public boolean canPickup(Player player, Material material, int incomingAmount) {
        int limit = getLimit(material);
        if (limit == -1) return true;

        int current = countItems(player, material);
        return (current + incomingAmount) <= limit;
    }

    public int getRemainingSpace(Player player, Material material) {
        int limit = getLimit(material);
        if (limit == -1) return Integer.MAX_VALUE;

        int current = countItems(player, material);
        return Math.max(0, limit - current);
    }
}
