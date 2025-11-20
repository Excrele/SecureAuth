package com.excrele.auth;

import com.excrele.config.ConfigManager;
import com.excrele.database.DatabaseManager;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.*;

public class TwoFactorAuthManager {
    private final ConfigManager config;
    private final DatabaseManager databaseManager;
    private final JavaPlugin plugin;
    private final Map<UUID, String> pending2FASetup = new HashMap<>(); // UUID -> secret key
    private final Map<UUID, List<String>> backupCodes = new HashMap<>(); // UUID -> backup codes

    public TwoFactorAuthManager(ConfigManager config, DatabaseManager databaseManager, JavaPlugin plugin) {
        this.config = config;
        this.databaseManager = databaseManager;
        this.plugin = plugin;
    }

    public boolean isEnabled() {
        return config.is2FAEnabled();
    }

    public boolean isRequired() {
        return config.is2FARequired();
    }

    public boolean has2FAEnabled(UUID playerId) {
        if (!isEnabled()) {
            return false;
        }
        
        if (databaseManager.isFileBased()) {
            // For file-based, we'd need to add 2FA data to the file
            // For now, return false - can be enhanced later
            return false;
        }
        
        try {
            String secret = get2FASecret(playerId);
            return secret != null && !secret.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    public String generateSecretKey() {
        SecureRandom random = new SecureRandom();
        byte[] key = new byte[20];
        random.nextBytes(key);
        return Base32.encode(key);
    }

    public String generateQRCodeURL(UUID playerId, String playerName, String secret) {
        String issuer = config.get2FATOTPIssuer().replace(" ", "%20");
        String account = playerName.replace(" ", "%20");
        return String.format("otpauth://totp/%s:%s?secret=%s&issuer=%s",
            issuer, account, secret, issuer);
    }

    public String getPendingSecret(UUID playerId) {
        return pending2FASetup.get(playerId);
    }

    public boolean verifyTOTP(UUID playerId, String code) {
        if (!isEnabled() || !has2FAEnabled(playerId)) {
            return false;
        }

        String secret = get2FASecret(playerId);
        if (secret == null) {
            return false;
        }

        // Check TOTP code
        if (verifyTOTPCode(secret, code)) {
            return true;
        }

        // Check backup codes
        return verifyBackupCode(playerId, code);
    }

    private boolean verifyTOTPCode(String secret, String code) {
        try {
            long currentTime = System.currentTimeMillis() / 1000 / 30; // 30-second window
            
            // Check current time window and ±1 window for clock skew
            for (int i = -1; i <= 1; i++) {
                if (generateTOTP(secret, currentTime + i).equals(code)) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            plugin.getLogger().warning("TOTP verification error: " + e.getMessage());
            return false;
        }
    }

    private String generateTOTP(String secret, long time) throws NoSuchAlgorithmException, InvalidKeyException {
        byte[] key = Base32.decode(secret);
        byte[] timeBytes = ByteBuffer.allocate(8).putLong(time).array();

        Mac mac = Mac.getInstance("HmacSHA1");
        SecretKeySpec keySpec = new SecretKeySpec(key, "HmacSHA1");
        mac.init(keySpec);
        byte[] hash = mac.doFinal(timeBytes);

        int offset = hash[hash.length - 1] & 0x0F;
        int binary = ((hash[offset] & 0x7F) << 24) |
                     ((hash[offset + 1] & 0xFF) << 16) |
                     ((hash[offset + 2] & 0xFF) << 8) |
                     (hash[offset + 3] & 0xFF);

        int otp = binary % 1000000;
        return String.format("%06d", otp);
    }

    public boolean setup2FA(Player player) {
        if (!isEnabled()) {
            return false;
        }

        UUID playerId = player.getUniqueId();
        String secret = generateSecretKey();
        pending2FASetup.put(playerId, secret);

        // Generate backup codes
        List<String> codes = generateBackupCodes();
        backupCodes.put(playerId, codes);

        // Save to database
        save2FASecret(playerId, secret);
        saveBackupCodes(playerId, codes);

        return true;
    }

    public void disable2FA(UUID playerId) {
        if (databaseManager.isFileBased()) {
            // File-based handling
            return;
        }
        
        delete2FASecret(playerId);
        backupCodes.remove(playerId);
    }

    private List<String> generateBackupCodes() {
        List<String> codes = new ArrayList<>();
        SecureRandom random = new SecureRandom();
        for (int i = 0; i < config.get2FABackupCodesCount(); i++) {
            int code = random.nextInt(900000) + 100000; // 6-digit code
            codes.add(String.valueOf(code));
        }
        return codes;
    }

    private boolean verifyBackupCode(UUID playerId, String code) {
        List<String> codes = getBackupCodes(playerId);
        if (codes == null || !codes.contains(code)) {
            return false;
        }
        
        // Remove used backup code
        codes.remove(code);
        saveBackupCodes(playerId, codes);
        return true;
    }

    private String get2FASecret(UUID playerId) {
        if (databaseManager.isFileBased()) {
            return null; // File-based not supported for 2FA yet
        }
        
        try {
            return databaseManager.get2FASecret(playerId);
        } catch (Exception e) {
            return null;
        }
    }

    private void save2FASecret(UUID playerId, String secret) {
        if (databaseManager.isFileBased()) {
            return; // File-based not supported for 2FA yet
        }
        
        databaseManager.set2FASecret(playerId, secret);
    }

    private void delete2FASecret(UUID playerId) {
        if (databaseManager.isFileBased()) {
            return;
        }
        
        databaseManager.delete2FASecret(playerId);
    }

    private List<String> getBackupCodes(UUID playerId) {
        if (databaseManager.isFileBased()) {
            return backupCodes.get(playerId);
        }
        
        return databaseManager.get2FABackupCodes(playerId);
    }

    private void saveBackupCodes(UUID playerId, List<String> codes) {
        if (databaseManager.isFileBased()) {
            backupCodes.put(playerId, codes);
            return;
        }
        
        databaseManager.set2FABackupCodes(playerId, codes);
    }

    public List<String> getBackupCodesForPlayer(UUID playerId) {
        return getBackupCodes(playerId);
    }

    // Base32 encoding/decoding helper
    private static class Base32 {
        private static final String BASE32_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

        public static String encode(byte[] data) {
            StringBuilder result = new StringBuilder();
            int buffer = 0;
            int bitsLeft = 0;

            for (byte b : data) {
                buffer = (buffer << 8) | (b & 0xFF);
                bitsLeft += 8;

                while (bitsLeft >= 5) {
                    result.append(BASE32_CHARS.charAt((buffer >> (bitsLeft - 5)) & 0x1F));
                    bitsLeft -= 5;
                }
            }

            if (bitsLeft > 0) {
                result.append(BASE32_CHARS.charAt((buffer << (5 - bitsLeft)) & 0x1F));
            }

            return result.toString();
        }

        public static byte[] decode(String encoded) {
            encoded = encoded.toUpperCase().replaceAll("[^A-Z2-7]", "");
            int buffer = 0;
            int bitsLeft = 0;
            List<Byte> result = new ArrayList<>();

            for (char c : encoded.toCharArray()) {
                int value = BASE32_CHARS.indexOf(c);
                if (value < 0) continue;

                buffer = (buffer << 5) | value;
                bitsLeft += 5;

                if (bitsLeft >= 8) {
                    result.add((byte) ((buffer >> (bitsLeft - 8)) & 0xFF));
                    bitsLeft -= 8;
                }
            }

            byte[] bytes = new byte[result.size()];
            for (int i = 0; i < result.size(); i++) {
                bytes[i] = result.get(i);
            }
            return bytes;
        }
    }
}

