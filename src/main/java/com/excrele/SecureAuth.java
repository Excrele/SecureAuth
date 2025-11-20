package com.excrele;

import org.bukkit.plugin.java.JavaPlugin;

import com.excrele.auth.AuthManager;
import com.excrele.auth.PasswordManager;
import com.excrele.auth.PasswordRecoveryManager;
import com.excrele.auth.RateLimitManager;
import com.excrele.auth.SessionManager;
import com.excrele.auth.TwoFactorAuthManager;
import com.excrele.commands.AdminCommandHandler;
import com.excrele.commands.AuthCommandHandler;
import com.excrele.commands.PlayerAccountCommandHandler;
import com.excrele.config.ConfigManager;
import com.excrele.database.DatabaseManager;
import com.excrele.listeners.PlayerEventListener;
import com.excrele.security.IPFilterManager;

public class SecureAuth extends JavaPlugin {
    private ConfigManager configManager;
    private PasswordManager passwordManager;
    private DatabaseManager databaseManager;
    private SessionManager sessionManager;
    private RateLimitManager rateLimitManager;
    private IPFilterManager ipFilterManager;
    private TwoFactorAuthManager twoFactorAuthManager;
    private PasswordRecoveryManager passwordRecoveryManager;
    private StatisticsManager statisticsManager;
    private CacheManager cacheManager;
    private AuthManager authManager;
    private AuthCommandHandler commandHandler;
    private AdminCommandHandler adminCommandHandler;
    private PlayerAccountCommandHandler playerAccountCommandHandler;
    private PlayerEventListener eventListener;

    @Override
    public void onEnable() {
        // Load configuration
        configManager = new ConfigManager(this);
        configManager.loadConfig();
        
        getLogger().info("Configuration loaded successfully");

        // Initialize password manager
        passwordManager = new PasswordManager(configManager, this);
        getLogger().info("Password manager initialized (algorithm: " + configManager.getHashAlgorithm() + ")");

        // Initialize database
        databaseManager = new DatabaseManager(configManager, this);
        getLogger().info("Database manager initialized");

        // Initialize session manager
        sessionManager = new SessionManager(configManager, this);
        sessionManager.start();
        getLogger().info("Session manager started");

        // Initialize rate limit manager
        rateLimitManager = new RateLimitManager(configManager, this);
        rateLimitManager.start();
        getLogger().info("Rate limit manager started");

        // Initialize IP filter manager
        ipFilterManager = new IPFilterManager(this);
        getLogger().info("IP filter manager initialized");

        // Initialize 2FA manager
        twoFactorAuthManager = new TwoFactorAuthManager(configManager, databaseManager, this);
        if (configManager.is2FAEnabled()) {
            getLogger().info("Two-Factor Authentication enabled");
        }

        // Initialize password recovery manager
        passwordRecoveryManager = new PasswordRecoveryManager(configManager, databaseManager, this);
        getLogger().info("Password recovery manager initialized");

        // Initialize statistics manager
        statisticsManager = new StatisticsManager(configManager, this);
        getLogger().info("Statistics manager initialized");

        // Initialize cache manager
        cacheManager = new CacheManager(this);
        getLogger().info("Cache manager initialized");

        // Initialize auth manager
        authManager = new AuthManager(configManager, passwordManager, databaseManager,
                                     sessionManager, rateLimitManager, ipFilterManager,
                                     twoFactorAuthManager, passwordRecoveryManager,
                                     statisticsManager, cacheManager, this);
        getLogger().info("Auth manager initialized");

        // Register PlaceholderAPI expansion if available
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new SecureAuthPlaceholders(authManager, sessionManager, twoFactorAuthManager, statisticsManager).register();
            getLogger().info("PlaceholderAPI expansion registered!");
        }

        // Register event listener
        eventListener = new PlayerEventListener(sessionManager, authManager, configManager, this);
        getServer().getPluginManager().registerEvents(eventListener, this);
        getLogger().info("Event listeners registered");

        // Register commands
        commandHandler = new AuthCommandHandler(authManager, passwordManager, configManager, this);
        this.getCommand("register").setExecutor(commandHandler);
        this.getCommand("login").setExecutor(commandHandler);
        this.getCommand("changepass").setExecutor(commandHandler);
        this.getCommand("setpass").setExecutor(commandHandler);
        
        // Initialize migration tool
        com.excrele.database.MigrationTool migrationTool = new com.excrele.database.MigrationTool(configManager, this, databaseManager);
        
        // Register admin commands
        adminCommandHandler = new AdminCommandHandler(authManager, configManager, databaseManager,
                                                      ipFilterManager, twoFactorAuthManager,
                                                      passwordRecoveryManager, statisticsManager,
                                                      migrationTool, this);
        if (this.getCommand("auth") != null) {
            this.getCommand("auth").setExecutor(adminCommandHandler);
            this.getCommand("auth").setTabCompleter(adminCommandHandler);
        }
        
        // Register player account commands
        playerAccountCommandHandler = new PlayerAccountCommandHandler(authManager, sessionManager,
                                                                     twoFactorAuthManager, configManager, this);
        if (this.getCommand("authinfo") != null) {
            this.getCommand("authinfo").setExecutor(playerAccountCommandHandler);
        }
        if (this.getCommand("logout") != null) {
            this.getCommand("logout").setExecutor(playerAccountCommandHandler);
        }
        if (this.getCommand("logoutall") != null) {
            this.getCommand("logoutall").setExecutor(playerAccountCommandHandler);
        }
        if (this.getCommand("2fa") != null) {
            this.getCommand("2fa").setExecutor(playerAccountCommandHandler);
        }
        if (this.getCommand("2faverify") != null) {
            this.getCommand("2faverify").setExecutor(playerAccountCommandHandler);
        }
        
        getLogger().info("Commands registered");

        getLogger().info("========================================");
        getLogger().info("SecureAuth v" + getDescription().getVersion() + " by " + getDescription().getAuthors());
        getLogger().info("Successfully enabled!");
        getLogger().info("Database: " + configManager.getDatabaseType());
        getLogger().info("Hash Algorithm: " + configManager.getHashAlgorithm());
        getLogger().info("Session Timeout: " + configManager.getSessionTimeoutMinutes() + " minutes");
        getLogger().info("Max Login Attempts: " + configManager.getMaxAttempts());
        getLogger().info("========================================");
    }

    @Override
    public void onDisable() {
        getLogger().info("Shutting down SecureAuth...");

        if (sessionManager != null) {
            sessionManager.stop();
        }

        if (rateLimitManager != null) {
            rateLimitManager.stop();
        }

        if (databaseManager != null) {
            databaseManager.close();
        }

        getLogger().info("SecureAuth disabled successfully!");
    }

    public void reload() {
        getLogger().info("Reloading SecureAuth configuration...");
        configManager.reloadConfig();
        getLogger().info("Configuration reloaded!");
    }

    // Getters for API access (if other plugins want to integrate)
    public ConfigManager getConfigManager() {
        return configManager;
    }

    public AuthManager getAuthManager() {
        return authManager;
    }

    public SessionManager getSessionManager() {
        return sessionManager;
    }
}
