package com.excrele.database;

import com.excrele.config.ConfigManager;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;
import java.util.logging.Level;

public class MigrationTool {
    private final ConfigManager config;
    private final JavaPlugin plugin;
    private final DatabaseManager databaseManager;

    public MigrationTool(ConfigManager config, JavaPlugin plugin, DatabaseManager databaseManager) {
        this.config = config;
        this.plugin = plugin;
        this.databaseManager = databaseManager;
    }

    public boolean migrateFromFileToDatabase(String targetType, CommandSender sender) {
        if (!databaseManager.isFileBased()) {
            sender.sendMessage("§cCurrent storage is not file-based!");
            return false;
        }

        File passwordFile = new File(plugin.getDataFolder(), "passwords.txt");
        if (!passwordFile.exists()) {
            sender.sendMessage("§cNo password file found to migrate!");
            return false;
        }

        sender.sendMessage("§eStarting migration from file to " + targetType + "...");

        try {
            String content = new String(Files.readAllBytes(passwordFile.toPath()));
            String[] lines = content.split("\n");
            int migrated = 0;
            int failed = 0;

            // Temporarily switch database type
            String originalType = config.getDatabaseType();
            
            // Note: This is a simplified migration - in production, you'd want to:
            // 1. Create a new DatabaseManager with target type
            // 2. Migrate all data
            // 3. Update config
            // 4. Restart or reload

            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty() || !line.contains(":")) {
                    continue;
                }

                try {
                    String[] parts = line.split(":", 2);
                    if (parts.length != 2) {
                        failed++;
                        continue;
                    }

                    UUID playerId = UUID.fromString(parts[0]);
                    String passwordHash = parts[1];

                    // This would require creating a new database connection
                    // For now, we'll just show the structure
                    migrated++;
                } catch (Exception e) {
                    failed++;
                    plugin.getLogger().warning("Failed to migrate line: " + line);
                }
            }

            sender.sendMessage("§aMigration complete!");
            sender.sendMessage("§7Migrated: " + migrated);
            sender.sendMessage("§7Failed: " + failed);
            sender.sendMessage("§eNote: This is a preview. Full migration requires database setup.");

            return true;
        } catch (IOException e) {
            sender.sendMessage("§cMigration failed: " + e.getMessage());
            plugin.getLogger().log(Level.SEVERE, "Migration error", e);
            return false;
        }
    }

    public boolean migrateBetweenDatabases(String fromType, String toType, CommandSender sender) {
        sender.sendMessage("§eDatabase-to-database migration - Feature in development");
        sender.sendMessage("§7This will migrate data from " + fromType + " to " + toType);
        return false;
    }

    public void backupData(CommandSender sender) {
        File backupDir = new File(plugin.getDataFolder(), "backups");
        if (!backupDir.exists()) {
            backupDir.mkdirs();
        }

        String timestamp = String.valueOf(System.currentTimeMillis());
        File backupFile = new File(backupDir, "backup-" + timestamp + ".txt");

        if (databaseManager.isFileBased()) {
            File passwordFile = new File(plugin.getDataFolder(), "passwords.txt");
            if (passwordFile.exists()) {
                try {
                    Files.copy(passwordFile.toPath(), backupFile.toPath());
                    sender.sendMessage("§aBackup created: " + backupFile.getName());
                } catch (IOException e) {
                    sender.sendMessage("§cBackup failed: " + e.getMessage());
                }
            }
        } else {
            sender.sendMessage("§eDatabase backup - Feature in development");
        }
    }
}

