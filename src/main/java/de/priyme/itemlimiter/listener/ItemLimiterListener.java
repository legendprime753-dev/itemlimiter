package de.priyme.itemlimiter.listener;

import de.priyme.itemlimiter.ItemLimiter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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

        // Try specific key like SPLASH_POTION:STRONG_HARMING
        if (plugin.getLimits().containsKey(key)) {
            return plugin.getLimits().get(key);
        }
        
        // Try fallback to just material like SPLASH_POTION
        if (key.contains(":") && plugin.getLimits().containsKey(item.getType().name())) {
            return plugin.getLimits().get(item.getType().name());
        }

        return -1;
    }

    private boolean isSameLimitedItem(ItemStack item1, ItemStack item2) {
        String key1 = getItemKey(item1);
        String key2 = getItemKey(item2);
        
        if (key1 == null || key2 == null) return false;
        
        // Items are considered the same if their limit keys exactly match
        // Example: SPLASH_POTION:STRONG_HARMING == SPLASH_POTION:STRONG_HARMING
        return key1.equals(key2);
    }

    private int countSimilarItems(Player player, ItemStack reference) {
        int count = 0;
        // Inventory Content
        for (ItemStack item : player.getInventory().getContents()) {
            if (isSameLimitedItem(item, reference)) count += item.getAmount();
        }
        // Cursor
        ItemStack cursor = player.getItemOnCursor();
        if (isSameLimitedItem(cursor, reference)) count += cursor.getAmount();

        // Crafting Grid
        Inventory topInv = player.getOpenInventory().getTopInventory();
        if (topInv instanceof CraftingInventory) {
            for (ItemStack item : ((CraftingInventory) topInv).getMatrix()) {
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

    // --- INTERACTION BLOCKS (Limit = 0) ---

    @EventHandler
    public void onConsume(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();
        if (getLimit(item) == 0) {
            event.setCancelled(true);
            sendFeedback(event.getPlayer(), "This potion is blocked!");
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

    // --- LIMIT CHECKS ---

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

        // Smart Split Logic
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

        if (event.getClickedInventory() instanceof CraftingInventory && event.getSlotType() == InventoryType.SlotType.CRAFTING) {
            ItemStack cursor = event.getCursor();
            if (getLimit(cursor) != -1) {
                int limit = getLimit(cursor);
                int current = countSimilarItems(player, cursor);
                if (current > limit) {
                    event.setCancelled(true);
                    sendFeedback(player, "Limit reached (Crafting)!");
                    return;
                }
            }
        }

        ItemStack currentItem = event.getCurrentItem();
        ItemStack cursor = event.getCursor();

        ItemStack matToCheck = null;
        if (getLimit(cursor) != -1) matToCheck = cursor;
        else if (getLimit(currentItem) != -1) matToCheck = currentItem;

        if (matToCheck == null) return;

        int limit = getLimit(matToCheck);
        int currentCount = countSimilarItems(player, matToCheck);

        Inventory clickedInv = event.getClickedInventory();
        boolean takingFromExternal = (clickedInv != null && clickedInv != player.getInventory() && !(clickedInv instanceof CraftingInventory));

        if (currentCount >= limit && takingFromExternal) {
            event.setCancelled(true);
            sendFeedback(player, "Limit reached!");
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

        Set<ItemStack> checked = new HashSet<>();

        for (int i = 0; i <= player.getInventory().getSize(); i++) {
            ItemStack item = i == player.getInventory().getSize() ? player.getItemOnCursor() : player.getInventory().getItem(i);
            if (item == null || item.getType() == Material.AIR) continue;
            
            int limit = getLimit(item);
            if (limit == -1) continue;

            boolean alreadyChecked = false;
            for (ItemStack c : checked) {
                if (isSameLimitedItem(c, item)) { alreadyChecked = true; break; }
            }
            if (alreadyChecked) continue;

            checked.add(item.clone());

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
