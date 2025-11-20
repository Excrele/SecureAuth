package com.excrele.auth;

import com.excrele.config.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SessionManager {
    private final ConfigManager config;
    private final JavaPlugin plugin;
    private final Map<UUID, Boolean> loggedInPlayers;
    private final Map<UUID, Long> lastActivity;
    private final Map<UUID, Long> sessionStartTime; // Track when session started
    private final Map<UUID, String> sessionIPs; // Track IP per session
    private BukkitTask timeoutTask;

    public SessionManager(ConfigManager config, JavaPlugin plugin) {
        this.config = config;
        this.plugin = plugin;
        this.loggedInPlayers = new HashMap<>();
        this.lastActivity = new HashMap<>();
        this.sessionStartTime = new HashMap<>();
        this.sessionIPs = new HashMap<>();
    }

    public void start() {
        long interval = config.getCheckIntervalSeconds() * 20L; // Convert to ticks
        
        timeoutTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            long timeout = config.getSessionTimeoutMinutes() * 60 * 1000;
            
            if (timeout <= 0) {
                return; // Timeout disabled
            }
            
            loggedInPlayers.entrySet().removeIf(entry -> {
                UUID playerId = entry.getKey();
                Long lastActive = lastActivity.get(playerId);
                
                if (lastActive == null || (now - lastActive) > timeout) {
                    Player player = Bukkit.getPlayer(playerId);
                    if (player != null && player.isOnline()) {
                        player.sendMessage(config.getMessage("session-timeout",
                            "&cSession timed out due to inactivity! Please login again."));
                    }
                    lastActivity.remove(playerId);
                    return true;
                }
                return false;
            });
        }, interval, interval);
    }

    public void stop() {
        if (timeoutTask != null) {
            timeoutTask.cancel();
        }
        loggedInPlayers.clear();
        lastActivity.clear();
        sessionStartTime.clear();
        sessionIPs.clear();
    }

    public boolean isLoggedIn(UUID playerId) {
        return loggedInPlayers.getOrDefault(playerId, false);
    }

    public void setLoggedIn(UUID playerId, boolean loggedIn) {
        setLoggedIn(playerId, loggedIn, null);
    }

    public void setLoggedIn(UUID playerId, boolean loggedIn, String ip) {
        if (loggedIn) {
            loggedInPlayers.put(playerId, true);
            sessionStartTime.put(playerId, System.currentTimeMillis());
            if (ip != null) {
                sessionIPs.put(playerId, ip);
            }
            updateActivity(playerId);
        } else {
            loggedInPlayers.remove(playerId);
            lastActivity.remove(playerId);
            sessionStartTime.remove(playerId);
            sessionIPs.remove(playerId);
        }
    }

    public void updateActivity(UUID playerId) {
        lastActivity.put(playerId, System.currentTimeMillis());
    }

    public void removePlayer(UUID playerId) {
        loggedInPlayers.remove(playerId);
        lastActivity.remove(playerId);
        sessionStartTime.remove(playerId);
        sessionIPs.remove(playerId);
    }

    public long getSessionDuration(UUID playerId) {
        Long startTime = sessionStartTime.get(playerId);
        if (startTime == null) {
            return 0;
        }
        return System.currentTimeMillis() - startTime;
    }

    public String getSessionIP(UUID playerId) {
        return sessionIPs.get(playerId);
    }

    public boolean hasActiveSession(UUID playerId) {
        return loggedInPlayers.getOrDefault(playerId, false);
    }

    public void clearAll() {
        loggedInPlayers.clear();
        lastActivity.clear();
        sessionStartTime.clear();
        sessionIPs.clear();
    }
}

