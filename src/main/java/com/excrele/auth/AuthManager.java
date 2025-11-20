package com.excrele.auth;

import com.excrele.cache.CacheManager;
import com.excrele.config.ConfigManager;
import com.excrele.database.DatabaseManager;
import com.excrele.security.IPFilterManager;
import com.excrele.statistics.StatisticsManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class AuthManager {
    private final ConfigManager config;
    private final PasswordManager passwordManager;
    private final DatabaseManager databaseManager;
    private final SessionManager sessionManager;
    private final RateLimitManager rateLimitManager;
    private final IPFilterManager ipFilterManager;
    private final TwoFactorAuthManager twoFactorAuthManager;
    private final PasswordRecoveryManager passwordRecoveryManager;
    private final StatisticsManager statisticsManager;
    private final CacheManager cacheManager;
    private final JavaPlugin plugin;
    private File passwordFile; // For file-based storage (legacy)

    public AuthManager(ConfigManager config, PasswordManager passwordManager, 
                      DatabaseManager databaseManager, SessionManager sessionManager,
                      RateLimitManager rateLimitManager, IPFilterManager ipFilterManager,
                      TwoFactorAuthManager twoFactorAuthManager, PasswordRecoveryManager passwordRecoveryManager,
                      StatisticsManager statisticsManager, CacheManager cacheManager, JavaPlugin plugin) {
        this.config = config;
        this.passwordManager = passwordManager;
        this.databaseManager = databaseManager;
        this.sessionManager = sessionManager;
        this.rateLimitManager = rateLimitManager;
        this.ipFilterManager = ipFilterManager;
        this.twoFactorAuthManager = twoFactorAuthManager;
        this.passwordRecoveryManager = passwordRecoveryManager;
        this.statisticsManager = statisticsManager;
        this.cacheManager = cacheManager;
        this.plugin = plugin;
        
        if (databaseManager.isFileBased()) {
            setupFileStorage();
        }
    }

    private void setupFileStorage() {
        passwordFile = new File(plugin.getDataFolder(), "passwords.txt");
        if (!passwordFile.exists()) {
            try {
                plugin.getDataFolder().mkdirs();
                passwordFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to create passwords.txt", e);
            }
        }
    }

    public boolean hasPassword(UUID playerId) {
        if (databaseManager.isFileBased()) {
            return hasRegisteredPasswordFile(playerId);
        }
        return databaseManager.hasPassword(playerId);
    }

    private boolean hasRegisteredPasswordFile(UUID playerId) {
        try {
            String content = new String(Files.readAllBytes(passwordFile.toPath()));
            return content.contains(playerId.toString() + ":");
        } catch (IOException e) {
            return false;
        }
    }

    public String getPasswordHash(UUID playerId) {
        if (databaseManager.isFileBased()) {
            return getSavedHashFile(playerId);
        }
        return databaseManager.getPasswordHash(playerId);
    }

    private String getSavedHashFile(UUID playerId) {
        try {
            String content = new String(Files.readAllBytes(passwordFile.toPath()));
            String[] lines = content.split("\n");
            for (String line : lines) {
                if (line.startsWith(playerId.toString() + ":")) {
                    return line.split(":", 2)[1].trim();
                }
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Couldn't read password file for " + playerId);
        }
        return null;
    }

    public void setPassword(UUID playerId, String password) {
        String hashed = passwordManager.hashPassword(password);
        
        if (databaseManager.isFileBased()) {
            updatePasswordFile(playerId, hashed);
        } else {
            databaseManager.setPassword(playerId, hashed);
        }
    }

    private void updatePasswordFile(UUID playerId, String newHashedPass) {
        try {
            String content = new String(Files.readAllBytes(passwordFile.toPath()));
            String[] lines = content.split("\n");
            String playerLine = playerId.toString() + ":" + newHashedPass;
            boolean found = false;

            for (int i = 0; i < lines.length; i++) {
                if (lines[i].startsWith(playerId.toString() + ":")) {
                    lines[i] = playerLine;
                    found = true;
                    break;
                }
            }

            if (!found) {
                String[] newLines = new String[lines.length + 1];
                System.arraycopy(lines, 0, newLines, 0, lines.length);
                newLines[lines.length] = playerLine;
                lines = newLines;
            }

            String newContent = String.join("\n", lines) + "\n";
            Files.write(Paths.get(passwordFile.getPath()), newContent.getBytes(), 
                       StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to update password for " + playerId, e);
        }
    }

    public void appendPasswordFile(UUID playerId, String hashedPass) throws IOException {
        String line = playerId.toString() + ":" + hashedPass + "\n";
        Files.write(Paths.get(passwordFile.getPath()), line.getBytes(), 
                   StandardOpenOption.APPEND, StandardOpenOption.CREATE);
    }

    public boolean register(Player player, String password, String repeatPassword) {
        UUID playerId = player.getUniqueId();
        
        // Validation
        if (!password.equals(repeatPassword)) {
            player.sendMessage(config.getMessage("register-password-mismatch",
                "&cPasswords don't match!"));
            return false;
        }
        
        if (!passwordManager.isPasswordValid(password)) {
            int minLength = config.getMinPasswordLength();
            player.sendMessage(config.getMessage("register-password-too-short",
                "&cPassword must be at least {min} characters!").replace("{min}", String.valueOf(minLength)));
            
            String feedback = passwordManager.getPasswordStrengthFeedback(password);
            if (feedback != null && config.isComplexityRequired()) {
                player.sendMessage("§c" + feedback);
            }
            return false;
        }
        
        if (hasPassword(playerId)) {
            player.sendMessage(config.getMessage("register-already-registered",
                "&cYou're already registered! Use /login instead."));
            return false;
        }
        
        // Register
        setPassword(playerId, password);
        sessionManager.setLoggedIn(playerId, true, getPlayerIp(player));
        rateLimitManager.clearAttempts(playerId, getPlayerIp(player));
        
        // Record statistics
        statisticsManager.recordRegistration();
        
        if (config.shouldLogLogins()) {
            plugin.getLogger().info("Player " + player.getName() + " registered successfully");
        }
        
        player.sendMessage(config.getMessage("register-success",
            "&aRegistered successfully! You're now logged in."));
        return true;
    }

    public boolean login(Player player, String password) {
        UUID playerId = player.getUniqueId();
        String ip = getPlayerIp(player);
        
        // Check IP blacklist
        if (ipFilterManager.isBlacklisted(ip)) {
            player.sendMessage("§cYour IP address has been blacklisted. Contact an administrator.");
            if (config.shouldLogFailedAttempts()) {
                plugin.getLogger().warning("Blacklisted IP " + ip + " attempted login from " + player.getName());
            }
            return false;
        }
        
        // Check if registered
        if (!hasPassword(playerId)) {
            player.sendMessage(config.getMessage("login-not-registered",
                "&cYou're not registered! Use /register first."));
            return false;
        }
        
        // Check IP lockout (skip if whitelisted)
        if (!ipFilterManager.isWhitelisted(ip)) {
            if (rateLimitManager.isIpLockedOut(ip)) {
                long remaining = rateLimitManager.getRemainingIpLockoutTime(ip);
                player.sendMessage(config.getMessage("login-ip-locked",
                    "&cYour IP is locked out! Wait {minutes} more minutes.")
                    .replace("{minutes}", String.valueOf(remaining)));
                return false;
            }
        }
        
        // Check player lockout
        if (rateLimitManager.isLockedOut(playerId)) {
            long remaining = rateLimitManager.getRemainingLockoutTime(playerId);
            player.sendMessage(config.getMessage("login-locked-out",
                "&cYou're locked out! Wait {minutes} more minutes.")
                .replace("{minutes}", String.valueOf(remaining)));
            return false;
        }
        
        // Verify password
        String savedHash = getPasswordHash(playerId);
        if (savedHash != null && passwordManager.verifyPassword(password, savedHash)) {
            // Check 2FA if enabled
            if (twoFactorAuthManager.isEnabled() && twoFactorAuthManager.has2FAEnabled(playerId)) {
                // 2FA verification will be handled separately via command
                // For now, we'll require 2FA code in a separate step
                player.sendMessage("§e[SecureAuth] Please verify your 2FA code with /2faverify <code>");
                // Don't log in yet, wait for 2FA
                return false;
            }
            
            sessionManager.setLoggedIn(playerId, true);
            // Only clear attempts if not whitelisted (whitelisted IPs bypass rate limiting)
            if (!ipFilterManager.isWhitelisted(ip)) {
                rateLimitManager.clearAttempts(playerId, ip);
            }
            
            // Record statistics
            statisticsManager.recordLogin(playerId);
            
            // Invalidate password cache on successful login
            cacheManager.invalidatePasswordHash(playerId);
            
            if (config.shouldLogLogins()) {
                plugin.getLogger().info("Player " + player.getName() + " logged in successfully");
            }
            
            player.sendMessage(config.getMessage("login-success",
                "&aLogin successful! Welcome back."));
            return true;
        } else {
            // Failed attempt (skip rate limiting for whitelisted IPs)
            if (!ipFilterManager.isWhitelisted(ip)) {
                rateLimitManager.recordFailedAttempt(playerId, ip);
            }
            
            // Record statistics
            statisticsManager.recordFailedAttempt();
            int attempts = rateLimitManager.getAttemptCount(playerId);
            int maxAttempts = config.getMaxAttempts();
            
            if (config.shouldLogFailedAttempts()) {
                plugin.getLogger().warning("Failed login attempt for " + player.getName() + 
                    " (IP: " + ip + ") - Attempts: " + attempts + "/" + maxAttempts);
            }
            
            if (rateLimitManager.isLockedOut(playerId)) {
                player.sendMessage(config.getMessage("login-locked-out",
                    "&cToo many wrong tries! Locked for " + config.getLockoutDurationMinutes() + " minutes."));
            } else {
                player.sendMessage(config.getMessage("login-wrong-password",
                    "&cWrong password! Attempts: {attempts}/{max}")
                    .replace("{attempts}", String.valueOf(attempts))
                    .replace("{max}", String.valueOf(maxAttempts)));
            }
            
            if (config.isIpLimitsEnabled() && rateLimitManager.isIpLockedOut(ip)) {
                player.sendMessage("§cToo many tries from your IP! Locked for " + 
                    config.getLockoutDurationMinutes() + " minutes.");
            }
            
            return false;
        }
    }

    public boolean changePassword(Player player, String oldPassword, String newPassword, String repeatNewPassword) {
        UUID playerId = player.getUniqueId();
        
        if (!sessionManager.isLoggedIn(playerId)) {
            player.sendMessage(config.getMessage("changepass-not-logged-in",
                "&cYou must be logged in to change your password!"));
            return false;
        }
        
        if (!hasPassword(playerId)) {
            player.sendMessage(config.getMessage("changepass-not-registered",
                "&cYou're not registered!"));
            return false;
        }
        
        // Verify old password
        String savedHash = getPasswordHash(playerId);
        if (savedHash == null || !passwordManager.verifyPassword(oldPassword, savedHash)) {
            player.sendMessage(config.getMessage("changepass-wrong-old",
                "&cWrong old password!"));
            return false;
        }
        
        // Validate new password
        if (!newPassword.equals(repeatNewPassword)) {
            player.sendMessage(config.getMessage("register-password-mismatch",
                "&cNew passwords don't match!"));
            return false;
        }
        
        if (!passwordManager.isPasswordValid(newPassword)) {
            int minLength = config.getMinPasswordLength();
            player.sendMessage(config.getMessage("register-password-too-short",
                "&cNew password must be at least {min} characters!")
                .replace("{min}", String.valueOf(minLength)));
            return false;
        }
        
        // Update password
        setPassword(playerId, newPassword);
        sessionManager.updateActivity(playerId);
        
        // Invalidate cache
        cacheManager.invalidatePasswordHash(playerId);
        
        // Record statistics
        statisticsManager.recordPasswordChange();
        
        if (config.shouldLogPasswordChanges()) {
            plugin.getLogger().info("Player " + player.getName() + " changed their password");
        }
        
        player.sendMessage(config.getMessage("changepass-success",
            "&aPassword changed successfully!"));
        return true;
    }

    public void setPasswordAdmin(Player targetPlayer, String newPassword) {
        UUID targetId = targetPlayer.getUniqueId();
        String ip = getPlayerIp(targetPlayer);
        
        if (!passwordManager.isPasswordValid(newPassword)) {
            return; // Validation should be done before calling this
        }
        
        setPassword(targetId, newPassword);
        sessionManager.setLoggedIn(targetId, false);
        rateLimitManager.clearAttempts(targetId, ip);
        
        if (config.shouldLogAdminActions()) {
            plugin.getLogger().info("Admin reset password for " + targetPlayer.getName());
        }
        
        targetPlayer.sendMessage(config.getMessage("setpass-success",
            "&eYour password was reset by an admin! Log in with the new one to play.")
            .replace("{player}", targetPlayer.getName()));
    }

    public CompletableFuture<Boolean> checkPremiumAndHandle(Player player) {
        String username = player.getName().toLowerCase();
        
        return CompletableFuture.supplyAsync(() -> isPremiumUser(username))
            .thenApply(isPremium -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    UUID playerId = player.getUniqueId();
                    String ip = getPlayerIp(player);
                    
                    if (isPremium && config.isPremiumAutoLogin()) {
                        sessionManager.setLoggedIn(playerId, true, ip);
                        rateLimitManager.clearAttempts(playerId, ip);
                        player.sendMessage(config.getMessage("premium-auto-login",
                            "&aWelcome, premium player! Auto-logged in."));
                    } else {
                        if (hasPassword(playerId)) {
                            player.sendMessage(config.getMessage("welcome-registered",
                                "&ePlease login with /login <password>"));
                        } else {
                            player.sendMessage(config.getMessage("welcome-cracked",
                                "&eWelcome! Please register with /register <password> <repeat>"));
                        }
                        sessionManager.setLoggedIn(playerId, false);
                    }
                });
                return isPremium;
            });
    }

    private boolean isPremiumUser(String username) {
        // Check cache first
        Boolean cached = cacheManager.getPremiumStatus(username);
        if (cached != null) {
            return cached;
        }
        
        try {
            java.net.URI uri = new java.net.URI("https://api.mojang.com/users/profiles/minecraft/" + username);
            java.net.URL url = uri.toURL();
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(config.getApiTimeoutMs());
            conn.setReadTimeout(config.getApiTimeoutMs());
            int responseCode = conn.getResponseCode();
            boolean isPremium = responseCode == 200;
            
            // Cache the result
            cacheManager.setPremiumStatus(username, isPremium);
            
            return isPremium;
        } catch (Exception e) {
            plugin.getLogger().warning("Premium check failed for " + username + ": " + e.getMessage());
            boolean isPremium = !config.isFailSafeCracked(); // Return false if fail-safe is cracked
            cacheManager.setPremiumStatus(username, isPremium);
            return isPremium;
        }
    }

    public void deleteAccount(UUID playerId) {
        if (databaseManager.isFileBased()) {
            // Delete from file
            try {
                String content = new String(Files.readAllBytes(passwordFile.toPath()));
                String[] lines = content.split("\n");
                StringBuilder newContent = new StringBuilder();
                for (String line : lines) {
                    if (!line.startsWith(playerId.toString() + ":")) {
                        newContent.append(line).append("\n");
                    }
                }
                Files.write(Paths.get(passwordFile.getPath()), newContent.toString().getBytes(),
                           StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to delete account from file: " + e.getMessage());
            }
        } else {
            databaseManager.deletePassword(playerId);
        }
        
        // Clear 2FA if exists
        if (twoFactorAuthManager.isEnabled() && twoFactorAuthManager.has2FAEnabled(playerId)) {
            twoFactorAuthManager.disable2FA(playerId);
        }
        
        // Clear session
        sessionManager.removePlayer(playerId);
    }

    private String getPlayerIp(Player player) {
        return ((java.net.InetSocketAddress) player.getAddress()).getAddress().getHostAddress();
    }
}

