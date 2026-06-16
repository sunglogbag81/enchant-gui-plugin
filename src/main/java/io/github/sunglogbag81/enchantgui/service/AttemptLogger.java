package io.github.sunglogbag81.enchantgui.service;

import io.github.sunglogbag81.enchantgui.config.ConfigManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.StringJoiner;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public final class AttemptLogger {
    private static final int QUEUE_CAPACITY = 4096;

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final Object lifecycleLock = new Object();
    private volatile LoggerWorker worker;

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

        LoggerWorker previous;
        synchronized (lifecycleLock) {
            previous = worker;
            worker = null;
        }
        if (previous != null) {
            previous.close();
        }

        boolean flatFileEnabled = configManager.isFlatFileLoggingEnabled();
        boolean sqliteEnabled = configManager.isSqliteLoggingEnabled();
        if (!flatFileEnabled && !sqliteEnabled) {
            return;
        }

        LoggerWorker nextWorker = new LoggerWorker(
                dataFolder.resolve(configManager.getFlatFileName()),
                dataFolder.resolve(configManager.getSqliteFile()),
                flatFileEnabled,
                sqliteEnabled
        );
        nextWorker.start();
        synchronized (lifecycleLock) {
            worker = nextWorker;
        }
    }

    public void close() {
        LoggerWorker previous;
        synchronized (lifecycleLock) {
            previous = worker;
            worker = null;
        }
        if (previous != null) {
            previous.close();
        }
    }

    public void log(AttemptLogRecord record) {
        LoggerWorker current = worker;
        if (current == null) {
            return;
        }
        if (!current.enqueue(record)) {
            plugin.getLogger().warning("EnchantGUI log queue is full; dropping enchant attempt log record.");
        }
    }

    public static AttemptLogRecord snapshot(String playerName,
                                            String playerUuid,
                                            String itemType,
                                            AttemptContext context,
                                            ValidationResult validationResult) {
        StringJoiner joiner = new StringJoiner(",");
        for (String enchant : validationResult.appliedEnchantNames()) {
            joiner.add(enchant);
        }
        return new AttemptLogRecord(
                Instant.now().toString(),
                playerName,
                playerUuid,
                itemType,
                context.tokenKey(),
                context.boosterKey(),
                context.protectionUsed(),
                context.success(),
                context.baseChance(),
                context.bonusChance(),
                context.finalChance(),
                joiner.toString()
        );
    }

    public record AttemptLogRecord(String createdAt,
                                   String playerName,
                                   String playerUuid,
                                   String itemType,
                                   String tokenKey,
                                   String boosterKey,
                                   boolean protectionUsed,
                                   boolean success,
                                   double baseChance,
                                   double bonusChance,
                                   double finalChance,
                                   String enchants) {
    }

    private final class LoggerWorker implements Runnable {
        private final Path flatFilePath;
        private final Path sqlitePath;
        private final boolean flatFileEnabled;
        private final boolean sqliteEnabled;
        private final LinkedBlockingQueue<AttemptLogRecord> queue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);
        private final Thread thread;
        private volatile boolean accepting = true;

        private Connection sqliteConnection;
        private PreparedStatement insertStatement;

        private LoggerWorker(Path flatFilePath, Path sqlitePath, boolean flatFileEnabled, boolean sqliteEnabled) {
            this.flatFilePath = flatFilePath;
            this.sqlitePath = sqlitePath;
            this.flatFileEnabled = flatFileEnabled;
            this.sqliteEnabled = sqliteEnabled;
            this.thread = new Thread(this, "EnchantGUI-Logger");
            this.thread.setDaemon(true);
        }

        private void start() {
            thread.start();
        }

        private boolean enqueue(AttemptLogRecord record) {
            if (!accepting || (!flatFileEnabled && !sqliteEnabled)) {
                return true;
            }
            return queue.offer(record);
        }

        private void close() {
            accepting = false;
            thread.interrupt();
            try {
                thread.join(10000);
                if (thread.isAlive()) {
                    plugin.getLogger().warning("EnchantGUI logger worker did not stop within 10 seconds; remaining logs may be lost.");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                plugin.getLogger().warning("Interrupted while shutting down EnchantGUI logger worker.");
            }
        }

        @Override
        public void run() {
            try {
                if (sqliteEnabled) {
                    openSqlite();
                }
                while (accepting || !queue.isEmpty()) {
                    AttemptLogRecord record;
                    try {
                        record = queue.poll(1, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        if (!accepting) {
                            break;
                        }
                        continue;
                    }
                    if (record == null) {
                        continue;
                    }
                    if (flatFileEnabled) {
                        appendFlatFile(record);
                    }
                    if (sqliteEnabled) {
                        appendSqlite(record);
                    }
                }
                AttemptLogRecord remaining;
                while ((remaining = queue.poll()) != null) {
                    if (flatFileEnabled) {
                        appendFlatFile(remaining);
                    }
                    if (sqliteEnabled) {
                        appendSqlite(remaining);
                    }
                }
            } finally {
                closeSqlite();
            }
        }

        private void appendFlatFile(AttemptLogRecord record) {
            String line = String.format(
                    "%s | player=%s | uuid=%s | item=%s | token=%s | booster=%s | protected=%s | success=%s | base=%.2f | bonus=%.2f | final=%.2f | enchants=%s%n",
                    record.createdAt(),
                    record.playerName(),
                    record.playerUuid(),
                    record.itemType(),
                    record.tokenKey(),
                    record.boosterKey(),
                    record.protectionUsed(),
                    record.success(),
                    record.baseChance(),
                    record.bonusChance(),
                    record.finalChance(),
                    record.enchants()
            );
            try {
                Files.writeString(flatFilePath, line, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to write attempt log: " + e.getMessage());
            }
        }

        private void openSqlite() {
            try {
                sqliteConnection = DriverManager.getConnection("jdbc:sqlite:" + sqlitePath.toAbsolutePath());
                try (Statement statement = sqliteConnection.createStatement()) {
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
                }
                insertStatement = sqliteConnection.prepareStatement("""
                        INSERT INTO enchant_attempts
                        (created_at, player_name, player_uuid, item_type, token_key, booster_key, protection_used, success, base_chance, bonus_chance, final_chance, enchants)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """);
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to initialize SQLite logging: " + e.getMessage());
                closeSqlite();
            }
        }

        private void appendSqlite(AttemptLogRecord record) {
            if (sqliteConnection == null || insertStatement == null) {
                return;
            }
            try {
                insertStatement.setString(1, record.createdAt());
                insertStatement.setString(2, record.playerName());
                insertStatement.setString(3, record.playerUuid());
                insertStatement.setString(4, record.itemType());
                insertStatement.setString(5, record.tokenKey());
                insertStatement.setString(6, record.boosterKey());
                insertStatement.setInt(7, record.protectionUsed() ? 1 : 0);
                insertStatement.setInt(8, record.success() ? 1 : 0);
                insertStatement.setDouble(9, record.baseChance());
                insertStatement.setDouble(10, record.bonusChance());
                insertStatement.setDouble(11, record.finalChance());
                insertStatement.setString(12, record.enchants());
                insertStatement.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to insert SQLite log row: " + e.getMessage());
            }
        }

        private void closeSqlite() {
            if (insertStatement != null) {
                try {
                    insertStatement.close();
                } catch (SQLException ignored) {
                }
                insertStatement = null;
            }
            if (sqliteConnection != null) {
                try {
                    sqliteConnection.close();
                } catch (SQLException ignored) {
                }
                sqliteConnection = null;
            }
        }
    }
}
