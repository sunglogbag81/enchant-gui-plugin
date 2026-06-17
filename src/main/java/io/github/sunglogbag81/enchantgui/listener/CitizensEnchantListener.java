package io.github.sunglogbag81.enchantgui.listener;

import io.github.sunglogbag81.enchantgui.EnchantGuiPlugin;
import io.github.sunglogbag81.enchantgui.config.ConfigManager;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public final class CitizensEnchantListener implements Listener {
    private final EnchantGuiPlugin plugin;
    private final ConfigManager configManager;

    public CitizensEnchantListener(EnchantGuiPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onNpcRightClick(NPCRightClickEvent event) {
        if (!configManager.isCitizensEnabled()) {
            return;
        }
        if (!configManager.getCitizensNpcIds().contains(event.getNPC().getId())) {
            return;
        }
        Player player = event.getClicker();
        if (configManager.isCitizensRequirePermission() && configManager.isRequirePermission() && !player.hasPermission("enchantgui.use")) {
            configManager.sendMessage(player, "no-permission");
            return;
        }
        plugin.openMenu(player);
    }
}
