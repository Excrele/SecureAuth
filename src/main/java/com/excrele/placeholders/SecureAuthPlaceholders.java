package com.excrele.placeholders;

import com.excrele.auth.AuthManager;
import com.excrele.auth.SessionManager;
import com.excrele.auth.TwoFactorAuthManager;
import com.excrele.statistics.StatisticsManager;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

public class SecureAuthPlaceholders extends PlaceholderExpansion {
    private final AuthManager authManager;
    private final SessionManager sessionManager;
    private final TwoFactorAuthManager twoFactorAuthManager;
    private final StatisticsManager statisticsManager;

    public SecureAuthPlaceholders(AuthManager authManager, SessionManager sessionManager,
                                 TwoFactorAuthManager twoFactorAuthManager,
                                 StatisticsManager statisticsManager) {
        this.authManager = authManager;
        this.sessionManager = sessionManager;
        this.twoFactorAuthManager = twoFactorAuthManager;
        this.statisticsManager = statisticsManager;
    }

    @Override
    public String getIdentifier() {
        return "secureauth";
    }

    @Override
    public String getAuthor() {
        return "excrele";
    }

    @Override
    public String getVersion() {
        return "1.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String identifier) {
        if (player == null) {
            return "";
        }

        UUID playerId = player.getUniqueId();
        identifier = identifier.toLowerCase();

        switch (identifier) {
            case "logged_in":
            case "is_logged_in":
                return sessionManager.isLoggedIn(playerId) ? "Yes" : "No";

            case "registered":
            case "is_registered":
                return authManager.hasPassword(playerId) ? "Yes" : "No";

            case "2fa_enabled":
            case "has_2fa":
                return (twoFactorAuthManager.isEnabled() && twoFactorAuthManager.has2FAEnabled(playerId)) ? "Yes" : "No";

            case "last_login":
                long lastLogin = statisticsManager.getLastLoginTime(playerId);
                if (lastLogin == 0) {
                    return "Never";
                }
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                return sdf.format(new Date(lastLogin));

            case "login_count":
                return String.valueOf(statisticsManager.getLoginCount(playerId));

            case "session_time":
                if (!sessionManager.isLoggedIn(playerId)) {
                    return "Not logged in";
                }
                // This would require tracking session start time
                return "Active";

            default:
                // Global statistics
                if (identifier.startsWith("stats_")) {
                    String statType = identifier.substring(6);
                    switch (statType) {
                        case "total_registrations":
                            return String.valueOf(statisticsManager.getTotalRegistrations());
                        case "total_logins":
                            return String.valueOf(statisticsManager.getTotalLogins());
                        case "total_failed_attempts":
                            return String.valueOf(statisticsManager.getTotalFailedAttempts());
                        case "total_password_changes":
                            return String.valueOf(statisticsManager.getTotalPasswordChanges());
                        case "total_2fa_setups":
                            return String.valueOf(statisticsManager.getTotal2FASetups());
                        case "active_sessions":
                            return String.valueOf(statisticsManager.getStatistics().get("activeSessions"));
                    }
                }
                return null;
        }
    }
}

