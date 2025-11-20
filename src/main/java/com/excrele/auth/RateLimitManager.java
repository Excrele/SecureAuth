package com.excrele.auth;

import com.excrele.config.ConfigManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class RateLimitManager {
    private final ConfigManager config;
    private final JavaPlugin plugin;
    private final Map<UUID, Integer> failedAttempts;
    private final Map<UUID, Long> lockoutEnds;
    private final Map<UUID, Long> playerLastAttempt;
    private final Map<String, Integer> ipFailedAttempts;
    private final Map<String, Long> ipLockoutEnds;
    private final Map<String, Long> ipLastAttempt;
    private final Map<UUID, Integer> lockoutCount = new HashMap<>(); // Track how many times locked out
    private final Map<String, Integer> ipLockoutCount = new HashMap<>(); // Track IP lockout count
    private BukkitTask cleanupTask;

    public RateLimitManager(ConfigManager config, JavaPlugin plugin) {
        this.config = config;
        this.plugin = plugin;
        this.failedAttempts = new HashMap<>();
        this.lockoutEnds = new HashMap<>();
        this.playerLastAttempt = new HashMap<>();
        this.ipFailedAttempts = new HashMap<>();
        this.ipLockoutEnds = new HashMap<>();
        this.ipLastAttempt = new HashMap<>();
    }

    public void start() {
        long interval = config.getCheckIntervalSeconds() * 20L;
        long resetDuration = config.getAttemptResetMinutes() * 60 * 1000;
        
        cleanupTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            
            // Reset old player attempt counts
            failedAttempts.entrySet().removeIf(e -> {
                UUID id = e.getKey();
                Long last = playerLastAttempt.get(id);
                if (last != null && (now - last) > resetDuration) {
                    playerLastAttempt.remove(id);
                    return true;
                }
                return false;
            });
            
            // Clean old player lockouts
            lockoutEnds.entrySet().removeIf(e -> {
                Long end = e.getValue();
                return end != null && now > end;
            });
            
            if (config.isIpLimitsEnabled()) {
                // Reset old IP attempt counts
                ipFailedAttempts.entrySet().removeIf(e -> {
                    String ip = e.getKey();
                    Long last = ipLastAttempt.get(ip);
                    if (last != null && (now - last) > resetDuration) {
                        ipLastAttempt.remove(ip);
                        return true;
                    }
                    return false;
                });
                
                // Clean old IP lockouts
                ipLockoutEnds.entrySet().removeIf(e -> {
                    Long end = e.getValue();
                    return end != null && now > end;
                });
            }
        }, interval, interval);
    }

    public void stop() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
        }
        failedAttempts.clear();
        lockoutEnds.clear();
        playerLastAttempt.clear();
        ipFailedAttempts.clear();
        ipLockoutEnds.clear();
        ipLastAttempt.clear();
    }

    public boolean isLockedOut(UUID playerId) {
        long now = System.currentTimeMillis();
        Long end = lockoutEnds.get(playerId);
        return end != null && now < end;
    }

    public boolean isIpLockedOut(String ip) {
        if (!config.isIpLimitsEnabled()) {
            return false;
        }
        long now = System.currentTimeMillis();
        Long end = ipLockoutEnds.get(ip);
        return end != null && now < end;
    }

    public long getRemainingLockoutTime(UUID playerId) {
        long now = System.currentTimeMillis();
        Long end = lockoutEnds.get(playerId);
        if (end != null && now < end) {
            return (end - now) / 1000 / 60; // minutes
        }
        return 0;
    }

    public long getRemainingIpLockoutTime(String ip) {
        if (!config.isIpLimitsEnabled()) {
            return 0;
        }
        long now = System.currentTimeMillis();
        Long end = ipLockoutEnds.get(ip);
        if (end != null && now < end) {
            return (end - now) / 1000 / 60; // minutes
        }
        return 0;
    }

    public void recordFailedAttempt(UUID playerId, String ip) {
        long now = System.currentTimeMillis();
        int maxAttempts = config.getMaxAttempts();
        long baseLockoutDuration = config.getLockoutDurationMinutes() * 60 * 1000;
        
        // Player attempts
        playerLastAttempt.put(playerId, now);
        int playerAttempts = failedAttempts.getOrDefault(playerId, 0) + 1;
        failedAttempts.put(playerId, playerAttempts);
        
        if (playerAttempts >= maxAttempts) {
            int lockoutNum = lockoutCount.getOrDefault(playerId, 0) + 1;
            lockoutCount.put(playerId, lockoutNum);
            
            // Progressive lockout: 1st = base, 2nd = base*3, 3rd = base*12, etc.
            long lockoutDuration = baseLockoutDuration;
            if (config.isProgressiveLockouts()) {
                lockoutDuration = baseLockoutDuration * (long) Math.pow(3, lockoutNum - 1);
            }
            
            lockoutEnds.put(playerId, now + lockoutDuration);
            failedAttempts.remove(playerId);
            playerLastAttempt.remove(playerId);
        }
        
        // IP attempts
        if (config.isIpLimitsEnabled()) {
            ipLastAttempt.put(ip, now);
            int ipAttempts = ipFailedAttempts.getOrDefault(ip, 0) + 1;
            ipFailedAttempts.put(ip, ipAttempts);
            
            if (ipAttempts >= maxAttempts) {
                int ipLockoutNum = ipLockoutCount.getOrDefault(ip, 0) + 1;
                ipLockoutCount.put(ip, ipLockoutNum);
                
                long ipLockoutDuration = baseLockoutDuration;
                if (config.isProgressiveLockouts()) {
                    ipLockoutDuration = baseLockoutDuration * (long) Math.pow(3, ipLockoutNum - 1);
                }
                
                ipLockoutEnds.put(ip, now + ipLockoutDuration);
                ipFailedAttempts.remove(ip);
                ipLastAttempt.remove(ip);
            }
        }
    }

    public int getAttemptCount(UUID playerId) {
        return failedAttempts.getOrDefault(playerId, 0);
    }

    public int getIpAttemptCount(String ip) {
        if (!config.isIpLimitsEnabled()) {
            return 0;
        }
        return ipFailedAttempts.getOrDefault(ip, 0);
    }

    public void clearAttempts(UUID playerId, String ip) {
        failedAttempts.remove(playerId);
        lockoutEnds.remove(playerId);
        playerLastAttempt.remove(playerId);
        
        if (config.isIpLimitsEnabled()) {
            ipFailedAttempts.remove(ip);
            ipLockoutEnds.remove(ip);
            ipLastAttempt.remove(ip);
        }
    }

    public void clearAll() {
        failedAttempts.clear();
        lockoutEnds.clear();
        playerLastAttempt.clear();
        ipFailedAttempts.clear();
        ipLockoutEnds.clear();
        ipLastAttempt.clear();
    }
}

