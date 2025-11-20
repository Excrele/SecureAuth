package com.excrele.listeners;

import com.excrele.auth.AuthManager;
import com.excrele.auth.SessionManager;
import com.excrele.config.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;

public class PlayerEventListener implements Listener {
    private final SessionManager sessionManager;
    private final AuthManager authManager;
    private final ConfigManager config;
    private final JavaPlugin plugin;

    public PlayerEventListener(SessionManager sessionManager, AuthManager authManager,
                               ConfigManager config, JavaPlugin plugin) {
        this.sessionManager = sessionManager;
        this.authManager = authManager;
        this.config = config;
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        // Premium check and handle
        if (config.isPremiumAutoLogin()) {
            authManager.checkPremiumAndHandle(player);
        } else {
            // Treat as cracked
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (authManager.hasPassword(player.getUniqueId())) {
                    player.sendMessage(config.getMessage("welcome-registered",
                        "&ePlease login with /login <password>"));
                } else {
                    player.sendMessage(config.getMessage("welcome-cracked",
                        "&eWelcome! Please register with /register <password> <repeat>"));
                }
                sessionManager.setLoggedIn(player.getUniqueId(), false);
            });
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        sessionManager.removePlayer(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    @SuppressWarnings("deprecation") // AsyncPlayerChatEvent is deprecated but still functional
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        if (!config.isBlockChat()) {
            return;
        }
        
        Player player = event.getPlayer();
        
        // Check bypass permission
        if (player.hasPermission("secureauth.bypass")) {
            return;
        }
        
        if (!sessionManager.isLoggedIn(player.getUniqueId())) {
            event.setCancelled(true);
            player.sendMessage(config.getMessage("restriction-chat",
                "&cYou must login first! Use /login <password>"));
        } else {
            sessionManager.updateActivity(player.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!config.isBlockMovement()) {
            return;
        }
        
        Player player = event.getPlayer();
        
        // Check bypass permission
        if (player.hasPermission("secureauth.bypass")) {
            return;
        }
        
        if (!sessionManager.isLoggedIn(player.getUniqueId())) {
            Location from = event.getFrom();
            Location to = event.getTo();
            
            if (to != null && (to.getBlockX() != from.getBlockX() || 
                              to.getBlockY() != from.getBlockY() || 
                              to.getBlockZ() != from.getBlockZ())) {
                event.setTo(from);
                player.sendMessage(config.getMessage("restriction-movement",
                    "&cYou're frozen until login! Use /login <password>"));
            }
        } else {
            Location from = event.getFrom();
            Location to = event.getTo();
            if (to != null && (to.getBlockX() != from.getBlockX() || 
                              to.getBlockY() != from.getBlockY() || 
                              to.getBlockZ() != from.getBlockZ())) {
                sessionManager.updateActivity(player.getUniqueId());
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!config.isBlockBuilding()) {
            return;
        }
        
        Player player = event.getPlayer();
        
        // Check bypass permission
        if (player.hasPermission("secureauth.bypass")) {
            return;
        }
        
        if (!sessionManager.isLoggedIn(player.getUniqueId())) {
            event.setCancelled(true);
            player.sendMessage(config.getMessage("restriction-building",
                "&cCan't build until login! Use /login <password>"));
        } else {
            sessionManager.updateActivity(player.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!config.isBlockMining()) {
            return;
        }
        
        Player player = event.getPlayer();
        
        // Check bypass permission
        if (player.hasPermission("secureauth.bypass")) {
            return;
        }
        
        if (!sessionManager.isLoggedIn(player.getUniqueId())) {
            event.setCancelled(true);
            player.sendMessage(config.getMessage("restriction-mining",
                "&cCan't mine until login! Use /login <password>"));
        } else {
            sessionManager.updateActivity(player.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!config.isBlockInteractions()) {
            return;
        }
        
        Player player = event.getPlayer();
        
        // Check bypass permission
        if (player.hasPermission("secureauth.bypass")) {
            return;
        }
        
        if (!sessionManager.isLoggedIn(player.getUniqueId())) {
            event.setCancelled(true);
            player.sendMessage(config.getMessage("restriction-interaction",
                "&cCan't interact until login! Use /login <password>"));
        } else {
            sessionManager.updateActivity(player.getUniqueId());
        }
    }
}

