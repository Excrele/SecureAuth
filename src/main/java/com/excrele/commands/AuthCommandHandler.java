package com.excrele.commands;

import com.excrele.auth.AuthManager;
import com.excrele.auth.PasswordManager;
import com.excrele.config.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class AuthCommandHandler implements CommandExecutor {
    private final AuthManager authManager;
    private final PasswordManager passwordManager;
    private final ConfigManager config;

    public AuthCommandHandler(AuthManager authManager, PasswordManager passwordManager,
                              ConfigManager config, JavaPlugin plugin) {
        this.authManager = authManager;
        this.passwordManager = passwordManager;
        this.config = config;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String cmdName = command.getName().toLowerCase();

        if ("setpass".equals(cmdName)) {
            return handleSetPass(sender, args);
        } else {
            if (!(sender instanceof Player)) {
                sender.sendMessage(config.getMessage("no-permission",
                    "&cOnly players can use this command!"));
                return true;
            }

            Player player = (Player) sender;

            switch (cmdName) {
                case "register":
                    return handleRegister(player, args);
                case "login":
                    return handleLogin(player, args);
                case "changepass":
                    return handleChangePass(player, args);
                default:
                    return false;
            }
        }
    }

    private boolean handleRegister(Player player, String[] args) {
        if (args.length != 2) {
            player.sendMessage("§cUsage: /register <password> <repeat-password>");
            return true;
        }

        String pass1 = args[0];
        String pass2 = args[1];

        // Show password strength feedback
        String strengthFeedback = passwordManager.getPasswordStrengthFeedback(pass1);
        if (strengthFeedback != null && !strengthFeedback.isEmpty()) {
            player.sendMessage("§ePassword Strength: §7" + strengthFeedback);
        }

        return authManager.register(player, pass1, pass2);
    }

    private boolean handleLogin(Player player, String[] args) {
        if (args.length != 1) {
            player.sendMessage("§cUsage: /login <password>");
            return true;
        }

        String password = args[0];
        return authManager.login(player, password);
    }

    private boolean handleChangePass(Player player, String[] args) {
        if (args.length != 3) {
            player.sendMessage("§cUsage: /changepass <old-password> <new-password> <repeat-new-password>");
            return true;
        }

        String oldPass = args[0];
        String newPass1 = args[1];
        String newPass2 = args[2];

        return authManager.changePassword(player, oldPass, newPass1, newPass2);
    }

    private boolean handleSetPass(CommandSender sender, String[] args) {
        if (!sender.hasPermission("secureauth.setpass")) {
            sender.sendMessage(config.getMessage("setpass-no-permission",
                "&cYou don't have permission to use this command!"));
            return true;
        }

        if (args.length != 2) {
            sender.sendMessage("§cUsage: /setpass <player-name> <new-password>");
            return true;
        }

        Player targetPlayer = Bukkit.getPlayer(args[0]);
        if (targetPlayer == null) {
            sender.sendMessage(config.getMessage("setpass-player-offline",
                "&cPlayer {player} is not online!")
                .replace("{player}", args[0]));
            return true;
        }

        String newPass = args[1];
        if (!passwordManager.isPasswordValid(newPass)) {
            int minLength = config.getMinPasswordLength();
            sender.sendMessage("§cPassword must be at least " + minLength + " characters!");
            return true;
        }

        authManager.setPasswordAdmin(targetPlayer, newPass);
        sender.sendMessage(config.getMessage("setpass-success",
            "&aPassword set for {player} successfully!")
            .replace("{player}", targetPlayer.getName()));
        return true;
    }
}

