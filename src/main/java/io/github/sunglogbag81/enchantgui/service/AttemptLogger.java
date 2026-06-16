package io.github.sunglogbag81.enchantgui.service;

import io.github.sunglogbag81.enchantgui.config.ConfigManager;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;

public final class AttemptLogger {
    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private Path flatFilePath;
    private Path sqlitePath;

    public AttemptLogger(JavaPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    public void initialize() {
        Path dataFolder = plugin.getDataFolder().toPath();
        try {
            Files.createDirectories(dataFolder);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not create plugin data folder for logging: " + e.getMessage());
        }
        flatFilePath = dataFolder.resolve(configManager.getFlatFileName());
        sqlitePath = dataFolder.resolve(configManager.getSqliteFile());
        if (configManager.isSqliteLoggingEnabled()) {
            ensureSqlite();
        }
    }

    public void log(Player player,
                    ItemStack item,
                    AttemptContext context,
                    ValidationResult validationResult) {
        if (configManager.isFlatFileLoggingEnabled()) {
            appendFlatFile(player, item, context, validationResult);
        }
        if (configManager.isSqliteLoggingEnabled()) {
            appendSqlite(player, item, context, validationResult);
        }
    }

    private void appendFlatFile(Player player,
                                ItemStack item,
                                AttemptContext context,
                                ValidationResult validationResult) {
        String line = String.format(
                "%s | player=%s | uuid=%s | item=%s | token=%s | booster=%s | protected=%s | success=%s | base=%.2f | bonus=%.2f | final=%.2f | enchants=%s%n",
                Instant.now(),
                player.getName(),
                player.getUniqueId(),
                item.getType(),
                context.tokenKey(),
                context.boosterKey(),
                context.protectionUsed(),
                context.success(),
                context.baseChance(),
                context.bonusChance(),
                context.finalChance(),
                String.join(",", validationResult.appliedEnchantNames())
        );
        try {
            Files.writeString(flatFilePath, line, StandardCharsets.UTF_8,
                    Files.exists(flatFilePath) ? java.nio.file.StandardOpenOption.APPEND : java.nio.file.StandardOpenOption.CREATE);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to write attempt log: " + e.getMessage());
        }
    }

    private void ensureSqlite() {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + sqlitePath.toAbsolutePath());
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS enchant_attempts (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        created_at TEXT NOT NULL,
                        player_name TEXT NOT NULL,
                        player_uuid TEXT NOT NULL,
                        item_type TEXT NOT NULL,
                        token_key TEXT NOT NULL,
                        booster_key TEXT,
                        protection_used INTEGER NOT NULL,
                        success INTEGER NOT NULL,
                        base_chance REAL NOT NULL,
                        bonus_chance REAL NOT NULL,
                        final_chance REAL NOT NULL,
                        enchants TEXT NOT NULL
                    )
                    """);
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to initialize SQLite logging: " + e.getMessage());
        }
    }

    private void appendSqlite(Player player,
                              ItemStack item,
                              AttemptContext context,
                              ValidationResult validationResult) {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + sqlitePath.toAbsolutePath());
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO enchant_attempts
                     (created_at, player_name, player_uuid, item_type, token_key, booster_key, protection_used, success, base_chance, bonus_chance, final_chance, enchants)
                     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                     """)) {
            statement.setString(1, Instant.now().toString());
            statement.setString(2, player.getName());
            statement.setString(3, player.getUniqueId().toString());
            statement.setString(4, item.getType().name());
            statement.setString(5, context.tokenKey());
            statement.setString(6, context.boosterKey());
            statement.setInt(7, context.protectionUsed() ? 1 : 0);
            statement.setInt(8, context.success() ? 1 : 0);
            statement.setDouble(9, context.baseChance());
            statement.setDouble(10, context.bonusChance());
            statement.setDouble(11, context.finalChance());
            statement.setString(12, String.join(",", validationResult.appliedEnchantNames()));
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to insert SQLite log row: " + e.getMessage());
        }
    }
}
