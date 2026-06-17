package io.github.sunglogbag81.enchantgui.config;

import io.github.sunglogbag81.enchantgui.model.EnchantEntry;
import io.github.sunglogbag81.enchantgui.model.EnchantOperation;
import io.github.sunglogbag81.enchantgui.model.FailureSettings;
import io.github.sunglogbag81.enchantgui.model.ProtectionItemDefinition;
import io.github.sunglogbag81.enchantgui.model.SupportItemDefinition;
import io.github.sunglogbag81.enchantgui.model.TokenDefinition;
import io.github.sunglogbag81.enchantgui.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class ConfigManager {
    private final JavaPlugin plugin;

    private final Map<String, TokenDefinition> tokens = new LinkedHashMap<>();
    private final Map<String, SupportItemDefinition> boosters = new LinkedHashMap<>();
    private String prefix;
    private String guiTitle;
    private int guiSize;
    private int itemSlot;
    private int tokenSlot;
    private int boosterSlot;
    private int protectionSlot;
    private int previewSlot;
    private int applySlot;
    private Material fillerMaterial;
    private String fillerName;
    private boolean soundsEnabled;
    private boolean particlesEnabled;
    private boolean previewLoreEnabled;
    private boolean closeReturnItems;
    private boolean blockShiftMoveIntoGui;
    private boolean requirePermission;
    private boolean allowUnsafeEnchants;
    private boolean allowOverVanillaMaxLevel;
    private boolean requirePdcToken;
    private boolean allowPlainNameFallback;
    private boolean boostersEnabled;
    private boolean protectionItemsEnabled;
    private boolean multiEnchantTokensEnabled;
    private boolean flatFileLoggingEnabled;
    private boolean sqliteLoggingEnabled;
    private boolean placeholderApiEnabled;
    private boolean citizensEnabled;
    private boolean citizensRequirePermission;
    private boolean announceSuccess;
    private boolean announceFailure;
    private double minFinalChance;
    private double maxFinalChance;
    private boolean consumeTokenOnSuccess;
    private boolean consumeTokenOnFail;
    private boolean consumeBoosterOnSuccess;
    private boolean consumeBoosterOnFail;
    private boolean consumeProtectionOnTrigger;
    private boolean destroyItemOnFail;
    private boolean removeTargetEnchantsOnFail;
    private int downgradeTargetEnchantsOnFail;
    private ProtectionItemDefinition protectionItem;
    private String previewWaitingName;
    private List<String> previewWaitingLore;
    private String previewReadyName;
    private List<String> previewReadyLore;
    private String flatFileName;
    private String sqliteFile;
    private final Set<Integer> citizensNpcIds = new LinkedHashSet<>();

    private EffectSettings successSound;
    private EffectSettings failSound;
    private ParticleSettings successParticle;
    private ParticleSettings failParticle;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();
        tokens.clear();
        boosters.clear();

        prefix = optionalText(config, "messages.prefix");
        guiTitle = ColorUtil.colorize(config.getString("gui.title", "&b인챈트 제작소"));
        guiSize = normalizeSize(config.getInt("gui.size", 27));
        itemSlot = config.getInt("gui.item-slot", 11);
        tokenSlot = config.getInt("gui.token-slot", 15);
        boosterSlot = config.getInt("gui.booster-slot", 20);
        protectionSlot = config.getInt("gui.protection-slot", 24);
        previewSlot = config.getInt("gui.preview-slot", 13);
        applySlot = config.getInt("gui.apply-slot", 22);
        warnIfSlotOutsideGui("gui.item-slot", itemSlot);
        warnIfSlotOutsideGui("gui.token-slot", tokenSlot);
        warnIfSlotOutsideGui("gui.booster-slot", boosterSlot);
        warnIfSlotOutsideGui("gui.protection-slot", protectionSlot);
        warnIfSlotOutsideGui("gui.preview-slot", previewSlot);
        warnIfSlotOutsideGui("gui.apply-slot", applySlot);
        fillerMaterial = material(config.getString("gui.filler-material"), Material.BLACK_STAINED_GLASS_PANE);
        fillerName = ColorUtil.colorize(config.getString("gui.filler-name", " "));

        soundsEnabled = config.getBoolean("features.sounds", true);
        particlesEnabled = config.getBoolean("features.particles", true);
        previewLoreEnabled = config.getBoolean("features.preview-lore", true);
        closeReturnItems = config.getBoolean("features.close-return-items", true);
        blockShiftMoveIntoGui = config.getBoolean("features.block-shift-move-into-gui", true);
        requirePermission = config.getBoolean("features.require-permission", true);
        allowUnsafeEnchants = config.getBoolean("features.allow-unsafe-enchants", false);
        allowOverVanillaMaxLevel = config.getBoolean("features.allow-over-vanilla-max-level", false);
        requirePdcToken = config.getBoolean("features.require-pdc-token", true);
        allowPlainNameFallback = config.getBoolean("features.allow-plain-name-fallback", false);
        boostersEnabled = config.getBoolean("features.enable-boosters", true);
        protectionItemsEnabled = config.getBoolean("features.enable-protection-items", true);
        multiEnchantTokensEnabled = config.getBoolean("features.enable-multi-enchant-tokens", true);
        flatFileLoggingEnabled = config.getBoolean("features.enable-flat-file-logging", true);
        sqliteLoggingEnabled = config.getBoolean("features.enable-sqlite-logging", true);
        placeholderApiEnabled = config.getBoolean("features.enable-placeholderapi", true);
        citizensEnabled = config.getBoolean("citizens.enabled", false);
        citizensRequirePermission = config.getBoolean("citizens.require-permission", true);
        announceSuccess = config.getBoolean("features.announce-success", false);
        announceFailure = config.getBoolean("features.announce-failure", false);

        minFinalChance = config.getDouble("chance.min-final", 0.0D);
        maxFinalChance = config.getDouble("chance.max-final", 100.0D);

        consumeTokenOnSuccess = config.getBoolean("failure-defaults.consume-token-on-success", true);
        consumeTokenOnFail = config.getBoolean("failure-defaults.consume-token-on-fail", true);
        consumeBoosterOnSuccess = config.getBoolean("failure-defaults.consume-booster-on-success", true);
        consumeBoosterOnFail = config.getBoolean("failure-defaults.consume-booster-on-fail", true);
        consumeProtectionOnTrigger = config.getBoolean("failure-defaults.consume-protection-on-trigger", true);
        destroyItemOnFail = config.getBoolean("failure-defaults.destroy-item-on-fail", false);
        removeTargetEnchantsOnFail = config.getBoolean("failure-defaults.remove-target-enchants-on-fail", false);
        downgradeTargetEnchantsOnFail = config.getInt("failure-defaults.downgrade-target-enchants-on-fail", 0);

        successSound = loadEffect(config, "sounds.success", Sound.ENTITY_PLAYER_LEVELUP);
        failSound = loadEffect(config, "sounds.fail", Sound.ENTITY_VILLAGER_NO);
        successParticle = loadParticle(config, "particles.success", Particle.FIREWORKS_SPARK);
        failParticle = loadParticle(config, "particles.fail", Particle.SMOKE_NORMAL);

        previewWaitingName = ColorUtil.colorize(config.getString("preview.waiting-name", "&7강화 정보 대기중"));
        previewWaitingLore = ColorUtil.colorize(config.getStringList("preview.waiting-lore"));
        previewReadyName = ColorUtil.colorize(config.getString("preview.ready-name", "&b강화 미리보기"));
        previewReadyLore = ColorUtil.colorize(config.getStringList("preview.ready-lore"));

        flatFileName = config.getString("logging.flat-file-name", "attempts.log");
        sqliteFile = config.getString("logging.sqlite-file", "enchantgui.db");
        citizensNpcIds.clear();
        for (Object rawId : config.getList("citizens.npc-ids", List.of())) {
            Integer npcId = parseOptionalInt(rawId);
            if (npcId != null && npcId >= 0) {
                citizensNpcIds.add(npcId);
            }
        }

        loadProtection(config.getConfigurationSection("protection-item"));
        loadTokens(config.getConfigurationSection("tokens"));
        loadBoosters(config.getConfigurationSection("boosters"));
    }

    private void loadProtection(ConfigurationSection section) {
        if (section == null) {
            protectionItem = null;
            return;
        }
        protectionItem = new ProtectionItemDefinition(
                section.getString("key", "basic_guard"),
                section.getBoolean("enabled", true),
                material(section.getString("material"), Material.TOTEM_OF_UNDYING),
                ColorUtil.colorize(section.getString("display-name", "&e강화 보호권")),
                ColorUtil.colorize(section.getStringList("lore")),
                section.getBoolean("consume-on-success", false),
                section.getBoolean("consume-on-fail", false),
                section.getBoolean("consume-on-trigger", consumeProtectionOnTrigger),
                section.getBoolean("protect-destroy-on-fail", true),
                section.getBoolean("protect-remove-target-enchants-on-fail", true),
                section.getBoolean("protect-downgrade-target-enchants-on-fail", true)
        );
    }

    private void loadTokens(ConfigurationSection section) {
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection tokenSection = section.getConfigurationSection(key);
            if (tokenSection == null) {
                continue;
            }
            List<EnchantEntry> entries = new ArrayList<>();
            for (Map<?, ?> map : tokenSection.getMapList("enchants")) {
                Object id = map.get("id");
                Object level = map.get("level");
                if (id == null || level == null) {
                    continue;
                }
                entries.add(new EnchantEntry(String.valueOf(id), Math.max(1, parseInt(level, 1))));
            }
            if (entries.isEmpty()) {
                continue;
            }
            if (!multiEnchantTokensEnabled && entries.size() > 1) {
                entries = List.of(entries.get(0));
            }
            FailureSettings failureSettings = new FailureSettings(
                    tokenSection.getBoolean("failure.destroy-item-on-fail", destroyItemOnFail),
                    tokenSection.getBoolean("failure.remove-target-enchants-on-fail", removeTargetEnchantsOnFail),
                    tokenSection.getInt("failure.downgrade-target-enchants-on-fail", downgradeTargetEnchantsOnFail)
            );
            TokenDefinition definition = new TokenDefinition(
                    key,
                    tokenSection.getBoolean("enabled", true),
                    material(tokenSection.getString("material"), Material.PAPER),
                    ColorUtil.colorize(tokenSection.getString("display-name", key)),
                    ColorUtil.colorize(tokenSection.getStringList("lore")),
                    tokenSection.getDouble("chance", 100.0D),
                    EnchantOperation.fromString(tokenSection.getString("operation", "SET")),
                    normalizeSet(tokenSection.getStringList("target-groups")),
                    normalizeSet(tokenSection.getStringList("target-materials")),
                    failureSettings,
                    Collections.unmodifiableList(entries)
            );
            tokens.put(key.toLowerCase(Locale.ROOT), definition);
        }
    }

    private void loadBoosters(ConfigurationSection section) {
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection boosterSection = section.getConfigurationSection(key);
            if (boosterSection == null) {
                continue;
            }
            SupportItemDefinition booster = new SupportItemDefinition(
                    key,
                    boosterSection.getBoolean("enabled", true),
                    material(boosterSection.getString("material"), Material.GLOWSTONE_DUST),
                    ColorUtil.colorize(boosterSection.getString("display-name", key)),
                    ColorUtil.colorize(boosterSection.getStringList("lore")),
                    boosterSection.getDouble("chance-bonus", 0.0D),
                    boosterSection.getBoolean("consume-on-success", consumeBoosterOnSuccess),
                    boosterSection.getBoolean("consume-on-fail", consumeBoosterOnFail)
            );
            boosters.put(key.toLowerCase(Locale.ROOT), booster);
        }
    }

    private EffectSettings loadEffect(FileConfiguration config, String path, Sound fallback) {
        return new EffectSettings(
                config.getBoolean(path + ".enabled", true),
                sound(config.getString(path + ".sound"), fallback),
                (float) config.getDouble(path + ".volume", 1.0D),
                (float) config.getDouble(path + ".pitch", 1.0D)
        );
    }

    private ParticleSettings loadParticle(FileConfiguration config, String path, Particle fallback) {
        return new ParticleSettings(
                config.getBoolean(path + ".enabled", true),
                particle(config.getString(path + ".particle"), fallback),
                config.getInt(path + ".count", 20),
                config.getDouble(path + ".offset-x", 0.3D),
                config.getDouble(path + ".offset-y", 0.4D),
                config.getDouble(path + ".offset-z", 0.3D),
                config.getDouble(path + ".extra", 0.01D)
        );
    }

    private int normalizeSize(int raw) {
        int value = Math.max(9, raw);
        return Math.min(54, ((value + 8) / 9) * 9);
    }

    private Material material(String raw, Material fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        Material material = Material.matchMaterial(raw.trim().toUpperCase(Locale.ROOT));
        return material == null ? fallback : material;
    }

    private Sound sound(String raw, Sound fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Sound.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            Bukkit.getLogger().warning("[EnchantGUI] Unknown sound in config: " + raw);
            return fallback;
        }
    }

    private Particle particle(String raw, Particle fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Particle.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            Bukkit.getLogger().warning("[EnchantGUI] Unknown particle in config: " + raw);
            return fallback;
        }
    }

    private Set<String> normalizeSet(List<String> values) {
        Set<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                normalized.add(value.trim().toUpperCase(Locale.ROOT));
            }
        }
        return Collections.unmodifiableSet(normalized);
    }

    private int parseInt(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private Integer parseOptionalInt(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public String message(String key) {
        FileConfiguration config = plugin.getConfig();
        String path = "messages." + key;
        if (!config.contains(path)) {
            return null;
        }
        String raw = config.getString(path);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return normalizePercents(prefix + ColorUtil.colorize(raw));
    }

    public String message(String key, Map<String, String> replacements) {
        String value = message(key);
        if (value == null) {
            return null;
        }
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            value = value.replace(entry.getKey(), entry.getValue());
        }
        return normalizePercents(value);
    }

    public void sendMessage(CommandSender sender, String key) {
        sendRawMessage(sender, message(key));
    }

    public void sendMessage(CommandSender sender, String key, Map<String, String> replacements) {
        sendRawMessage(sender, message(key, replacements));
    }

    public void sendRawMessage(CommandSender sender, String message) {
        if (sender == null || message == null || message.isBlank()) {
            return;
        }
        sender.sendMessage(ColorUtil.colorize(message));
    }

    public boolean isSlotInBounds(int slot) {
        return slot >= 0 && slot < guiSize;
    }

    private String optionalText(FileConfiguration config, String path) {
        String raw = config.getString(path);
        if (raw == null || raw.isBlank()) {
            return "";
        }
        return ColorUtil.colorize(raw);
    }

    private void warnIfSlotOutsideGui(String path, int slot) {
        if (!isSlotInBounds(slot)) {
            plugin.getLogger().warning(path + "=" + slot + " is outside gui.size=" + guiSize + "; that slot will be ignored.");
        }
    }

    private String normalizePercents(String value) {
        String normalized = Objects.requireNonNullElse(value, "");
        while (normalized.contains("%%")) {
            normalized = normalized.replace("%%", "%");
        }
        return normalized;
    }

    public Map<String, TokenDefinition> getTokens() {
        return Collections.unmodifiableMap(tokens);
    }

    public Map<String, SupportItemDefinition> getBoosters() {
        return Collections.unmodifiableMap(boosters);
    }

    public TokenDefinition getToken(String key) {
        return key == null ? null : tokens.get(key.toLowerCase(Locale.ROOT));
    }

    public SupportItemDefinition getBooster(String key) {
        return key == null ? null : boosters.get(key.toLowerCase(Locale.ROOT));
    }

    public String getPrefix() { return prefix; }
    public String getGuiTitle() { return guiTitle; }
    public int getGuiSize() { return guiSize; }
    public int getItemSlot() { return itemSlot; }
    public int getTokenSlot() { return tokenSlot; }
    public int getBoosterSlot() { return boosterSlot; }
    public int getProtectionSlot() { return protectionSlot; }
    public int getPreviewSlot() { return previewSlot; }
    public int getApplySlot() { return applySlot; }
    public Material getFillerMaterial() { return fillerMaterial; }
    public String getFillerName() { return fillerName; }
    public boolean isSoundsEnabled() { return soundsEnabled; }
    public boolean isParticlesEnabled() { return particlesEnabled; }
    public boolean isPreviewLoreEnabled() { return previewLoreEnabled; }
    public boolean isCloseReturnItems() { return closeReturnItems; }
    public boolean isBlockShiftMoveIntoGui() { return blockShiftMoveIntoGui; }
    public boolean isRequirePermission() { return requirePermission; }
    public boolean isAllowUnsafeEnchants() { return allowUnsafeEnchants; }
    public boolean isAllowOverVanillaMaxLevel() { return allowOverVanillaMaxLevel; }
    public boolean isRequirePdcToken() { return requirePdcToken; }
    public boolean isAllowPlainNameFallback() { return allowPlainNameFallback; }
    public boolean isBoostersEnabled() { return boostersEnabled; }
    public boolean isProtectionItemsEnabled() { return protectionItemsEnabled; }
    public boolean isMultiEnchantTokensEnabled() { return multiEnchantTokensEnabled; }
    public boolean isFlatFileLoggingEnabled() { return flatFileLoggingEnabled; }
    public boolean isSqliteLoggingEnabled() { return sqliteLoggingEnabled; }
    public boolean isPlaceholderApiEnabled() { return placeholderApiEnabled; }
    public boolean isCitizensEnabled() { return citizensEnabled; }
    public boolean isCitizensRequirePermission() { return citizensRequirePermission; }
    public Set<Integer> getCitizensNpcIds() { return Collections.unmodifiableSet(citizensNpcIds); }
    public boolean isAnnounceSuccess() { return announceSuccess; }
    public boolean isAnnounceFailure() { return announceFailure; }
    public double getMinFinalChance() { return minFinalChance; }
    public double getMaxFinalChance() { return maxFinalChance; }
    public boolean isConsumeTokenOnSuccess() { return consumeTokenOnSuccess; }
    public boolean isConsumeTokenOnFail() { return consumeTokenOnFail; }
    public boolean isConsumeBoosterOnSuccess() { return consumeBoosterOnSuccess; }
    public boolean isConsumeBoosterOnFail() { return consumeBoosterOnFail; }
    public boolean isConsumeProtectionOnTrigger() { return consumeProtectionOnTrigger; }
    public boolean isDestroyItemOnFail() { return destroyItemOnFail; }
    public boolean isRemoveTargetEnchantsOnFail() { return removeTargetEnchantsOnFail; }
    public int getDowngradeTargetEnchantsOnFail() { return downgradeTargetEnchantsOnFail; }
    public ProtectionItemDefinition getProtectionItem() { return protectionItem; }
    public String getPreviewWaitingName() { return previewWaitingName; }
    public List<String> getPreviewWaitingLore() { return previewWaitingLore; }
    public String getPreviewReadyName() { return previewReadyName; }
    public List<String> getPreviewReadyLore() { return previewReadyLore; }
    public String getFlatFileName() { return flatFileName; }
    public String getSqliteFile() { return sqliteFile; }
    public EffectSettings getSuccessSound() { return successSound; }
    public EffectSettings getFailSound() { return failSound; }
    public ParticleSettings getSuccessParticle() { return successParticle; }
    public ParticleSettings getFailParticle() { return failParticle; }

    public record EffectSettings(boolean enabled, Sound sound, float volume, float pitch) {
    }

    public record ParticleSettings(boolean enabled, Particle particle, int count, double offsetX, double offsetY, double offsetZ, double extra) {
    }
}
