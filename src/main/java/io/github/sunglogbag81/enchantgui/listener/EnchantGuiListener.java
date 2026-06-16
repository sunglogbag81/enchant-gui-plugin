package io.github.sunglogbag81.enchantgui.listener;

import io.github.sunglogbag81.enchantgui.EnchantGuiPlugin;
import io.github.sunglogbag81.enchantgui.config.ConfigManager;
import io.github.sunglogbag81.enchantgui.gui.EnchantMenuHolder;
import io.github.sunglogbag81.enchantgui.service.EnchantProcessor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public final class EnchantGuiListener implements Listener {
    private final EnchantGuiPlugin plugin;
    private final ConfigManager configManager;
    private final EnchantProcessor enchantProcessor;

    public EnchantGuiListener(EnchantGuiPlugin plugin, ConfigManager configManager, EnchantProcessor enchantProcessor) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.enchantProcessor = enchantProcessor;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof EnchantMenuHolder)) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        int rawSlot = event.getRawSlot();
        int topSize = top.getSize();
        boolean topInventory = rawSlot >= 0 && rawSlot < topSize;

        if (topInventory) {
            int slot = event.getSlot();
            if (slot == configManager.getApplySlot()) {
                event.setCancelled(true);
                if (event.getWhoClicked() instanceof Player player) {
                    enchantProcessor.attempt(player, top);
                }
                return;
            }
            if (slot == configManager.getPreviewSlot()) {
                event.setCancelled(true);
                return;
            }
            if (!isAllowedInputSlot(slot)) {
                event.setCancelled(true);
                return;
            }
        } else if (event.isShiftClick() && configManager.isBlockShiftMoveIntoGui()) {
            event.setCancelled(true);
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> enchantProcessor.refreshPreview(top));
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof EnchantMenuHolder)) {
            return;
        }
        int topSize = event.getView().getTopInventory().getSize();
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < topSize && !isAllowedInputSlot(rawSlot)) {
                event.setCancelled(true);
                return;
            }
        }
        Bukkit.getScheduler().runTask(plugin, () -> enchantProcessor.refreshPreview(event.getView().getTopInventory()));
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof EnchantMenuHolder)) {
            return;
        }
        if (!(event.getPlayer() instanceof Player player) || !configManager.isCloseReturnItems()) {
            return;
        }
        boolean returned = false;
        for (int slot : inputSlots()) {
            ItemStack item = event.getInventory().getItem(slot);
            if (item == null || item.getType().isAir()) {
                continue;
            }
            event.getInventory().setItem(slot, null);
            player.getInventory().addItem(item).values().forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
            returned = true;
        }
        if (returned) {
            player.sendMessage(configManager.message("returned-items"));
        }
    }

    private boolean isAllowedInputSlot(int slot) {
        return slot == configManager.getItemSlot()
                || slot == configManager.getTokenSlot()
                || slot == configManager.getBoosterSlot()
                || slot == configManager.getProtectionSlot();
    }

    private int[] inputSlots() {
        return new int[]{
                configManager.getItemSlot(),
                configManager.getTokenSlot(),
                configManager.getBoosterSlot(),
                configManager.getProtectionSlot()
        };
    }
}
