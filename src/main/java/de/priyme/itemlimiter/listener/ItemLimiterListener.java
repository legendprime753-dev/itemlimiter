package de.priyme.itemlimiter.listener;

import de.priyme.itemlimiter.ItemLimiter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.LingeringPotionSplashEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public class ItemLimiterListener implements Listener {

    private final ItemLimiter plugin;

    public ItemLimiterListener(ItemLimiter plugin) {
        this.plugin = plugin;
    }

    public static String getItemKey(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return null;
        Material mat = item.getType();
        
        if (mat == Material.POTION || mat == Material.SPLASH_POTION || mat == Material.LINGERING_POTION || mat == Material.TIPPED_ARROW) {
            if (item.getItemMeta() instanceof PotionMeta meta) {
                return mat.name() + ":" + meta.getBasePotionType().name();
            }
        }
        return mat.name();
    }

    private int getLimit(ItemStack item) {
        String key = getItemKey(item);
        if (key == null) return -1;

        if (plugin.getLimits().containsKey(key)) {
            return plugin.getLimits().get(key);
        }
        
        if (key.contains(":") && plugin.getLimits().containsKey(item.getType().name())) {
            return plugin.getLimits().get(item.getType().name());
        }

        return -1;
    }

    private boolean isSameLimitedItem(ItemStack item1, ItemStack item2) {
        String key1 = getItemKey(item1);
        String key2 = getItemKey(item2);
        if (key1 == null || key2 == null) return false;
        return key1.equals(key2);
    }

    private int countSimilarItems(Player player, ItemStack reference) {
        int count = 0;
        // 1. Inventar
        for (ItemStack item : player.getInventory().getContents()) {
            if (isSameLimitedItem(item, reference)) count += item.getAmount();
        }
        
        // 2. Mauszeiger (Cursor)
        ItemStack cursor = player.getItemOnCursor();
        if (isSameLimitedItem(cursor, reference)) count += cursor.getAmount();

        // 3. FIX: Crafting-Feld aktiv überwachen (auch das 2x2 Feld im eigenen Inventar)
        InventoryView view = player.getOpenInventory();
        if (view.getType() == InventoryType.CRAFTING) {
            Inventory topInv = view.getTopInventory();
            for (int i = 1; i <= 4; i++) { // 1 bis 4 ist das 2x2 Crafting-Feld
                ItemStack item = topInv.getItem(i);
                if (isSameLimitedItem(item, reference)) count += item.getAmount();
            }
        } else if (view.getType() == InventoryType.WORKBENCH) {
            Inventory topInv = view.getTopInventory();
            for (int i = 1; i <= 9; i++) {
                ItemStack item = topInv.getItem(i);
                if (isSameLimitedItem(item, reference)) count += item.getAmount();
            }
        }

        return count;
    }

    private void sendFeedback(Player player, String message) {
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
        player.getWorld().spawnParticle(org.bukkit.Particle.SMOKE, player.getLocation().add(0, 1, 0), 10, 0.2, 0.2, 0.2, 0.0);
        player.sendActionBar(Component.text(message, NamedTextColor.RED));
    }

    @EventHandler
    public void onConsume(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();
        if (getLimit(item) == 0) {
            event.setCancelled(true);
            sendFeedback(event.getPlayer(), "This item is blocked!");
        }
    }

    @EventHandler
    public void onSplash(PotionSplashEvent event) {
        if (event.getEntity().getShooter() instanceof Player player) {
            if (getLimit(event.getEntity().getItem()) == 0) {
                event.setCancelled(true);
                sendFeedback(player, "This potion is blocked!");
            }
        }
    }

    @EventHandler
    public void onLingeringSplash(LingeringPotionSplashEvent event) {
        if (event.getEntity().getShooter() instanceof Player player) {
            if (getLimit(event.getEntity().getItem()) == 0) {
                event.setCancelled(true);
                sendFeedback(player, "This potion is blocked!");
            }
        }
    }

    @EventHandler
    public void onShootBow(EntityShootBowEvent event) {
        if (event.getEntity() instanceof Player player) {
            ItemStack arrow = event.getConsumable();
            if (arrow != null && getLimit(arrow) == 0) {
                event.setCancelled(true);
                sendFeedback(player, "This arrow is blocked!");
            }
        }
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!plugin.isWorldEnabled(player.getWorld().getName())) return;

        Item itemEntity = event.getItem();
        ItemStack stack = itemEntity.getItemStack();

        int limit = getLimit(stack);
        if (limit == -1) return;

        int currentAmount = countSimilarItems(player, stack);

        if (currentAmount >= limit) {
            event.setCancelled(true);
            sendFeedback(player, "Limit reached!");
            return;
        }

        int spaceLeft = limit - currentAmount;
        if (stack.getAmount() > spaceLeft) {
            event.setCancelled(true);
            ItemStack toGive = stack.clone();
            toGive.setAmount(spaceLeft);
            HashMap<Integer, ItemStack> leftovers = player.getInventory().addItem(toGive);
            if (!leftovers.isEmpty()) {
                for (ItemStack leftover : leftovers.values()) {
                    player.getWorld().dropItem(player.getLocation(), leftover);
                }
            }
            stack.setAmount(stack.getAmount() - spaceLeft);
            itemEntity.setItemStack(stack);
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 1f);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!plugin.isWorldEnabled(player.getWorld().getName())) return;

        ItemStack currentItem = event.getCurrentItem();
        ItemStack cursor = event.getCursor();

        // 1. FIX BUNDLE BYPASS: Limitierten Items verbieten in ein Bundle zu gehen
        boolean currentIsBundle = currentItem != null && currentItem.getItemMeta() instanceof org.bukkit.inventory.meta.BundleMeta;
        boolean cursorIsBundle = cursor != null && cursor.getItemMeta() instanceof org.bukkit.inventory.meta.BundleMeta;
        
        if ((currentIsBundle && cursor != null && getLimit(cursor) != -1) || 
            (cursorIsBundle && currentItem != null && getLimit(currentItem) != -1)) {
            event.setCancelled(true);
            sendFeedback(player, "You cannot put limited items in bundles!");
            return;
        }

        // 2. FIX SHIFT-CLICK: Logik um externes looten zu verhindern
        if (event.isShiftClick() && currentItem != null && getLimit(currentItem) != -1) {
            Inventory clickedInv = event.getClickedInventory();
            if (clickedInv != null && clickedInv != player.getInventory() && !(clickedInv instanceof CraftingInventory)) {
                int limit = getLimit(currentItem);
                int currentCount = countSimilarItems(player, currentItem);
                
                if (currentCount >= limit) {
                    event.setCancelled(true);
                    sendFeedback(player, "Limit reached!");
                    return;
                }
                
                int space = limit - currentCount;
                if (currentItem.getAmount() > space) {
                    event.setCancelled(true);
                    ItemStack toMove = currentItem.clone();
                    toMove.setAmount(space);
                    HashMap<Integer, ItemStack> leftovers = player.getInventory().addItem(toMove);
                    if (leftovers.isEmpty()) {
                        currentItem.setAmount(currentItem.getAmount() - space);
                    }
                    sendFeedback(player, "Limit reached!");
                    return;
                }
            }
        }

        // 3. FIX HOTBAR SWAP (Tasten 1-9):
        if (event.getClick() == org.bukkit.event.inventory.ClickType.NUMBER_KEY) {
            Inventory clickedInv = event.getClickedInventory();
            if (clickedInv != null && clickedInv != player.getInventory() && !(clickedInv instanceof CraftingInventory)) {
                if (currentItem != null && getLimit(currentItem) != -1) {
                    ItemStack hotbarItem = player.getInventory().getItem(event.getHotbarButton());
                    int limit = getLimit(currentItem);
                    int currentCount = countSimilarItems(player, currentItem);
                    
                    if (isSameLimitedItem(hotbarItem, currentItem)) {
                        currentCount -= hotbarItem.getAmount();
                    }
                    
                    if (currentCount + currentItem.getAmount() > limit) {
                        event.setCancelled(true);
                        sendFeedback(player, "Limit reached!");
                        return;
                    }
                }
            }
        }

        if (event.getClickedInventory() instanceof CraftingInventory && event.getSlotType() == InventoryType.SlotType.CRAFTING) {
            if (cursor != null && getLimit(cursor) != -1) {
                int limit = getLimit(cursor);
                int current = countSimilarItems(player, cursor);
                if (current > limit) {
                    event.setCancelled(true);
                    sendFeedback(player, "Limit reached (Crafting)!");
                    return;
                }
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!plugin.isWorldEnabled(player.getWorld().getName())) return;

        boolean involvesCrafting = event.getInventory() instanceof CraftingInventory;
        if (!involvesCrafting) return;

        ItemStack dragged = event.getOldCursor();
        if (getLimit(dragged) == -1) return;

        for (int slot : event.getRawSlots()) {
             int limit = getLimit(dragged);
             int current = countSimilarItems(player, dragged);
             if (current > limit) {
                 event.setCancelled(true);
                 sendFeedback(player, "Limit reached!");
                 return;
             }
        }
    }

    @EventHandler
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!plugin.isWorldEnabled(player.getWorld().getName())) return;

        ItemStack result = event.getRecipe().getResult();
        int limit = getLimit(result);

        if (limit != -1) {
            int currentCount = countSimilarItems(player, result);
            if (currentCount + result.getAmount() > limit) {
                event.setCancelled(true);
                sendFeedback(player, "Limit reached via Crafting!");
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!plugin.isWorldEnabled(player.getWorld().getName())) return;

        // FIX ON CLOSE: Wenn man ESC drückt, fallen die Items aus dem Crafting-Grid ins Inventar.
        // Das passiert aber erst NACHDEM das Event triggert. 
        // 1-Tick Delay sorgt dafür, dass wir danach prüfen und alles Überschüssige sicher droppen.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            enforceLimits(player);
        }, 1L);
    }

    private void enforceLimits(Player player) {
        Set<String> checkedKeys = new HashSet<>();

        for (int i = 0; i <= player.getInventory().getSize(); i++) {
            ItemStack item = i == player.getInventory().getSize() ? player.getItemOnCursor() : player.getInventory().getItem(i);
            if (item == null || item.getType() == Material.AIR) continue;
            
            String key = getItemKey(item);
            if (key == null || checkedKeys.contains(key)) continue;

            int limit = getLimit(item);
            if (limit == -1) continue;

            checkedKeys.add(key);

            int totalCount = countSimilarItems(player, item);
            
            if (totalCount > limit) {
                int amountToRemove = totalCount - limit;

                ItemStack dropStack = item.clone();
                dropStack.setAmount(amountToRemove);
                player.getWorld().dropItem(player.getLocation(), dropStack);
                
                ItemStack cursor = player.getItemOnCursor();
                if (isSameLimitedItem(cursor, item)) {
                    if (cursor.getAmount() <= amountToRemove) {
                        amountToRemove -= cursor.getAmount();
                        player.setItemOnCursor(null); 
                    } else {
                        cursor.setAmount(cursor.getAmount() - amountToRemove);
                        amountToRemove = 0;
                    }
                }

                if (amountToRemove > 0) {
                    for (int j = 0; j < player.getInventory().getSize(); j++) {
                        ItemStack invItem = player.getInventory().getItem(j);
                        if (isSameLimitedItem(invItem, item)) {
                            if (invItem.getAmount() <= amountToRemove) {
                                amountToRemove -= invItem.getAmount();
                                player.getInventory().setItem(j, null);
                            } else {
                                invItem.setAmount(invItem.getAmount() - amountToRemove);
                                amountToRemove = 0;
                            }
                        }
                        if (amountToRemove <= 0) break;
                    }
                }

                player.sendMessage(Component.text("Excess items were dropped!", NamedTextColor.RED));
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);
            }
        }
    }
}
