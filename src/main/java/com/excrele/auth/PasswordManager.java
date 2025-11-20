package com.excrele.auth;

import com.excrele.config.ConfigManager;
import org.bukkit.plugin.java.JavaPlugin;

import de.mkammerer.argon2.Argon2;
import de.mkammerer.argon2.Argon2Factory;
import org.mindrot.jbcrypt.BCrypt;

import java.util.regex.Pattern;

public class PasswordManager {
    private final ConfigManager config;
    private final JavaPlugin plugin;
    private Argon2 argon2;

    public PasswordManager(ConfigManager config, JavaPlugin plugin) {
        this.config = config;
        this.plugin = plugin;
        
        if ("argon2".equalsIgnoreCase(config.getHashAlgorithm())) {
            this.argon2 = Argon2Factory.create();
        }
    }

    /**
     * Hash a password using the configured algorithm
     */
    public String hashPassword(String password) {
        String algorithm = config.getHashAlgorithm().toLowerCase();
        
        switch (algorithm) {
            case "bcrypt":
                return BCrypt.hashpw(password, BCrypt.gensalt(config.getBcryptCostFactor()));
            
            case "argon2":
                return argon2.hash(
                    config.getArgon2Iterations(),
                    config.getArgon2Memory(),
                    config.getArgon2Parallelism(),
                    password.toCharArray()
                );
            
            default:
                plugin.getLogger().warning("Unknown hash algorithm: " + algorithm + ", using bcrypt");
                return BCrypt.hashpw(password, BCrypt.gensalt(config.getBcryptCostFactor()));
        }
    }

    /**
     * Verify a password against a hash
     */
    public boolean verifyPassword(String password, String hash) {
        if (hash == null || hash.isEmpty()) {
            return false;
        }
        
        String algorithm = config.getHashAlgorithm().toLowerCase();
        
        try {
            switch (algorithm) {
                case "bcrypt":
                    return BCrypt.checkpw(password, hash);
                
                case "argon2":
                    return argon2.verify(hash, password.toCharArray());
                
                default:
                    // Try bcrypt as fallback
                    return BCrypt.checkpw(password, hash);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Password verification failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Check if password meets minimum requirements
     */
    public boolean isPasswordValid(String password) {
        if (password == null || password.length() < config.getMinPasswordLength()) {
            return false;
        }
        
        if (config.isComplexityRequired()) {
            return isPasswordComplex(password);
        }
        
        return true;
    }

    /**
     * Check if password meets complexity requirements
     */
    private boolean isPasswordComplex(String password) {
        boolean hasUpper = Pattern.compile("[A-Z]").matcher(password).find();
        boolean hasLower = Pattern.compile("[a-z]").matcher(password).find();
        boolean hasDigit = Pattern.compile("[0-9]").matcher(password).find();
        boolean hasSpecial = Pattern.compile("[^A-Za-z0-9]").matcher(password).find();
        
        return hasUpper && hasLower && hasDigit && hasSpecial;
    }

    /**
     * Get password strength feedback
     */
    public String getPasswordStrengthFeedback(String password) {
        if (password == null || password.length() < config.getMinPasswordLength()) {
            return "Password must be at least " + config.getMinPasswordLength() + " characters";
        }
        
        if (config.isComplexityRequired()) {
            boolean hasUpper = Pattern.compile("[A-Z]").matcher(password).find();
            boolean hasLower = Pattern.compile("[a-z]").matcher(password).find();
            boolean hasDigit = Pattern.compile("[0-9]").matcher(password).find();
            boolean hasSpecial = Pattern.compile("[^A-Za-z0-9]").matcher(password).find();
            
            StringBuilder feedback = new StringBuilder("Password needs: ");
            boolean needsComma = false;
            
            if (!hasUpper) {
                feedback.append("uppercase letter");
                needsComma = true;
            }
            if (!hasLower) {
                if (needsComma) feedback.append(", ");
                feedback.append("lowercase letter");
                needsComma = true;
            }
            if (!hasDigit) {
                if (needsComma) feedback.append(", ");
                feedback.append("number");
                needsComma = true;
            }
            if (!hasSpecial) {
                if (needsComma) feedback.append(", ");
                feedback.append("special character");
            }
            
            return feedback.toString();
        }
        
        return null; // No complexity requirements
    }
}

