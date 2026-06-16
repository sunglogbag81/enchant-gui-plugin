package io.github.sunglogbag81.enchantgui;

import io.github.sunglogbag81.enchantgui.command.EnchantCommand;
import io.github.sunglogbag81.enchantgui.config.ConfigManager;
import io.github.sunglogbag81.enchantgui.gui.EnchantMenuHolder;
import io.github.sunglogbag81.enchantgui.listener.EnchantGuiListener;
import io.github.sunglogbag81.enchantgui.model.SupportItemDefinition;
import io.github.sunglogbag81.enchantgui.model.TokenDefinition;
import io.github.sunglogbag81.enchantgui.placeholder.EnchantGuiExpansion;
import io.github.sunglogbag81.enchantgui.service.AttemptContext;
import io.github.sunglogbag81.enchantgui.service.AttemptLogger;
import io.github.sunglogbag81.enchantgui.service.EnchantProcessor;
import io.github.sunglogbag81.enchantgui.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.PluginCommand;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class EnchantGuiPlugin extends JavaPlugin {
    private ConfigManager configManager;
    private AttemptLogger attemptLogger;
    private EnchantProcessor enchantProcessor;
    private EnchantGuiExpansion placeholderExpansion;
    private final Map<UUID, AttemptContext> lastAttempts = new HashMap<>();
    private NamespacedKey tokenKey;
    private NamespacedKey boosterKey;
    private NamespacedKey protectionKey;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        tokenKey = new NamespacedKey(this, "token-key");
        boosterKey = new NamespacedKey(this, "booster-key");
        protectionKey = new NamespacedKey(this, "protection-key");

        configManager = new ConfigManager(this);
        configManager.reload();

        attemptLogger = new AttemptLogger(this, configManager);
        attemptLogger.initialize();

        enchantProcessor = new EnchantProcessor(this, configManager, attemptLogger);

        PluginCommand command = getCommand("인챈트");
        if (command != null) {
            EnchantCommand enchantCommand = new EnchantCommand(this, configManager, enchantProcessor);
            command.setExecutor(enchantCommand);
            command.setTabCompleter(enchantCommand);
        }

        getServer().getPluginManager().registerEvents(new EnchantGuiListener(this, configManager, enchantProcessor), this);
        registerPlaceholderExpansion();
    }

    public void reloadPlugin() {
        configManager.reload();
        attemptLogger.initialize();
        registerPlaceholderExpansion();
    }

    @Override
    public void onDisable() {
        if (placeholderExpansion != null) {
            placeholderExpansion.unregister();
            placeholderExpansion = null;
        }
        if (attemptLogger != null) {
            attemptLogger.close();
        }
    }

    private void registerPlaceholderExpansion() {
        if (placeholderExpansion != null) {
            placeholderExpansion.unregister();
            placeholderExpansion = null;
        }
        if (!configManager.isPlaceholderApiEnabled()) {
            return;
        }
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            placeholderExpansion = new EnchantGuiExpansion(this);
            placeholderExpansion.register();
        }
    }

    public Inventory createMenu(Player player) {
        EnchantMenuHolder holder = new EnchantMenuHolder();
        Inventory inventory = Bukkit.createInventory(holder, configManager.getGuiSize(), configManager.getGuiTitle());
        holder.setInventory(inventory);

        ItemStack filler = new ItemStack(configManager.getFillerMaterial());
        ItemMeta fillerMeta = filler.getItemMeta();
        if (fillerMeta != null) {
            fillerMeta.setDisplayName(configManager.getFillerName());
            filler.setItemMeta(fillerMeta);
        }
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, filler);
        }

        inventory.setItem(configManager.getItemSlot(), null);
        inventory.setItem(configManager.getTokenSlot(), null);
        inventory.setItem(configManager.getBoosterSlot(), null);
        inventory.setItem(configManager.getProtectionSlot(), null);
        inventory.setItem(configManager.getApplySlot(), namedGuide(Material.EMERALD, "&#6d9dfc강화 시작", List.of("&7장비와 강화권을 넣으면 시도할 수 있습니다.")));
        inventory.setItem(configManager.getPreviewSlot(), buildWaitingPreview());
        placeGuideIfFree(inventory, configManager.getItemSlot() - 1, Material.ANVIL, "&b장비 슬롯", List.of("&7오른쪽 빈 칸에 장비를 올려두세요."));
        placeGuideIfFree(inventory, configManager.getTokenSlot() + 1, Material.PAPER, "&e강화권 슬롯", List.of("&7왼쪽 빈 칸에 강화권을 올려두세요."));
        placeGuideIfFree(inventory, configManager.getBoosterSlot() - 1, Material.GLOWSTONE_DUST, "&6확률 증가", List.of("&7보정 아이템은 선택 사항입니다."));
        placeGuideIfFree(inventory, configManager.getProtectionSlot() + 1, Material.TOTEM_OF_UNDYING, "&a보호권", List.of("&7실패 패널티를 막고 싶다면 사용하세요."));
        return inventory;
    }

    public ItemStack buildWaitingPreview() {
        return namedGuide(Material.NETHER_STAR,
                configManager.getPreviewWaitingName(),
                configManager.getPreviewWaitingLore());
    }

    public ItemStack buildReadyPreview(String tokenName,
                                       String enchantSummary,
                                       double baseChance,
                                       double bonusChance,
                                       double finalChance,
                                       String protectionLabel) {
        List<String> lore = new ArrayList<>();
        for (String line : configManager.getPreviewReadyLore()) {
            lore.add(line
                    .replace("%token%", tokenName)
                    .replace("%enchants%", enchantSummary)
                    .replace("%base_chance%", formatChance(baseChance))
                    .replace("%boost%", formatChance(bonusChance))
                    .replace("%final_chance%", formatChance(finalChance))
                    .replace("%protection%", protectionLabel));
        }
        return namedGuide(Material.ENCHANTED_BOOK, configManager.getPreviewReadyName(), lore);
    }

    public ItemStack createTokenItem(TokenDefinition token) {
        ItemStack itemStack = new ItemStack(token.material());
        ItemMeta meta = itemStack.getItemMeta();
        if (meta == null) {
            return itemStack;
        }
        meta.setDisplayName(token.displayName());
        meta.setLore(token.lore());
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(tokenKey, PersistentDataType.STRING, token.key());
        itemStack.setItemMeta(meta);
        itemStack.addUnsafeEnchantment(Enchantment.LUCK, 1);
        return itemStack;
    }

    public ItemStack createBoosterItem(SupportItemDefinition booster) {
        ItemStack itemStack = new ItemStack(booster.material());
        ItemMeta meta = itemStack.getItemMeta();
        if (meta == null) {
            return itemStack;
        }
        meta.setDisplayName(booster.displayName());
        meta.setLore(booster.lore());
        meta.getPersistentDataContainer().set(boosterKey, PersistentDataType.STRING, booster.key());
        itemStack.setItemMeta(meta);
        return itemStack;
    }

    public ItemStack createProtectionItem() {
        SupportItemDefinition protectionItem = configManager.getProtectionItem();
        if (protectionItem == null) {
            return new ItemStack(Material.TOTEM_OF_UNDYING);
        }
        ItemStack itemStack = new ItemStack(protectionItem.material());
        ItemMeta meta = itemStack.getItemMeta();
        if (meta == null) {
            return itemStack;
        }
        meta.setDisplayName(protectionItem.displayName());
        meta.setLore(protectionItem.lore());
        meta.getPersistentDataContainer().set(protectionKey, PersistentDataType.STRING, protectionItem.key());
        itemStack.setItemMeta(meta);
        return itemStack;
    }

    private void placeGuideIfFree(Inventory inventory, int slot, Material material, String name, List<String> lore) {
        if (slot < 0 || slot >= inventory.getSize()) {
            return;
        }
        if (inventory.getItem(slot) == null || inventory.getItem(slot).getType().isAir()) {
            inventory.setItem(slot, namedGuide(material, name, lore));
        }
    }

    private ItemStack namedGuide(Material material, String name, List<String> lore) {
        ItemStack itemStack = new ItemStack(material);
        ItemMeta meta = itemStack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ColorUtil.colorize(name));
            meta.setLore(ColorUtil.colorize(lore));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            itemStack.setItemMeta(meta);
        }
        return itemStack;
    }

    public String readTokenKey(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir() || !itemStack.hasItemMeta()) {
            return null;
        }
        return itemStack.getItemMeta().getPersistentDataContainer().get(tokenKey, PersistentDataType.STRING);
    }

    public String readBoosterKey(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir() || !itemStack.hasItemMeta()) {
            return null;
        }
        return itemStack.getItemMeta().getPersistentDataContainer().get(boosterKey, PersistentDataType.STRING);
    }

    public String readProtectionKey(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir() || !itemStack.hasItemMeta()) {
            return null;
        }
        return itemStack.getItemMeta().getPersistentDataContainer().get(protectionKey, PersistentDataType.STRING);
    }

    public void openMenu(Player player) {
        player.openInventory(createMenu(player));
        player.sendMessage(configManager.message("opened"));
    }

    public void storeAttempt(UUID uuid, AttemptContext context) {
        lastAttempts.put(uuid, context);
    }

    public AttemptContext getLastAttempt(UUID uuid) {
        return lastAttempts.get(uuid);
    }

    public String formatChance(double value) {
        return String.format(java.util.Locale.US, "%.2f", value);
    }

    public ConfigManager getPluginConfigManager() {
        return configManager;
    }

    public EnchantProcessor getEnchantProcessor() {
        return enchantProcessor;
    }
}
