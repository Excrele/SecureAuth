package com.excrele.commands;

import com.excrele.auth.AuthManager;
import com.excrele.auth.SessionManager;
import com.excrele.auth.TwoFactorAuthManager;
import com.excrele.config.ConfigManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

public class PlayerAccountCommandHandler implements CommandExecutor {
    private final AuthManager authManager;
    private final SessionManager sessionManager;
    private final TwoFactorAuthManager twoFactorAuthManager;
    private final ConfigManager config;
    private final JavaPlugin plugin;

    public PlayerAccountCommandHandler(AuthManager authManager, SessionManager sessionManager,
                                      TwoFactorAuthManager twoFactorAuthManager,
                                      ConfigManager config, JavaPlugin plugin) {
        this.authManager = authManager;
        this.sessionManager = sessionManager;
        this.twoFactorAuthManager = twoFactorAuthManager;
        this.config = config;
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(org.bukkit.command.CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cOnly players can use this command!");
            return true;
        }

        Player player = (Player) sender;
        UUID playerId = player.getUniqueId();
        String cmdName = command.getName().toLowerCase();

        switch (cmdName) {
            case "authinfo":
                return handleAuthInfo(player);
            case "logout":
                return handleLogout(player);
            case "logoutall":
                return handleLogoutAll(player);
            case "2fa":
                return handle2FA(player, args);
            case "2faverify":
                return handle2FAVerify(player, args);
            default:
                return false;
        }
    }

    private boolean handleAuthInfo(Player player) {
        UUID playerId = player.getUniqueId();
        boolean hasPassword = authManager.hasPassword(playerId);
        boolean isLoggedIn = sessionManager.isLoggedIn(playerId);
        boolean has2FA = twoFactorAuthManager.isEnabled() && twoFactorAuthManager.has2FAEnabled(playerId);

        player.sendMessage("§6=== Your Account Information ===");
        player.sendMessage("§7Registered: " + (hasPassword ? "§aYes" : "§cNo"));
        player.sendMessage("§7Logged In: " + (isLoggedIn ? "§aYes" : "§cNo"));
        player.sendMessage("§72FA Enabled: " + (has2FA ? "§aYes" : "§cNo"));
        player.sendMessage("§7UUID: " + playerId.toString());

        return true;
    }

    private boolean handleLogout(Player player) {
        UUID playerId = player.getUniqueId();
        if (!sessionManager.isLoggedIn(playerId)) {
            player.sendMessage("§cYou're not logged in!");
            return true;
        }

        sessionManager.setLoggedIn(playerId, false);
        player.sendMessage("§aYou have been logged out!");
        return true;
    }

    private boolean handleLogoutAll(Player player) {
        UUID playerId = player.getUniqueId();
        if (!sessionManager.isLoggedIn(playerId)) {
            player.sendMessage("§cYou're not logged in!");
            return true;
        }

        // For now, just logout current session
        // Future: Implement multi-session support
        sessionManager.setLoggedIn(playerId, false);
        player.sendMessage("§aYou have been logged out from all sessions!");
        return true;
    }

    private boolean handle2FA(Player player, String[] args) {
        if (!twoFactorAuthManager.isEnabled()) {
            player.sendMessage("§c2FA is not enabled on this server!");
            return true;
        }

        if (args.length == 0) {
            player.sendMessage("§cUsage: /2fa <setup|disable>");
            return true;
        }

        String action = args[0].toLowerCase();
        UUID playerId = player.getUniqueId();

        switch (action) {
            case "setup":
                if (twoFactorAuthManager.has2FAEnabled(playerId)) {
                    player.sendMessage("§c2FA is already enabled for your account!");
                    return true;
                }

                if (twoFactorAuthManager.setup2FA(player)) {
                    String qrUrl = twoFactorAuthManager.generateQRCodeURL(playerId, player.getName(),
                        twoFactorAuthManager.getPendingSecret(playerId));
                    player.sendMessage("§a2FA setup initiated!");
                    player.sendMessage("§7Scan this QR code with your authenticator app:");
                    player.sendMessage("§e" + qrUrl);
                    player.sendMessage("§7Or enter this secret manually: §e" + twoFactorAuthManager.getPendingSecret(playerId));
                    
                    // Show backup codes
                    java.util.List<String> backupCodes = twoFactorAuthManager.getBackupCodesForPlayer(playerId);
                    if (backupCodes != null && !backupCodes.isEmpty()) {
                        player.sendMessage("§6Backup Codes (save these!):");
                        for (String code : backupCodes) {
                            player.sendMessage("§7- " + code);
                        }
                    }
                } else {
                    player.sendMessage("§cFailed to setup 2FA!");
                }
                break;

            case "disable":
                if (!twoFactorAuthManager.has2FAEnabled(playerId)) {
                    player.sendMessage("§c2FA is not enabled for your account!");
                    return true;
                }

                if (!sessionManager.isLoggedIn(playerId)) {
                    player.sendMessage("§cYou must be logged in to disable 2FA!");
                    return true;
                }

                twoFactorAuthManager.disable2FA(playerId);
                player.sendMessage("§a2FA has been disabled for your account!");
                break;

            default:
                player.sendMessage("§cUsage: /2fa <setup|disable>");
                return true;
        }

        return true;
    }

    private boolean handle2FAVerify(Player player, String[] args) {
        if (!twoFactorAuthManager.isEnabled()) {
            player.sendMessage("§c2FA is not enabled on this server!");
            return true;
        }

        if (args.length == 0) {
            player.sendMessage("§cUsage: /2faverify <code>");
            return true;
        }

        String code = args[0];
        UUID playerId = player.getUniqueId();

        if (!twoFactorAuthManager.has2FAEnabled(playerId)) {
            player.sendMessage("§c2FA is not enabled for your account!");
            return true;
        }

        if (twoFactorAuthManager.verifyTOTP(playerId, code)) {
            // Complete login
            sessionManager.setLoggedIn(playerId, true);
            player.sendMessage("§a2FA verified! You are now logged in.");
            return true;
        } else {
            player.sendMessage("§cInvalid 2FA code! Please try again.");
            return true;
        }
    }
}

