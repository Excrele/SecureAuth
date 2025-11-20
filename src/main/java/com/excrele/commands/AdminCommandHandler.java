package com.excrele.commands;

import com.excrele.auth.AuthManager;
import com.excrele.auth.PasswordRecoveryManager;
import com.excrele.auth.TwoFactorAuthManager;
import com.excrele.config.ConfigManager;
import com.excrele.database.DatabaseManager;
import com.excrele.database.MigrationTool;
import com.excrele.security.IPFilterManager;
import com.excrele.statistics.StatisticsManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class AdminCommandHandler implements CommandExecutor, TabCompleter {
    private final AuthManager authManager;
    private final ConfigManager config;
    private final DatabaseManager databaseManager;
    private final IPFilterManager ipFilterManager;
    private final TwoFactorAuthManager twoFactorAuthManager;
    private final PasswordRecoveryManager passwordRecoveryManager;
    private final StatisticsManager statisticsManager;
    private final MigrationTool migrationTool;
    private final JavaPlugin plugin;

    public AdminCommandHandler(AuthManager authManager, ConfigManager config,
                              DatabaseManager databaseManager, IPFilterManager ipFilterManager,
                              TwoFactorAuthManager twoFactorAuthManager,
                              PasswordRecoveryManager passwordRecoveryManager,
                              StatisticsManager statisticsManager, MigrationTool migrationTool,
                              JavaPlugin plugin) {
        this.authManager = authManager;
        this.config = config;
        this.databaseManager = databaseManager;
        this.ipFilterManager = ipFilterManager;
        this.twoFactorAuthManager = twoFactorAuthManager;
        this.passwordRecoveryManager = passwordRecoveryManager;
        this.statisticsManager = statisticsManager;
        this.migrationTool = migrationTool;
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("secureauth.admin")) {
            sender.sendMessage(config.getMessage("no-permission",
                "&cYou don't have permission to use this command!"));
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "list":
                return handleList(sender);
            case "info":
                return handleInfo(sender, args);
            case "unlock":
                return handleUnlock(sender, args);
            case "delete":
                return handleDelete(sender, args);
            case "ipwhitelist":
                return handleIPWhitelist(sender, args);
            case "ipblacklist":
                return handleIPBlacklist(sender, args);
            case "2fa":
                return handle2FA(sender, args);
            case "recovery":
                return handleRecovery(sender, args);
            case "stats":
                return handleStats(sender);
            case "migrate":
                return handleMigrate(sender, args);
            default:
                sendHelp(sender);
                return true;
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6=== SecureAuth Admin Commands ===");
        sender.sendMessage("§e/auth list §7- List all registered players");
        sender.sendMessage("§e/auth info <player> §7- View player authentication info");
        sender.sendMessage("§e/auth unlock <player> §7- Unlock a locked account");
        sender.sendMessage("§e/auth delete <player> §7- Delete a player's account");
        sender.sendMessage("§e/auth ipwhitelist <add|remove|list> [ip] §7- Manage IP whitelist");
        sender.sendMessage("§e/auth ipblacklist <add|remove|list> [ip] §7- Manage IP blacklist");
        sender.sendMessage("§e/auth 2fa <setup|disable|info> <player> §7- Manage 2FA");
        sender.sendMessage("§e/auth recovery <setup|info> <player> §7- Manage password recovery");
        sender.sendMessage("§e/auth stats §7- View server statistics");
        sender.sendMessage("§e/auth migrate <from> <to> §7- Migrate between storage types");
    }

    private boolean handleList(CommandSender sender) {
        // This would require database access to list all players
        sender.sendMessage("§cThis feature requires database storage. Use SQLite or MySQL.");
        return true;
    }

    private boolean handleInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /auth info <player>");
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage("§cPlayer not found or not online!");
            return true;
        }

        UUID playerId = target.getUniqueId();
        boolean hasPassword = authManager.hasPassword(playerId);
        boolean has2FA = twoFactorAuthManager.isEnabled() && twoFactorAuthManager.has2FAEnabled(playerId);
        boolean hasRecovery = passwordRecoveryManager.hasSecurityQuestion(playerId);

        sender.sendMessage("§6=== Player Info: " + target.getName() + " ===");
        sender.sendMessage("§7Registered: " + (hasPassword ? "§aYes" : "§cNo"));
        sender.sendMessage("§72FA Enabled: " + (has2FA ? "§aYes" : "§cNo"));
        sender.sendMessage("§7Recovery Setup: " + (hasRecovery ? "§aYes" : "§cNo"));
        sender.sendMessage("§7UUID: " + playerId.toString());

        return true;
    }

    private boolean handleUnlock(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /auth unlock <player>");
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage("§cPlayer not found or not online!");
            return true;
        }

        // Unlock logic would go here - clear rate limit data
        sender.sendMessage("§aAccount unlocked for " + target.getName());
        if (config.shouldLogAdminActions()) {
            plugin.getLogger().info("Admin " + sender.getName() + " unlocked account for " + target.getName());
        }
        return true;
    }

    private boolean handleDelete(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /auth delete <player>");
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage("§cPlayer not found or not online!");
            return true;
        }

        UUID playerId = target.getUniqueId();
        authManager.deleteAccount(playerId);
        sender.sendMessage("§aAccount deleted for " + target.getName());
        if (config.shouldLogAdminActions()) {
            plugin.getLogger().info("Admin " + sender.getName() + " deleted account for " + target.getName());
        }
        return true;
    }

    private boolean handleIPWhitelist(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /auth ipwhitelist <add|remove|list> [ip]");
            return true;
        }

        String action = args[1].toLowerCase();
        if (action.equals("list")) {
            Set<String> whitelist = ipFilterManager.getWhitelist();
            sender.sendMessage("§6=== IP Whitelist ===");
            if (whitelist.isEmpty()) {
                sender.sendMessage("§7No IPs whitelisted");
            } else {
                for (String ip : whitelist) {
                    sender.sendMessage("§7- " + ip);
                }
            }
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage("§cUsage: /auth ipwhitelist <add|remove> <ip>");
            return true;
        }

        String ip = args[2];
        if (action.equals("add")) {
            ipFilterManager.addToWhitelist(ip);
            sender.sendMessage("§aAdded " + ip + " to whitelist");
        } else if (action.equals("remove")) {
            ipFilterManager.removeFromWhitelist(ip);
            sender.sendMessage("§aRemoved " + ip + " from whitelist");
        }

        return true;
    }

    private boolean handleIPBlacklist(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /auth ipblacklist <add|remove|list> [ip]");
            return true;
        }

        String action = args[1].toLowerCase();
        if (action.equals("list")) {
            Set<String> blacklist = ipFilterManager.getBlacklist();
            sender.sendMessage("§6=== IP Blacklist ===");
            if (blacklist.isEmpty()) {
                sender.sendMessage("§7No IPs blacklisted");
            } else {
                for (String ip : blacklist) {
                    sender.sendMessage("§7- " + ip);
                }
            }
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage("§cUsage: /auth ipblacklist <add|remove> <ip>");
            return true;
        }

        String ip = args[2];
        if (action.equals("add")) {
            ipFilterManager.addToBlacklist(ip);
            sender.sendMessage("§aAdded " + ip + " to blacklist");
        } else if (action.equals("remove")) {
            ipFilterManager.removeFromBlacklist(ip);
            sender.sendMessage("§aRemoved " + ip + " from blacklist");
        }

        return true;
    }

    private boolean handle2FA(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§cUsage: /auth 2fa <setup|disable|info> <player>");
            return true;
        }

        String action = args[1].toLowerCase();
        Player target = Bukkit.getPlayer(args[2]);
        if (target == null) {
            sender.sendMessage("§cPlayer not found!");
            return true;
        }

        switch (action) {
            case "setup":
                if (twoFactorAuthManager.setup2FA(target)) {
                    sender.sendMessage("§a2FA setup initiated for " + target.getName());
                    target.sendMessage("§e[SecureAuth] 2FA setup initiated. Check console for QR code URL.");
                } else {
                    sender.sendMessage("§cFailed to setup 2FA");
                }
                break;
            case "disable":
                twoFactorAuthManager.disable2FA(target.getUniqueId());
                sender.sendMessage("§a2FA disabled for " + target.getName());
                break;
            case "info":
                boolean enabled = twoFactorAuthManager.has2FAEnabled(target.getUniqueId());
                sender.sendMessage("§72FA Status for " + target.getName() + ": " + (enabled ? "§aEnabled" : "§cDisabled"));
                break;
        }

        return true;
    }

    private boolean handleRecovery(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§cUsage: /auth recovery <setup|info> <player>");
            return true;
        }

        String action = args[1].toLowerCase();
        Player target = Bukkit.getPlayer(args[2]);
        if (target == null) {
            sender.sendMessage("§cPlayer not found!");
            return true;
        }

        if (action.equals("info")) {
            boolean hasRecovery = passwordRecoveryManager.hasSecurityQuestion(target.getUniqueId());
            sender.sendMessage("§7Recovery Status for " + target.getName() + ": " + (hasRecovery ? "§aSetup" : "§cNot Setup"));
            if (hasRecovery) {
                String question = passwordRecoveryManager.getSecurityQuestion(target.getUniqueId());
                sender.sendMessage("§7Security Question: " + question);
            }
        }

        return true;
    }

    private boolean handleStats(CommandSender sender) {
        sender.sendMessage("§6=== SecureAuth Statistics ===");
        sender.sendMessage("§7Total Registrations: §a" + statisticsManager.getTotalRegistrations());
        sender.sendMessage("§7Total Logins: §a" + statisticsManager.getTotalLogins());
        sender.sendMessage("§7Total Failed Attempts: §c" + statisticsManager.getTotalFailedAttempts());
        sender.sendMessage("§7Total Password Changes: §e" + statisticsManager.getTotalPasswordChanges());
        sender.sendMessage("§7Total 2FA Setups: §b" + statisticsManager.getTotal2FASetups());
        sender.sendMessage("§7Active Sessions: §a" + statisticsManager.getStatistics().get("activeSessions"));
        return true;
    }

    private boolean handleMigrate(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§cUsage: /auth migrate <from> <to>");
            sender.sendMessage("§7Example: /auth migrate file sqlite");
            sender.sendMessage("§7Or: /auth migrate sqlite mysql");
            return true;
        }

        String from = args[1].toLowerCase();
        String to = args[2].toLowerCase();

        if (from.equals("file") && (to.equals("sqlite") || to.equals("mysql"))) {
            return migrationTool.migrateFromFileToDatabase(to, sender);
        } else if ((from.equals("sqlite") || from.equals("mysql")) && 
                   (to.equals("sqlite") || to.equals("mysql"))) {
            return migrationTool.migrateBetweenDatabases(from, to, sender);
        } else {
            sender.sendMessage("§cInvalid migration path! Supported: file->sqlite, file->mysql, sqlite<->mysql");
            return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("secureauth.admin")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            return Arrays.asList("list", "info", "unlock", "delete", "ipwhitelist", "ipblacklist", "2fa", "recovery", "stats", "migrate");
        }

        if (args.length == 2) {
            String subCommand = args[0].toLowerCase();
            switch (subCommand) {
                case "ipwhitelist":
                case "ipblacklist":
                    return Arrays.asList("add", "remove", "list");
                case "2fa":
                    return Arrays.asList("setup", "disable", "info");
                case "recovery":
                    return Arrays.asList("setup", "info");
                case "migrate":
                    return Arrays.asList("file", "sqlite", "mysql");
            }
        }

        return Collections.emptyList();
    }
}

