package com.excrele.auth;

import com.excrele.config.ConfigManager;
import com.excrele.database.DatabaseManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.security.SecureRandom;
import java.util.*;

public class PasswordRecoveryManager {
    private final ConfigManager config;
    private final DatabaseManager databaseManager;
    private final JavaPlugin plugin;
    private final Map<String, RecoveryToken> recoveryTokens = new HashMap<>(); // token -> RecoveryToken
    private final Map<UUID, List<String>> securityQuestions = new HashMap<>(); // UUID -> questions/answers

    public PasswordRecoveryManager(ConfigManager config, DatabaseManager databaseManager, JavaPlugin plugin) {
        this.config = config;
        this.databaseManager = databaseManager;
        this.plugin = plugin;
    }

    public String generateRecoveryToken(UUID playerId) {
        SecureRandom random = new SecureRandom();
        byte[] tokenBytes = new byte[32];
        random.nextBytes(tokenBytes);
        String token = Base64.getEncoder().encodeToString(tokenBytes);
        
        RecoveryToken recoveryToken = new RecoveryToken(playerId, System.currentTimeMillis() + (24 * 60 * 60 * 1000)); // 24 hours
        recoveryTokens.put(token, recoveryToken);
        
        return token;
    }

    public boolean isValidToken(String token) {
        RecoveryToken recoveryToken = recoveryTokens.get(token);
        if (recoveryToken == null) {
            return false;
        }
        
        if (System.currentTimeMillis() > recoveryToken.getExpiryTime()) {
            recoveryTokens.remove(token);
            return false;
        }
        
        return true;
    }

    public UUID getPlayerIdFromToken(String token) {
        RecoveryToken recoveryToken = recoveryTokens.get(token);
        if (recoveryToken != null && isValidToken(token)) {
            return recoveryToken.getPlayerId();
        }
        return null;
    }

    public void consumeToken(String token) {
        recoveryTokens.remove(token);
    }

    public void setSecurityQuestion(UUID playerId, String question, String answer) {
        List<String> qa = new ArrayList<>();
        qa.add(question);
        qa.add(answer.toLowerCase().trim()); // Store answer in lowercase for comparison
        securityQuestions.put(playerId, qa);
        
        if (!databaseManager.isFileBased()) {
            databaseManager.setSecurityQuestion(playerId, question, answer);
        }
    }

    public boolean verifySecurityAnswer(UUID playerId, String answer) {
        List<String> qa = securityQuestions.get(playerId);
        if (qa == null || qa.size() < 2) {
            if (!databaseManager.isFileBased()) {
                String storedAnswer = databaseManager.getSecurityAnswer(playerId);
                if (storedAnswer != null) {
                    return storedAnswer.equalsIgnoreCase(answer.trim());
                }
            }
            return false;
        }
        
        return qa.get(1).equalsIgnoreCase(answer.trim());
    }

    public String getSecurityQuestion(UUID playerId) {
        List<String> qa = securityQuestions.get(playerId);
        if (qa != null && !qa.isEmpty()) {
            return qa.get(0);
        }
        
        if (!databaseManager.isFileBased()) {
            return databaseManager.getSecurityQuestion(playerId);
        }
        
        return null;
    }

    public boolean hasSecurityQuestion(UUID playerId) {
        if (securityQuestions.containsKey(playerId)) {
            return true;
        }
        
        if (!databaseManager.isFileBased()) {
            return databaseManager.hasSecurityQuestion(playerId);
        }
        
        return false;
    }

    private static class RecoveryToken {
        private final UUID playerId;
        private final long expiryTime;

        public RecoveryToken(UUID playerId, long expiryTime) {
            this.playerId = playerId;
            this.expiryTime = expiryTime;
        }

        public UUID getPlayerId() {
            return playerId;
        }

        public long getExpiryTime() {
            return expiryTime;
        }
    }
}

