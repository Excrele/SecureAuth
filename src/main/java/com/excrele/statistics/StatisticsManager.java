package com.excrele.statistics;

import com.excrele.auth.AuthManager;
import com.excrele.auth.SessionManager;
import com.excrele.config.ConfigManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public class StatisticsManager {
    private final ConfigManager config;
    private final JavaPlugin plugin;
    private final AtomicLong totalRegistrations = new AtomicLong(0);
    private final AtomicLong totalLogins = new AtomicLong(0);
    private final AtomicLong totalFailedAttempts = new AtomicLong(0);
    private final AtomicLong totalPasswordChanges = new AtomicLong(0);
    private final AtomicLong total2FASetups = new AtomicLong(0);
    private final Map<UUID, Long> lastLoginTime = new HashMap<>();
    private final Map<UUID, Integer> loginCount = new HashMap<>();

    public StatisticsManager(ConfigManager config, JavaPlugin plugin) {
        this.config = config;
        this.plugin = plugin;
    }

    public void recordRegistration() {
        totalRegistrations.incrementAndGet();
    }

    public void recordLogin(UUID playerId) {
        totalLogins.incrementAndGet();
        lastLoginTime.put(playerId, System.currentTimeMillis());
        loginCount.put(playerId, loginCount.getOrDefault(playerId, 0) + 1);
    }

    public void recordFailedAttempt() {
        totalFailedAttempts.incrementAndGet();
    }

    public void recordPasswordChange() {
        totalPasswordChanges.incrementAndGet();
    }

    public void record2FASetup() {
        total2FASetups.incrementAndGet();
    }

    public long getTotalRegistrations() {
        return totalRegistrations.get();
    }

    public long getTotalLogins() {
        return totalLogins.get();
    }

    public long getTotalFailedAttempts() {
        return totalFailedAttempts.get();
    }

    public long getTotalPasswordChanges() {
        return totalPasswordChanges.get();
    }

    public long getTotal2FASetups() {
        return total2FASetups.get();
    }

    public long getLastLoginTime(UUID playerId) {
        return lastLoginTime.getOrDefault(playerId, 0L);
    }

    public int getLoginCount(UUID playerId) {
        return loginCount.getOrDefault(playerId, 0);
    }

    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalRegistrations", totalRegistrations.get());
        stats.put("totalLogins", totalLogins.get());
        stats.put("totalFailedAttempts", totalFailedAttempts.get());
        stats.put("totalPasswordChanges", totalPasswordChanges.get());
        stats.put("total2FASetups", total2FASetups.get());
        stats.put("activeSessions", lastLoginTime.size());
        return stats;
    }
}

