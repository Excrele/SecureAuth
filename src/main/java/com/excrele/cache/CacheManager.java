package com.excrele.cache;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CacheManager {
    private final JavaPlugin plugin;
    private final Map<String, Boolean> premiumCache = new ConcurrentHashMap<>(); // username -> isPremium
    private final Map<String, Long> premiumCacheTime = new ConcurrentHashMap<>(); // username -> timestamp
    private final Map<UUID, String> passwordHashCache = new ConcurrentHashMap<>(); // UUID -> hash
    private static final long PREMIUM_CACHE_TTL = 30 * 60 * 1000; // 30 minutes
    private static final long PASSWORD_CACHE_TTL = 5 * 60 * 1000; // 5 minutes

    public CacheManager(JavaPlugin plugin) {
        this.plugin = plugin;
        
        // Start cache cleanup task
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            long now = System.currentTimeMillis();
            
            // Clean expired premium cache
            premiumCacheTime.entrySet().removeIf(entry -> {
                if (now - entry.getValue() > PREMIUM_CACHE_TTL) {
                    premiumCache.remove(entry.getKey());
                    return true;
                }
                return false;
            });
            
            // Password cache is cleared on password change, so we don't need TTL cleanup
            // (it's short-lived and cleared when needed)
        }, 6000L, 6000L); // Every 5 minutes
    }

    public Boolean getPremiumStatus(String username) {
        String lowerUsername = username.toLowerCase();
        Long cacheTime = premiumCacheTime.get(lowerUsername);
        
        if (cacheTime != null && (System.currentTimeMillis() - cacheTime) < PREMIUM_CACHE_TTL) {
            return premiumCache.get(lowerUsername);
        }
        
        return null; // Cache miss
    }

    public void setPremiumStatus(String username, boolean isPremium) {
        String lowerUsername = username.toLowerCase();
        premiumCache.put(lowerUsername, isPremium);
        premiumCacheTime.put(lowerUsername, System.currentTimeMillis());
    }

    public String getPasswordHash(UUID playerId) {
        return passwordHashCache.get(playerId);
    }

    public void setPasswordHash(UUID playerId, String hash) {
        passwordHashCache.put(playerId, hash);
    }

    public void invalidatePasswordHash(UUID playerId) {
        passwordHashCache.remove(playerId);
    }

    public void clearAll() {
        premiumCache.clear();
        premiumCacheTime.clear();
        passwordHashCache.clear();
    }

    public void clearPremiumCache() {
        premiumCache.clear();
        premiumCacheTime.clear();
    }
}

