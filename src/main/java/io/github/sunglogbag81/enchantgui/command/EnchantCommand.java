package io.github.sunglogbag81.enchantgui.command;

import io.github.sunglogbag81.enchantgui.EnchantGuiPlugin;
import io.github.sunglogbag81.enchantgui.config.ConfigManager;
import io.github.sunglogbag81.enchantgui.model.SupportItemDefinition;
import io.github.sunglogbag81.enchantgui.model.TokenDefinition;
import io.github.sunglogbag81.enchantgui.service.EnchantProcessor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class EnchantCommand implements CommandExecutor, TabCompleter {
    private final EnchantGuiPlugin plugin;
    private final ConfigManager configManager;
    private final EnchantProcessor enchantProcessor;

    public EnchantCommand(EnchantGuiPlugin plugin, ConfigManager configManager, EnchantProcessor enchantProcessor) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.enchantProcessor = enchantProcessor;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(configManager.message("player-only"));
                return true;
            }
            if (configManager.isRequirePermission() && !player.hasPermission("enchantgui.use")) {
                player.sendMessage(configManager.message("no-permission"));
                return true;
            }
            plugin.openMenu(player);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "reload" -> handleReload(sender);
            case "list" -> handleList(sender);
            case "give" -> handleGive(sender, args);
            default -> {
                sender.sendMessage(configManager.message("usage-admin"));
                yield true;
            }
        };
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("enchantgui.reload") && !sender.hasPermission("enchantgui.admin")) {
            sender.sendMessage(configManager.message("no-permission"));
            return true;
        }
        plugin.reloadPlugin();
        sender.sendMessage(configManager.message("config-reloaded"));
        return true;
    }

    private boolean handleList(CommandSender sender) {
        if (!sender.hasPermission("enchantgui.list") && !sender.hasPermission("enchantgui.admin")) {
            sender.sendMessage(configManager.message("no-permission"));
            return true;
        }
        sender.sendMessage(configManager.getPrefix() + "&f토큰: " + String.join(", ", configManager.getTokens().keySet()));
        sender.sendMessage(configManager.getPrefix() + "&f부스터: " + String.join(", ", configManager.getBoosters().keySet()));
        if (configManager.getProtectionItem() != null) {
            sender.sendMessage(configManager.getPrefix() + "&f보호권: " + configManager.getProtectionItem().key());
        }
        return true;
    }

    private boolean handleGive(CommandSender sender, String[] args) {
        if (!sender.hasPermission("enchantgui.give") && !sender.hasPermission("enchantgui.admin")) {
            sender.sendMessage(configManager.message("no-permission"));
            return true;
        }
        if (args.length < 4) {
            sender.sendMessage(configManager.message("usage-admin"));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(configManager.getPrefix() + "&c플레이어를 찾을 수 없습니다: " + args[1]);
            return true;
        }
        String type = args[2].toLowerCase(Locale.ROOT);
        String key = args[3];
        int amount = 1;
        if (args.length >= 5) {
            try {
                amount = Math.max(1, Integer.parseInt(args[4]));
            } catch (NumberFormatException ignored) {
            }
        }

        switch (type) {
            case "token" -> {
                TokenDefinition token = configManager.getToken(key);
                if (token == null) {
                    sender.sendMessage(configManager.message("unknown-entry", Map.of("%key%", key)));
                    return true;
                }
                var item = plugin.createTokenItem(token);
                item.setAmount(amount);
                target.getInventory().addItem(item);
                sender.sendMessage(configManager.message("token-given", Map.of(
                        "%player%", target.getName(),
                        "%token%", token.displayName(),
                        "%amount%", String.valueOf(amount)
                )));
            }
            case "booster" -> {
                SupportItemDefinition booster = configManager.getBooster(key);
                if (booster == null) {
                    sender.sendMessage(configManager.message("unknown-entry", Map.of("%key%", key)));
                    return true;
                }
                var item = plugin.createBoosterItem(booster);
                item.setAmount(amount);
                target.getInventory().addItem(item);
                sender.sendMessage(configManager.message("booster-given", Map.of(
                        "%player%", target.getName(),
                        "%booster%", booster.displayName(),
                        "%amount%", String.valueOf(amount)
                )));
            }
            case "protection" -> {
                if (configManager.getProtectionItem() == null) {
                    sender.sendMessage(configManager.getPrefix() + "&c보호권이 비활성화되어 있습니다.");
                    return true;
                }
                var item = plugin.createProtectionItem();
                item.setAmount(amount);
                target.getInventory().addItem(item);
                sender.sendMessage(configManager.message("protection-given", Map.of(
                        "%player%", target.getName(),
                        "%protection%", configManager.getProtectionItem().displayName(),
                        "%amount%", String.valueOf(amount)
                )));
            }
            default -> sender.sendMessage(configManager.message("usage-admin"));
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return filter(List.of("reload", "list", "give"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            return filter(List.of("token", "booster", "protection"), args[2]);
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("give")) {
            return switch (args[2].toLowerCase(Locale.ROOT)) {
                case "token" -> filter(new ArrayList<>(configManager.getTokens().keySet()), args[3]);
                case "booster" -> filter(new ArrayList<>(configManager.getBoosters().keySet()), args[3]);
                case "protection" -> filter(List.of(configManager.getProtectionItem() == null ? "basic_guard" : configManager.getProtectionItem().key()), args[3]);
                default -> List.of();
            };
        }
        return List.of();
    }

    private List<String> filter(List<String> values, String input) {
        String lowered = input.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lowered)).toList();
    }
}
