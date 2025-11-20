package com.excrele.database;

import com.excrele.config.ConfigManager;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.logging.Level;

public class DatabaseManager {
    private final ConfigManager config;
    private final JavaPlugin plugin;
    private HikariDataSource dataSource;
    private String databaseType;

    public DatabaseManager(ConfigManager config, JavaPlugin plugin) {
        this.config = config;
        this.plugin = plugin;
        this.databaseType = config.getDatabaseType().toLowerCase();
        initializeDatabase();
    }

    private void initializeDatabase() {
        try {
            switch (databaseType) {
                case "sqlite":
                    setupSQLite();
                    break;
                case "mysql":
                    setupMySQL();
                    break;
                case "file":
                    plugin.getLogger().info("Using file-based storage (default, no database required)");
                    return;
                default:
                    plugin.getLogger().warning("Unknown database type: " + databaseType + ", defaulting to file-based storage");
                    return;
            }
            
            createTables();
            plugin.getLogger().info("Database initialized successfully (" + databaseType + ")");
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize database", e);
        }
    }

    private void setupSQLite() {
        HikariConfig hikariConfig = new HikariConfig();
        File dbFile = new File(plugin.getDataFolder(), config.getSqliteFilename());
        hikariConfig.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
        hikariConfig.setDriverClassName("org.sqlite.JDBC");
        hikariConfig.setMaximumPoolSize(1); // SQLite doesn't need multiple connections
        hikariConfig.setConnectionTimeout(30000);
        hikariConfig.setIdleTimeout(600000);
        hikariConfig.setMaxLifetime(1800000);
        
        dataSource = new HikariDataSource(hikariConfig);
    }

    private void setupMySQL() {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(
            "jdbc:mysql://" + config.getMysqlHost() + ":" + config.getMysqlPort() + "/" + config.getMysqlDatabase()
        );
        hikariConfig.setUsername(config.getMysqlUsername());
        hikariConfig.setPassword(config.getMysqlPassword());
        hikariConfig.setDriverClassName("com.mysql.cj.jdbc.Driver");
        hikariConfig.setMaximumPoolSize(config.getMysqlPoolSize());
        hikariConfig.setConnectionTimeout(30000);
        hikariConfig.setIdleTimeout(600000);
        hikariConfig.setMaxLifetime(config.getMysqlMaxLifetime());
        hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
        hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
        hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        
        dataSource = new HikariDataSource(hikariConfig);
    }

