package io.github.sunglogbag81.enchantgui.placeholder;

import io.github.sunglogbag81.enchantgui.EnchantGuiPlugin;
import io.github.sunglogbag81.enchantgui.service.AttemptContext;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

public final class EnchantGuiExpansion extends PlaceholderExpansion {
    private final EnchantGuiPlugin plugin;

    public EnchantGuiExpansion(EnchantGuiPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "enchantgui";
    }

    @Override
    public @NotNull String getAuthor() {
        return "sunglogbag81";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null || !player.isOnline()) {
            return "";
        }
        AttemptContext context = plugin.getLastAttempt(player.getUniqueId());
        if (context == null) {
            return switch (params.toLowerCase(Locale.ROOT)) {
                case "last_result" -> "NONE";
                case "last_token", "last_booster" -> "-";
                default -> "0";
            };
        }
        return switch (params.toLowerCase(Locale.ROOT)) {
            case "last_result" -> context.success() ? "SUCCESS" : "FAIL";
            case "last_token" -> context.tokenKey();
            case "last_booster" -> context.boosterKey() == null ? "-" : context.boosterKey();
            case "last_base_chance" -> format(context.baseChance());
            case "last_bonus_chance" -> format(context.bonusChance());
            case "last_final_chance" -> format(context.finalChance());
            default -> null;
        };
    }

    private String format(double value) {
        return String.format(Locale.US, "%.2f", value);
    }
}
