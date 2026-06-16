package io.github.sunglogbag81.enchantgui.service;

import io.github.sunglogbag81.enchantgui.EnchantGuiPlugin;
import io.github.sunglogbag81.enchantgui.config.ConfigManager;
import io.github.sunglogbag81.enchantgui.model.EnchantEntry;
import io.github.sunglogbag81.enchantgui.model.EnchantOperation;
import io.github.sunglogbag81.enchantgui.model.FailureSettings;
import io.github.sunglogbag81.enchantgui.model.SupportItemDefinition;
import io.github.sunglogbag81.enchantgui.model.TokenDefinition;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public final class EnchantProcessor {
    private final EnchantGuiPlugin plugin;
    private final ConfigManager configManager;
    private final AttemptLogger attemptLogger;

    public EnchantProcessor(EnchantGuiPlugin plugin, ConfigManager configManager, AttemptLogger attemptLogger) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.attemptLogger = attemptLogger;
    }

    public ValidationResult validate(Inventory inventory) {
        ItemStack target = getGuiItem(inventory, configManager.getItemSlot());
        if (isEmpty(target)) {
            return ValidationResult.error(configManager.message("invalid-item"));
        }

        TokenDefinition token = resolveToken(getGuiItem(inventory, configManager.getTokenSlot()));
        if (token == null || !token.enabled()) {
            return ValidationResult.error(configManager.message("invalid-token"));
        }

        if (!isAllowedTarget(target.getType(), token)) {
            return ValidationResult.error(configManager.message("invalid-target"));
        }

        SupportItemDefinition booster = resolveBooster(getGuiItem(inventory, configManager.getBoosterSlot()));
        SupportItemDefinition protection = resolveProtection(getGuiItem(inventory, configManager.getProtectionSlot()));

        Map<Enchantment, Integer> resultLevels = new HashMap<>();
        List<String> appliedNames = new ArrayList<>();
        boolean changed = false;
        for (EnchantEntry entry : token.enchants()) {
            Enchantment enchantment = entry.resolve().orElse(null);
            if (enchantment == null) {
                continue;
            }
            int current = target.getEnchantmentLevel(enchantment);
            int nextLevel = switch (token.operation()) {
                case ADD -> current + entry.level();
                case SET -> Math.max(current, entry.level());
            };
            if (!configManager.isAllowOverVanillaMaxLevel()) {
                nextLevel = Math.min(nextLevel, enchantment.getMaxLevel());
            }
            if (nextLevel > current) {
                changed = true;
            }
            resultLevels.put(enchantment, nextLevel);
            appliedNames.add(prettyEnchant(enchantment) + " " + formatEnchantLevel(nextLevel));
        }

        if (!changed) {
            return ValidationResult.error(configManager.message("no-effect"));
        }

        double baseChance = token.chance();
        double bonusChance = booster != null && booster.enabled() && configManager.isBoostersEnabled() ? booster.chanceBonus() : 0.0D;
        double finalChance = clamp(baseChance + bonusChance, configManager.getMinFinalChance(), configManager.getMaxFinalChance());
        String summary = String.join(", ", appliedNames);
        String protectionLabel = protection != null && protection.enabled() ? protection.displayName() : "없음";
        return new ValidationResult(true,
                "OK",
                baseChance,
                bonusChance,
                finalChance,
                summary,
                protectionLabel,
                appliedNames);
    }

    public void refreshPreview(Inventory inventory) {
        if (!configManager.isSlotInBounds(configManager.getPreviewSlot()) || configManager.getPreviewSlot() >= inventory.getSize()) {
            return;
        }
        ValidationResult result = validate(inventory);
        if (!result.ok()) {
            inventory.setItem(configManager.getPreviewSlot(), plugin.buildWaitingPreview());
            return;
        }
        TokenDefinition token = resolveToken(getGuiItem(inventory, configManager.getTokenSlot()));
        if (token == null) {
            inventory.setItem(configManager.getPreviewSlot(), plugin.buildWaitingPreview());
            return;
        }
        inventory.setItem(configManager.getPreviewSlot(), plugin.buildReadyPreview(
                token.displayName(),
                result.enchantSummary(),
                result.baseChance(),
                result.bonusChance(),
                result.finalChance(),
                result.protectionLabel()));
    }

    public void attempt(Player player, Inventory inventory) {
        ValidationResult validation = validate(inventory);
        if (!validation.ok()) {
            configManager.sendRawMessage(player, validation.message());
            return;
        }

        ItemStack item = getGuiItem(inventory, configManager.getItemSlot());
        ItemStack tokenItem = getGuiItem(inventory, configManager.getTokenSlot());
        ItemStack boosterItem = getGuiItem(inventory, configManager.getBoosterSlot());
        ItemStack protectionItem = getGuiItem(inventory, configManager.getProtectionSlot());

        TokenDefinition token = Objects.requireNonNull(resolveToken(tokenItem));
        SupportItemDefinition booster = resolveBooster(boosterItem);
        SupportItemDefinition protection = resolveProtection(protectionItem);

        boolean success = ThreadLocalRandom.current().nextDouble(100.0D) < validation.finalChance();
        boolean protectionTriggered = false;

        if (success) {
            applyToken(item, token);
            consumeIfNeeded(tokenItem, configManager.isConsumeTokenOnSuccess());
            if (booster != null) {
                consumeIfNeeded(boosterItem, booster.consumeOnSuccess());
            }
            playFeedback(player, true);
            sendResultMessage(player, true, token.displayName(), validation.finalChance());
        } else {
            FailureSettings failureSettings = token.failureSettings();
            boolean destructive = failureSettings.destroyItemOnFail()
                    || failureSettings.removeTargetEnchantsOnFail()
                    || failureSettings.downgradeTargetEnchantsOnFail() > 0;
            protectionTriggered = destructive && protection != null && protection.enabled() && configManager.isProtectionItemsEnabled();
            if (protectionTriggered) {
                configManager.sendMessage(player, "protected-fail");
                if (protection.consumeOnSuccess()) {
                    consumeIfNeeded(protectionItem, true);
                }
            } else {
                applyFailurePenalty(item, token, failureSettings);
            }
            consumeIfNeeded(tokenItem, configManager.isConsumeTokenOnFail());
            if (booster != null) {
                consumeIfNeeded(boosterItem, booster.consumeOnFail());
            }
            playFeedback(player, false);
            sendResultMessage(player, false, token.displayName(), validation.finalChance());
        }

        AttemptContext context = new AttemptContext(
                token.key(),
                booster == null ? null : booster.key(),
                protectionTriggered,
                validation.baseChance(),
                validation.bonusChance(),
                validation.finalChance(),
                success
        );
        plugin.storeAttempt(player.getUniqueId(), context);
        attemptLogger.log(AttemptLogger.snapshot(
                player.getName(),
                player.getUniqueId().toString(),
                item == null ? "AIR" : item.getType().name(),
                context,
                validation
        ));
        refreshPreview(inventory);
    }

    private void applyToken(ItemStack item, TokenDefinition token) {
        for (EnchantEntry entry : token.enchants()) {
            Enchantment enchantment = entry.resolve().orElse(null);
            if (enchantment == null) {
                continue;
            }
            int current = item.getEnchantmentLevel(enchantment);
            int nextLevel = token.operation() == EnchantOperation.ADD ? current + entry.level() : Math.max(current, entry.level());
            if (!configManager.isAllowOverVanillaMaxLevel()) {
                nextLevel = Math.min(nextLevel, enchantment.getMaxLevel());
            }
            if (nextLevel <= current) {
                continue;
            }
            if (configManager.isAllowUnsafeEnchants()) {
                item.addUnsafeEnchantment(enchantment, nextLevel);
            } else {
                item.addEnchantment(enchantment, nextLevel);
            }
        }
    }

    private void applyFailurePenalty(ItemStack item, TokenDefinition token, FailureSettings failureSettings) {
        if (failureSettings.destroyItemOnFail()) {
            item.setAmount(0);
            return;
        }
        if (failureSettings.removeTargetEnchantsOnFail()) {
            for (EnchantEntry entry : token.enchants()) {
                entry.resolve().ifPresent(item::removeEnchantment);
            }
        }
        if (failureSettings.downgradeTargetEnchantsOnFail() > 0) {
            for (EnchantEntry entry : token.enchants()) {
                Enchantment enchantment = entry.resolve().orElse(null);
                if (enchantment == null) {
                    continue;
                }
                int current = item.getEnchantmentLevel(enchantment);
                if (current <= 0) {
                    continue;
                }
                int next = Math.max(0, current - failureSettings.downgradeTargetEnchantsOnFail());
                item.removeEnchantment(enchantment);
                if (next > 0) {
                    if (configManager.isAllowUnsafeEnchants()) {
                        item.addUnsafeEnchantment(enchantment, next);
                    } else {
                        item.addEnchantment(enchantment, next);
                    }
                }
            }
        }
    }

    private void playFeedback(Player player, boolean success) {
        if (configManager.isSoundsEnabled()) {
            ConfigManager.EffectSettings soundSettings = success ? configManager.getSuccessSound() : configManager.getFailSound();
            if (soundSettings.enabled()) {
                player.playSound(player.getLocation(), soundSettings.sound(), soundSettings.volume(), soundSettings.pitch());
            }
        }
        if (configManager.isParticlesEnabled()) {
            ConfigManager.ParticleSettings particleSettings = success ? configManager.getSuccessParticle() : configManager.getFailParticle();
            if (particleSettings.enabled()) {
                player.getWorld().spawnParticle(
                        particleSettings.particle(),
                        player.getLocation().add(0.0D, 1.0D, 0.0D),
                        particleSettings.count(),
                        particleSettings.offsetX(),
                        particleSettings.offsetY(),
                        particleSettings.offsetZ(),
                        particleSettings.extra());
            }
        }
    }

    private void sendResultMessage(Player player, boolean success, String tokenName, double chance) {
        Map<String, String> replacements = Map.of(
                "%token%", tokenName,
                "%chance%", plugin.formatChance(chance)
        );
        configManager.sendMessage(player, success ? "success" : "fail", replacements);
        if ((success && configManager.isAnnounceSuccess()) || (!success && configManager.isAnnounceFailure())) {
            String resultLabel = success ? "성공" : "실패";
            String broadcast = configManager.getPrefix() + player.getName() + " -> " + tokenName + " : " + resultLabel;
            if (!broadcast.isBlank()) {
                Bukkit.broadcastMessage(broadcast);
            }
        }
    }

    private void consumeIfNeeded(ItemStack item, boolean shouldConsume) {
        if (!shouldConsume || isEmpty(item)) {
            return;
        }
        item.setAmount(item.getAmount() - 1);
        if (item.getAmount() <= 0) {
            item.setAmount(0);
        }
    }

    public TokenDefinition resolveToken(ItemStack itemStack) {
        if (isEmpty(itemStack)) {
            return null;
        }
        String tokenKey = plugin.readTokenKey(itemStack);
        if (tokenKey != null) {
            return configManager.getToken(tokenKey);
        }
        if (configManager.isRequirePdcToken() && !configManager.isAllowPlainNameFallback()) {
            return null;
        }
        if (!configManager.isAllowPlainNameFallback() || !itemStack.hasItemMeta()) {
            return null;
        }
        String displayName = itemStack.getItemMeta().getDisplayName();
        for (TokenDefinition token : configManager.getTokens().values()) {
            if (token.material() == itemStack.getType() && token.displayName().equals(displayName)) {
                return token;
            }
        }
        return null;
    }

    public SupportItemDefinition resolveBooster(ItemStack itemStack) {
        if (!configManager.isBoostersEnabled() || isEmpty(itemStack)) {
            return null;
        }
        String boosterKey = plugin.readBoosterKey(itemStack);
        if (boosterKey != null) {
            return configManager.getBooster(boosterKey);
        }
        if (!configManager.isAllowPlainNameFallback() || !itemStack.hasItemMeta()) {
            return null;
        }
        String displayName = itemStack.getItemMeta().getDisplayName();
        for (SupportItemDefinition booster : configManager.getBoosters().values()) {
            if (booster.material() == itemStack.getType() && booster.displayName().equals(displayName)) {
                return booster;
            }
        }
        return null;
    }

    public SupportItemDefinition resolveProtection(ItemStack itemStack) {
        if (!configManager.isProtectionItemsEnabled() || isEmpty(itemStack)) {
            return null;
        }
        SupportItemDefinition protection = configManager.getProtectionItem();
        if (protection == null) {
            return null;
        }
        String protectionKey = plugin.readProtectionKey(itemStack);
        if (protectionKey != null && protection.key().equalsIgnoreCase(protectionKey)) {
            return protection;
        }
        if (!configManager.isAllowPlainNameFallback() || !itemStack.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = itemStack.getItemMeta();
        return protection.material() == itemStack.getType() && protection.displayName().equals(meta.getDisplayName()) ? protection : null;
    }

    private boolean isAllowedTarget(Material material, TokenDefinition token) {
        Set<String> materials = token.targetMaterials();
        if (!materials.isEmpty() && materials.contains(material.name())) {
            return true;
        }
        if (materials.isEmpty() && token.targetGroups().isEmpty()) {
            return true;
        }
        for (String group : token.targetGroups()) {
            if (matchesGroup(material, group)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesGroup(Material material, String group) {
        String name = material.name();
        return switch (group) {
            case "SWORDS" -> name.endsWith("_SWORD");
            case "AXES" -> name.endsWith("_AXE");
            case "PICKAXES" -> name.endsWith("_PICKAXE");
            case "SHOVELS" -> name.endsWith("_SHOVEL");
            case "HOES" -> name.endsWith("_HOE");
            case "TOOLS" -> name.endsWith("_AXE") || name.endsWith("_PICKAXE") || name.endsWith("_SHOVEL") || name.endsWith("_HOE");
            case "HELMETS" -> name.endsWith("_HELMET");
            case "CHESTPLATES" -> name.endsWith("_CHESTPLATE");
            case "LEGGINGS" -> name.endsWith("_LEGGINGS");
            case "BOOTS" -> name.endsWith("_BOOTS");
            case "ARMOR" -> name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE") || name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS");
            case "BOWS" -> name.equals("BOW");
            case "CROSSBOWS" -> name.equals("CROSSBOW");
            case "TRIDENTS" -> name.equals("TRIDENT");
            case "FISHING_RODS" -> name.equals("FISHING_ROD");
            case "SHIELDS" -> name.equals("SHIELD");
            default -> false;
        };
    }

    private String prettyEnchant(Enchantment enchantment) {
        String translated = switch (enchantment.getKey().getKey()) {
            case "protection" -> "보호";
            case "fire_protection" -> "화염 보호";
            case "feather_falling" -> "가벼운 착지";
            case "blast_protection" -> "폭발 보호";
            case "projectile_protection" -> "발사체 보호";
            case "respiration" -> "호흡";
            case "aqua_affinity" -> "친수성";
            case "thorns" -> "가시";
            case "depth_strider" -> "물갈퀴";
            case "frost_walker" -> "차가운 걸음";
            case "binding_curse" -> "귀속 저주";
            case "soul_speed" -> "소울 스피드";
            case "swift_sneak" -> "신속한 잠행";
            case "sharpness" -> "날카로움";
            case "smite" -> "강타";
            case "bane_of_arthropods" -> "살충";
            case "knockback" -> "밀치기";
            case "fire_aspect" -> "발화";
            case "looting" -> "약탈";
            case "sweeping", "sweeping_edge" -> "휩쓸기";
            case "efficiency" -> "효율";
            case "silk_touch" -> "섬세한 손길";
            case "unbreaking" -> "내구성";
            case "fortune" -> "행운";
            case "power" -> "힘";
            case "punch" -> "밀어내기";
            case "flame" -> "화염";
            case "infinity" -> "무한";
            case "luck_of_the_sea" -> "바다의 행운";
            case "lure" -> "미끼";
            case "loyalty" -> "충성";
            case "impaling" -> "찌르기";
            case "riptide" -> "급류";
            case "channeling" -> "집전";
            case "multishot" -> "다중 발사";
            case "quick_charge" -> "빠른 장전";
            case "piercing" -> "관통";
            case "mending" -> "수선";
            case "vanishing_curse" -> "소실 저주";
            case "density" -> "밀집";
            case "breach" -> "파열";
            case "wind_burst" -> "돌풍";
            default -> fallbackEnchantName(enchantment);
        };
        return translated;
    }

    private String fallbackEnchantName(Enchantment enchantment) {
        String key = enchantment.getKey().getKey().replace('_', ' ');
        String[] parts = key.split(" ");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            builder.append(part.substring(0, 1).toUpperCase(Locale.ROOT))
                    .append(part.substring(1).toLowerCase(Locale.ROOT))
                    .append(' ');
        }
        return builder.toString().trim();
    }

    private String formatEnchantLevel(int level) {
        if (level <= 0) {
            return String.valueOf(level);
        }
        return switch (level) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            case 8 -> "VIII";
            case 9 -> "IX";
            case 10 -> "X";
            default -> String.valueOf(level);
        };
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private ItemStack getGuiItem(Inventory inventory, int slot) {
        if (!configManager.isSlotInBounds(slot) || slot >= inventory.getSize()) {
            return null;
        }
        return inventory.getItem(slot);
    }

    private boolean isEmpty(ItemStack itemStack) {
        return itemStack == null || itemStack.getType().isAir() || itemStack.getAmount() <= 0;
    }
}