    private void createTables() throws SQLException {
        String createTableSQL;
        String create2FATableSQL;
        
        if ("sqlite".equals(databaseType)) {
            createTableSQL = "CREATE TABLE IF NOT EXISTS secureauth_passwords (" +
                "uuid VARCHAR(36) PRIMARY KEY, " +
                "password_hash TEXT NOT NULL, " +
                "created_at BIGINT NOT NULL, " +
                "last_changed BIGINT NOT NULL" +
                ")";
            create2FATableSQL = "CREATE TABLE IF NOT EXISTS secureauth_2fa (" +
                "uuid VARCHAR(36) PRIMARY KEY, " +
                "secret_key TEXT NOT NULL, " +
                "backup_codes TEXT, " +
                "enabled_at BIGINT NOT NULL" +
                ")";
        } else {
            createTableSQL = "CREATE TABLE IF NOT EXISTS secureauth_passwords (" +
                "uuid VARCHAR(36) PRIMARY KEY, " +
                "password_hash TEXT NOT NULL, " +
                "created_at BIGINT NOT NULL, " +
                "last_changed BIGINT NOT NULL" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
            create2FATableSQL = "CREATE TABLE IF NOT EXISTS secureauth_2fa (" +
                "uuid VARCHAR(36) PRIMARY KEY, " +
                "secret_key TEXT NOT NULL, " +
                "backup_codes TEXT, " +
                "enabled_at BIGINT NOT NULL" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
        }
        
        try (Connection conn = getConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement(createTableSQL)) {
                stmt.execute();
            }
            try (PreparedStatement stmt = conn.prepareStatement(create2FATableSQL)) {
                stmt.execute();
            }
            if ("sqlite".equals(databaseType)) {
                String createRecoveryTableSQL = "CREATE TABLE IF NOT EXISTS secureauth_recovery (" +
                    "uuid VARCHAR(36) PRIMARY KEY, " +
                    "security_question TEXT, " +
                    "security_answer TEXT" +
                    ")";
                try (PreparedStatement stmt = conn.prepareStatement(createRecoveryTableSQL)) {
                    stmt.execute();
                }
            } else {
                String createRecoveryTableSQL = "CREATE TABLE IF NOT EXISTS secureauth_recovery (" +
                    "uuid VARCHAR(36) PRIMARY KEY, " +
                    "security_question TEXT, " +
                    "security_answer TEXT" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
                try (PreparedStatement stmt = conn.prepareStatement(createRecoveryTableSQL)) {
                    stmt.execute();
                }
            }
        }
    }

    public Connection getConnection() throws SQLException {
        if (dataSource == null) {
            throw new SQLException("Database not initialized");
        }
        return dataSource.getConnection();
    }

    public boolean hasPassword(UUID playerId) {
        if ("file".equals(databaseType)) {
            return false; // File-based handled separately
        }
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT uuid FROM secureauth_passwords WHERE uuid = ?")) {
            stmt.setString(1, playerId.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to check password for " + playerId, e);
            return false;
        }
    }

    public String getPasswordHash(UUID playerId) {
        if ("file".equals(databaseType)) {
            return null; // File-based handled separately
        }
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT password_hash FROM secureauth_passwords WHERE uuid = ?")) {
            stmt.setString(1, playerId.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("password_hash");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to get password hash for " + playerId, e);
        }
        return null;
    }

    public void setPassword(UUID playerId, String passwordHash) {
        if ("file".equals(databaseType)) {
            return; // File-based handled separately
        }
        
        long now = System.currentTimeMillis();
        
        try (Connection conn = getConnection()) {
            // Check if exists
            boolean exists = hasPassword(playerId);
            
            if (exists) {
                // Update
                try (PreparedStatement stmt = conn.prepareStatement(
                    "UPDATE secureauth_passwords SET password_hash = ?, last_changed = ? WHERE uuid = ?")) {
                    stmt.setString(1, passwordHash);
                    stmt.setLong(2, now);
                    stmt.setString(3, playerId.toString());
                    stmt.executeUpdate();
                }
            } else {
                // Insert
                try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO secureauth_passwords (uuid, password_hash, created_at, last_changed) VALUES (?, ?, ?, ?)")) {
                    stmt.setString(1, playerId.toString());
                    stmt.setString(2, passwordHash);
                    stmt.setLong(3, now);
                    stmt.setLong(4, now);
                    stmt.executeUpdate();
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to set password for " + playerId, e);
        }
    }

    public void deletePassword(UUID playerId) {
        if ("file".equals(databaseType)) {
            return; // File-based handled separately
        }
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement("DELETE FROM secureauth_passwords WHERE uuid = ?")) {
            stmt.setString(1, playerId.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to delete password for " + playerId, e);
        }
    }

    // 2FA methods
    public String get2FASecret(UUID playerId) {
        if ("file".equals(databaseType)) {
            return null;
        }
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT secret_key FROM secureauth_2fa WHERE uuid = ?")) {
            stmt.setString(1, playerId.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("secret_key");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to get 2FA secret for " + playerId, e);
        }
        return null;
    }

    public void set2FASecret(UUID playerId, String secret) {
        if ("file".equals(databaseType)) {
            return;
        }
        
        long now = System.currentTimeMillis();
        
        try (Connection conn = getConnection()) {
            boolean exists = get2FASecret(playerId) != null;
            
            if (exists) {
                try (PreparedStatement stmt = conn.prepareStatement(
                    "UPDATE secureauth_2fa SET secret_key = ? WHERE uuid = ?")) {
                    stmt.setString(1, secret);
                    stmt.setString(2, playerId.toString());
                    stmt.executeUpdate();
                }
            } else {
                try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO secureauth_2fa (uuid, secret_key, enabled_at) VALUES (?, ?, ?)")) {
                    stmt.setString(1, playerId.toString());
                    stmt.setString(2, secret);
                    stmt.setLong(3, now);
                    stmt.executeUpdate();
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to set 2FA secret for " + playerId, e);
        }
    }

    public void delete2FASecret(UUID playerId) {
        if ("file".equals(databaseType)) {
            return;
        }
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement("DELETE FROM secureauth_2fa WHERE uuid = ?")) {
            stmt.setString(1, playerId.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to delete 2FA secret for " + playerId, e);
        }
    }

    public List<String> get2FABackupCodes(UUID playerId) {
        if ("file".equals(databaseType)) {
            return null;
        }
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT backup_codes FROM secureauth_2fa WHERE uuid = ?")) {
            stmt.setString(1, playerId.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String codes = rs.getString("backup_codes");
                    if (codes != null && !codes.isEmpty()) {
                        return new ArrayList<>(Arrays.asList(codes.split(",")));
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to get 2FA backup codes for " + playerId, e);
        }
        return new ArrayList<>();
    }

    public void set2FABackupCodes(UUID playerId, List<String> codes) {
        if ("file".equals(databaseType)) {
            return;
        }
        
        String codesStr = String.join(",", codes);
        
        try (Connection conn = getConnection()) {
            boolean exists = get2FASecret(playerId) != null;
            
            if (exists) {
                try (PreparedStatement stmt = conn.prepareStatement(
                    "UPDATE secureauth_2fa SET backup_codes = ? WHERE uuid = ?")) {
                    stmt.setString(1, codesStr);
                    stmt.setString(2, playerId.toString());
                    stmt.executeUpdate();
                }
            } else {
                // If no 2FA exists, create entry with just backup codes
                try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO secureauth_2fa (uuid, secret_key, backup_codes, enabled_at) VALUES (?, ?, ?, ?)")) {
                    stmt.setString(1, playerId.toString());
                    stmt.setString(2, ""); // Empty secret
                    stmt.setString(3, codesStr);
                    stmt.setLong(4, System.currentTimeMillis());
                    stmt.executeUpdate();
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to set 2FA backup codes for " + playerId, e);
        }
    }

    // Security question methods
    public boolean hasSecurityQuestion(UUID playerId) {
        if ("file".equals(databaseType)) {
            return false;
        }
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT uuid FROM secureauth_recovery WHERE uuid = ?")) {
            stmt.setString(1, playerId.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to check security question for " + playerId, e);
            return false;
        }
    }

    public String getSecurityQuestion(UUID playerId) {
        if ("file".equals(databaseType)) {
            return null;
        }
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT security_question FROM secureauth_recovery WHERE uuid = ?")) {
            stmt.setString(1, playerId.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("security_question");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to get security question for " + playerId, e);
        }
        return null;
    }

    public String getSecurityAnswer(UUID playerId) {
        if ("file".equals(databaseType)) {
            return null;
        }
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT security_answer FROM secureauth_recovery WHERE uuid = ?")) {
            stmt.setString(1, playerId.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("security_answer");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to get security answer for " + playerId, e);
        }
        return null;
    }

    public void setSecurityQuestion(UUID playerId, String question, String answer) {
        if ("file".equals(databaseType)) {
            return;
        }
        
        try (Connection conn = getConnection()) {
            boolean exists = hasSecurityQuestion(playerId);
            
            if (exists) {
                try (PreparedStatement stmt = conn.prepareStatement(
                    "UPDATE secureauth_recovery SET security_question = ?, security_answer = ? WHERE uuid = ?")) {
                    stmt.setString(1, question);
                    stmt.setString(2, answer.toLowerCase().trim());
                    stmt.setString(3, playerId.toString());
                    stmt.executeUpdate();
                }
            } else {
                try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO secureauth_recovery (uuid, security_question, security_answer) VALUES (?, ?, ?)")) {
                    stmt.setString(1, playerId.toString());
                    stmt.setString(2, question);
                    stmt.setString(3, answer.toLowerCase().trim());
                    stmt.executeUpdate();
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to set security question for " + playerId, e);
        }
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    public boolean isFileBased() {
        return "file".equals(databaseType);
    }
}

