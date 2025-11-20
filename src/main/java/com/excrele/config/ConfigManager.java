package com.excrele.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.logging.Level;

public class ConfigManager {
    private final JavaPlugin plugin;
    private FileConfiguration config;
    private File configFile;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        configFile = new File(plugin.getDataFolder(), "config.yml");
        
        if (!configFile.exists()) {
            plugin.getDataFolder().mkdirs();
            try (InputStream is = plugin.getResource("config.yml")) {
                if (is != null) {
                    Files.copy(is, configFile.toPath());
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to create config.yml", e);
            }
        }
        
        config = YamlConfiguration.loadConfiguration(configFile);
    }

    public void reloadConfig() {
        config = YamlConfiguration.loadConfiguration(configFile);
    }

    public FileConfiguration getConfig() {
        return config;
    }

    // Database settings
    public String getDatabaseType() {
        return config.getString("database.type", "sqlite");
    }

    public String getSqliteFilename() {
        return config.getString("database.sqlite.filename", "secureauth.db");
    }

    public String getMysqlHost() {
        return config.getString("database.mysql.host", "localhost");
    }

    public int getMysqlPort() {
        return config.getInt("database.mysql.port", 3306);
    }

    public String getMysqlDatabase() {
        return config.getString("database.mysql.database", "secureauth");
    }

    public String getMysqlUsername() {
        return config.getString("database.mysql.username", "root");
    }

    public String getMysqlPassword() {
        return config.getString("database.mysql.password", "password");
    }

    public int getMysqlPoolSize() {
        return config.getInt("database.mysql.pool-size", 5);
    }

    public long getMysqlMaxLifetime() {
        return config.getLong("database.mysql.max-lifetime", 1800000);
    }

    // Security settings
    public String getHashAlgorithm() {
        return config.getString("security.hash-algorithm", "bcrypt");
    }

    public int getBcryptCostFactor() {
        return config.getInt("security.bcrypt.cost-factor", 10);
    }

    public int getArgon2Memory() {
        return config.getInt("security.argon2.memory", 65536);
    }

    public int getArgon2Iterations() {
        return config.getInt("security.argon2.iterations", 3);
    }

    public int getArgon2Parallelism() {
        return config.getInt("security.argon2.parallelism", 4);
    }

    public int getMinPasswordLength() {
        return config.getInt("security.min-password-length", 4);
    }

    public boolean isComplexityRequired() {
        return config.getBoolean("security.require-complexity", false);
    }

    public int getMaxAttempts() {
        return config.getInt("security.max-attempts", 3);
    }

    public long getLockoutDurationMinutes() {
        return config.getLong("security.lockout-duration-minutes", 5);
    }

    public long getAttemptResetMinutes() {
        return config.getLong("security.attempt-reset-minutes", 5);
    }

    public boolean isIpLimitsEnabled() {
        return config.getBoolean("security.enable-ip-limits", true);
    }

    public boolean isProgressiveLockouts() {
        return config.getBoolean("security.progressive-lockouts", true);
    }

    // 2FA settings
    public boolean is2FAEnabled() {
        return config.getBoolean("security.two-factor-auth.enabled", false);
    }

    public boolean is2FARequired() {
        return config.getBoolean("security.two-factor-auth.required", false);
    }

    public String get2FATOTPIssuer() {
        return config.getString("security.two-factor-auth.totp-issuer", "SecureAuth Server");
    }

    public int get2FABackupCodesCount() {
        return config.getInt("security.two-factor-auth.backup-codes-count", 10);
    }

    // Session settings
    public long getSessionTimeoutMinutes() {
        return config.getLong("session.timeout-minutes", 30);
    }

    public long getCheckIntervalSeconds() {
        return config.getLong("session.check-interval-seconds", 60);
    }

    // Premium settings
    public boolean isPremiumAutoLogin() {
        return config.getBoolean("premium.auto-login", true);
    }

    public int getApiTimeoutMs() {
        return config.getInt("premium.api-timeout-ms", 5000);
    }

    public boolean isFailSafeCracked() {
        return config.getBoolean("premium.fail-safe-cracked", true);
    }

    // Restrictions
    public boolean isBlockChat() {
        return config.getBoolean("restrictions.block-chat", true);
    }

    public boolean isBlockMovement() {
        return config.getBoolean("restrictions.block-movement", true);
    }

    public boolean isBlockBuilding() {
        return config.getBoolean("restrictions.block-building", true);
    }

    public boolean isBlockMining() {
        return config.getBoolean("restrictions.block-mining", true);
    }

    public boolean isBlockInteractions() {
        return config.getBoolean("restrictions.block-interactions", true);
    }

    // Messages
    public String getMessage(String key, String defaultValue) {
        String message = config.getString("messages." + key, defaultValue);
        String prefix = config.getString("messages.prefix", "&8[&6SecureAuth&8]&r ");
        return prefix + message.replace("&", "§");
    }

    public String getMessage(String key) {
        return getMessage(key, "");
    }

    // Logging settings
    public boolean shouldLogLogins() {
        return config.getBoolean("logging.log-logins", true);
    }

    public boolean shouldLogFailedAttempts() {
        return config.getBoolean("logging.log-failed-attempts", true);
    }

    public boolean shouldLogPasswordChanges() {
        return config.getBoolean("logging.log-password-changes", true);
    }

    public boolean shouldLogAdminActions() {
        return config.getBoolean("logging.log-admin-actions", true);
    }
}

