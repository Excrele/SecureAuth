package com.excrele.security;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class IPFilterManager {
    private final JavaPlugin plugin;
    private final Set<String> whitelist;
    private final Set<String> blacklist;
    private File whitelistFile;
    private File blacklistFile;

    public IPFilterManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.whitelist = new HashSet<>();
        this.blacklist = new HashSet<>();
        loadIPLists();
    }

    private void loadIPLists() {
        whitelistFile = new File(plugin.getDataFolder(), "ip-whitelist.yml");
        blacklistFile = new File(plugin.getDataFolder(), "ip-blacklist.yml");

        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        // Load whitelist
        if (whitelistFile.exists()) {
            FileConfiguration config = YamlConfiguration.loadConfiguration(whitelistFile);
            List<String> ips = config.getStringList("whitelist");
            whitelist.addAll(ips);
        } else {
            try {
                whitelistFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to create whitelist file");
            }
        }

        // Load blacklist
        if (blacklistFile.exists()) {
            FileConfiguration config = YamlConfiguration.loadConfiguration(blacklistFile);
            List<String> ips = config.getStringList("blacklist");
            blacklist.addAll(ips);
        } else {
            try {
                blacklistFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to create blacklist file");
            }
        }
    }

    public boolean isWhitelisted(String ip) {
        return whitelist.contains(ip);
    }

    public boolean isBlacklisted(String ip) {
        return blacklist.contains(ip);
    }

    public void addToWhitelist(String ip) {
        whitelist.add(ip);
        saveWhitelist();
    }

    public void removeFromWhitelist(String ip) {
        whitelist.remove(ip);
        saveWhitelist();
    }

    public void addToBlacklist(String ip) {
        blacklist.add(ip);
        saveBlacklist();
    }

    public void removeFromBlacklist(String ip) {
        blacklist.remove(ip);
        saveBlacklist();
    }

    public Set<String> getWhitelist() {
        return new HashSet<>(whitelist);
    }

    public Set<String> getBlacklist() {
        return new HashSet<>(blacklist);
    }

    private void saveWhitelist() {
        try {
            FileConfiguration config = new YamlConfiguration();
            config.set("whitelist", new ArrayList<>(whitelist));
            config.save(whitelistFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save whitelist");
        }
    }

    private void saveBlacklist() {
        try {
            FileConfiguration config = new YamlConfiguration();
            config.set("blacklist", new ArrayList<>(blacklist));
            config.save(blacklistFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save blacklist");
        }
    }
}

