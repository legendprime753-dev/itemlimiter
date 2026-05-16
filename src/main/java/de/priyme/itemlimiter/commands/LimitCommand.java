package de.priyme.itemlimiter.commands;

import de.priyme.itemlimiter.ItemLimiter;
import de.priyme.itemlimiter.listener.ItemLimiterListener;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

public class LimitCommand implements CommandExecutor, Listener {

    private final ItemLimiter plugin;
    private final Component GUI_TITLE = Component.text("ItemLimiter Management", NamedTextColor.DARK_PURPLE);

    public LimitCommand(ItemLimiter plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only!");
            return true;
        }

        // GEÄNDERT: Prüft nun, ob der Spieler Operator (OP) ist, anstatt auf eine Permission.
        if (!player.isOp()) {
            player.sendMessage(Component.text("No permission! You must be OP.", NamedTextColor.RED));
            return true;
        }

        openGUI(player);
        return true;
    }

    private void openGUI(Player player) {
        Inventory gui = Bukkit.createInventory(null, 54, GUI_TITLE);

        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta meta = info.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("Guide", NamedTextColor.GOLD));
            meta.lore(List.of(
                    Component.text("Left-Click: Limit +1", NamedTextColor.GRAY),
                    Component.text("Right-Click: Limit -1", NamedTextColor.GRAY),
                    Component.text("Shift-Click: Remove Limit", NamedTextColor.GRAY),
                    Component.text("Click item in inventory: Add to list", NamedTextColor.YELLOW)
            ));
            info.setItemMeta(meta);
        }
        gui.setItem(4, info);

        int slot = 9;
        for (Map.Entry<String, Integer> entry : plugin.getLimits().entrySet()) {
            if (slot >= 54) break;

            String key = entry.getKey();
            int limit = entry.getValue();

            // Materialnamen auslesen (falls ein ":" im Namen ist, z.B. bei Tränken, nehmen wir den vorderen Teil)
            String matName = key.contains(":") ? key.split(":")[0] : key;
            Material mat = Material.matchMaterial(matName);
            if (mat == null) mat = Material.STONE;

            ItemStack item = new ItemStack(mat);
            
            // Falls es sich um ein NBT-Item wie einen Trank/Pfeil handelt, fügen wir den PotionType für die Anzeige wieder hinzu
            if (key.contains(":") && item.getItemMeta() instanceof PotionMeta pMeta) {
                try {
                    String pTypeName = key.split(":")[1];
                    PotionType pType = PotionType.valueOf(pTypeName);
                    pMeta.setBasePotionType(pType);
                    item.setItemMeta(pMeta);
                } catch (Exception ignored) {}
            }

            ItemMeta itemMeta = item.getItemMeta();
            if (itemMeta != null) {
                itemMeta.displayName(Component.text(key, NamedTextColor.AQUA));
                itemMeta.lore(List.of(
                        Component.text("Current Limit: ", NamedTextColor.GRAY)
                                .append(Component.text(limit, NamedTextColor.GREEN))
                ));
                item.setItemMeta(itemMeta);
            }
            
            item.setAmount(Math.min(limit, 64));
            if (limit < 1) item.setAmount(1);

            gui.setItem(slot++, item);
        }

        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1f, 1f);
    }

    @EventHandler
    public void onGuiClick(InventoryClickEvent event) {
        if (!event.getView().title().equals(GUI_TITLE)) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;
        
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        // Nutzt unsere NBT-Methode, um den exakten Key (z.B. POTION:STRONG_HARMING) zu generieren
        String key = ItemLimiterListener.getItemKey(clicked);
        if (key == null) return;

        if (event.getClickedInventory() == event.getView().getTopInventory()) {
            // Klick oben im GUI
            if (!plugin.getLimits().containsKey(key)) return;

            int current = plugin.getLimits().get(key);

            if (event.isShiftClick()) {
                plugin.getLimits().remove(key);
                plugin.getConfig().set("limits." + key, null);
                player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_BREAK, 1f, 1f);
            } else if (event.isRightClick()) {
                int newVal = Math.max(1, current - 1);
                plugin.getLimits().put(key, newVal);
                plugin.getConfig().set("limits." + key, newVal);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            } else if (event.isLeftClick()) {
                int newVal = current + 1;
                plugin.getLimits().put(key, newVal);
                plugin.getConfig().set("limits." + key, newVal);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            }
            plugin.saveConfig();
            openGUI(player); 
        } 
        else {
            // Klick im Spieler-Inventar: Item der Limit-Liste hinzufügen
            if (plugin.getLimits().containsKey(key)) {
                player.sendMessage(Component.text("Item is already limited!", NamedTextColor.RED));
                return;
            }
            
            plugin.getLimits().put(key, 1);
            plugin.getConfig().set("limits." + key, 1);
            plugin.saveConfig();
            
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
            openGUI(player);
        }
    }
}
