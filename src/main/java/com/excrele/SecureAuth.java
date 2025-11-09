// SecureAuth.java - This is the WHOLE plugin in ONE file! It's like a Swiss Army knife: handles joining players, commands, secure passwords, chat blocking, movement blocking, building blocking, session timeouts, password changes, login attempt limits, and now attempt reset timers + IP-based limits too.
// We're keeping it simple with few files (just this + plugin.yml), but modular with clear sections like chapters in a book.

package com.excrele;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

// The main class: SecureAuth! It extends JavaPlugin (makes it a plugin) and implements Listener (watches for player events) + CommandExecutor (handles /commands).
public class SecureAuth extends JavaPlugin implements Listener, CommandExecutor {

    // SECTION 1: Storage - Like a notebook in the server's memory. It tracks who's logged in (true/false) while the server runs.
    // We use a HashMap: Key is player's unique ID (UUID, like a fingerprint), value is if they're logged in.
    private Map<UUID, Boolean> loggedInPlayers = new HashMap<>();

    // Activity Tracker: Another notebook page that remembers when each logged-in player last did something fun (like chat or move).
    // Key: UUID, Value: Timestamp (a number for "when"). If too old (idle too long), we log 'em out for safety!
    private Map<UUID, Long> lastActivity = new HashMap<>();

    // Failed Attempts Tracker: A counter for how many wrong /login tries a player has made. Like a "strikes" list - too many, and you're benched!
    // Key: UUID, Value: Number of fails. Resets on success or after lockout.
    private Map<UUID, Integer> failedAttempts = new HashMap<>();

    // Lockout Timer: When someone hits too many fails, we lock their login for a bit. This map holds when the "time out" ends.
    // Key: UUID, Value: Timestamp (future time when they can try again). Keeps hackers guessing without spamming!
    private Map<UUID, Long> lockoutEnds = new HashMap<>();

    // NEW! Last Attempt Time: To reset old failed counts fairly, we track when the last wrong guess happened.
    // Key: UUID, Value: Timestamp of last fail. If too old, we forgive and reset the count!
    private Map<UUID, Long> playerLastAttempt = new HashMap<>();

    // NEW! IP-Based Limits: Like player limits, but for the internet address (IP). Stops one person trying many accounts from the same computer!
    // Failed Attempts for IP: Key: IP string (like "192.168.1.1"), Value: Number of fails from that IP.
    private Map<String, Integer> ipFailedAttempts = new HashMap<>();

    // Lockout for IP: Key: IP, Value: End time of lockout. If IP is bad, everyone from it waits!
    private Map<String, Long> ipLockoutEnds = new HashMap<>();

    // Last Attempt for IP: Key: IP, Value: Timestamp of last fail from that IP.
    private Map<String, Long> ipLastAttempt = new HashMap<>();

    // Password storage: A file called passwords.txt (simple text, one line per player: UUID:hashedPassword).
    // Why hashed? It's scrambled so even if someone steals the file, they can't read real passwords. Super secret!
    private File passwordFile;

    // Session Timeout: How long can a player chill without activity before we say "time's up, log in again!"?
    // Set to 30 minutes (in milliseconds - 30 min * 60 sec * 1000 ms). Easy to tweak!
    private long SESSION_TIMEOUT = 30L * 60 * 1000;

    // Login Limits: How many wrong tries before lockout? And how long is the lock (5 min here)?
    private static final int MAX_ATTEMPTS = 3;  // 3 strikes, you're out!
    private static final long LOCKOUT_DURATION = 5L * 60 * 1000;  // 5 minutes in the penalty box.

    // NEW! Attempt Reset Timer: How long before we forget old wrong guesses and reset the count? (5 min here - forgiving!)
    private static final long RESET_DURATION = 5L * 60 * 1000;  // 5 minutes, then clean slate for attempts.

    // Timeout Checker: A repeating job (like a timer) that runs every minute to scan for idle players.
    private BukkitTask timeoutTask;

    // SECTION 2: Startup - What happens when the server loads our plugin (onEnable).
    @Override
    public void onEnable() {
        // Tell the server: "Watch for player join/quit events, chat events, movement events, AND block events!" This lets us react when someone connects, types in chat, tries to walk, or messes with blocks.
        getServer().getPluginManager().registerEvents(this, this);

        // Set up our password file in the plugin's folder (plugins/SecureAuth/passwords.txt).
        passwordFile = new File(getDataFolder(), "passwords.txt");
        if (!passwordFile.exists()) {
            try {
                getDataFolder().mkdirs();  // Create the folder if it doesn't exist.
                passwordFile.createNewFile();  // Make the empty file.
                getLogger().info("Created new passwords.txt - ready for secure logins!");
            } catch (IOException e) {
                getLogger().log(Level.SEVERE, "Failed to create passwords.txt! Check file permissions.", e);
            }
        }

        // Hook up our commands: When someone types /register, /login, /changepass, or /setpass, this class handles it.
        this.getCommand("register").setExecutor(this);
        this.getCommand("login").setExecutor(this);
        this.getCommand("changepass").setExecutor(this);
        this.getCommand("setpass").setExecutor(this);

        // Start the session timeout checker: Every 60 seconds (1200 ticks - Minecraft runs 20 ticks/sec), check for idle players.
        // It's like a security guard patrolling: "Anyone AFK too long? Log out!" Now also cleans old attempt counts & locks.
        timeoutTask = getServer().getScheduler().runTaskTimer(this, () -> {
            long now = System.currentTimeMillis();  // Grab the current time stamp.

            // First, session timeout for logged-in players.
            loggedInPlayers.entrySet().removeIf(entry -> {
                UUID playerId = entry.getKey();
                Long lastActive = lastActivity.get(playerId);
                if (lastActive == null || (now - lastActive) > SESSION_TIMEOUT) {
                    // Too idle! Log 'em out.
                    Player player = Bukkit.getPlayer(playerId);
                    if (player != null && player.isOnline()) {
                        player.sendMessage("§c[SecureAuth] Session timed out due to inactivity! Type §b/login <password> §cto log back in securely.");
                    }
                    lastActivity.remove(playerId);  // Forget their activity too.
                    return true;  // Remove from logged-in list.
                }
                return false;  // Keep 'em logged in.
            });

            // NEW! Reset old player attempt counts: If last fail was too long ago, forgive and forget!
            failedAttempts.entrySet().removeIf(e -> {
                UUID id = e.getKey();
                Long last = playerLastAttempt.get(id);
                if (last != null && (now - last) > RESET_DURATION) {
                    playerLastAttempt.remove(id);
                    return true;  // Wipe the count.
                }
                return false;
            });

            // NEW! Clean old player lockouts: If lock time passed, unlock 'em.
            lockoutEnds.entrySet().removeIf(e -> {
                UUID id = e.getKey();
                Long end = e.getValue();
                if (end != null && now > end) {
                    return true;  // Unlock!
                }
                return false;
            });

            // NEW! Same for IPs: Reset old IP attempt counts.
            ipFailedAttempts.entrySet().removeIf(e -> {
                String ip = e.getKey();
                Long last = ipLastAttempt.get(ip);
                if (last != null && (now - last) > RESET_DURATION) {
                    ipLastAttempt.remove(ip);
                    return true;  // Wipe the IP count.
                }
                return false;
            });

            // NEW! Clean old IP lockouts.
            ipLockoutEnds.entrySet().removeIf(e -> {
                String ip = e.getKey();
                Long end = e.getValue();
                if (end != null && now > end) {
                    return true;  // Unlock the IP!
                }
                return false;
            });
        }, 1200L, 1200L);  // Delay first run, then repeat every 1200 ticks (1 min).

        // Clear any old login data from last server restart - no one stays logged in forever!
        loggedInPlayers.clear();
        lastActivity.clear();  // Clear old activity too.
        failedAttempts.clear();  // Wipe old fail counts.
        lockoutEnds.clear();  // End any old lockouts.
        playerLastAttempt.clear();  // NEW! Clear old attempt times.
        ipFailedAttempts.clear();  // NEW! Clear IP fails.
        ipLockoutEnds.clear();  // NEW! Clear IP lockouts.
        ipLastAttempt.clear();  // NEW! Clear IP attempt times.

        getLogger().info("SecureAuth by excrele is online! Premium players auto-login, cracked ones: register to play safe. Chat, movement & building blocked until login. Sessions timeout after 30 min idle. Password changes, login limits (3 tries/5 min lock), IP limits, & attempt resets available!");
    }

    // SECTION 3: Shutdown - Cleans up when the server stops (onDisable).
    @Override
    public void onDisable() {
        if (timeoutTask != null) {
            timeoutTask.cancel();  // Stop the timeout checker when shutting down.
        }
        loggedInPlayers.clear();  // Forget all logins - next join starts fresh.
        lastActivity.clear();  // Forget activity too.
        failedAttempts.clear();  // Forget fail counts.
        lockoutEnds.clear();  // Clear lockouts.
        playerLastAttempt.clear();  // NEW! Forget attempt times.
        ipFailedAttempts.clear();  // NEW! Forget IP fails.
        ipLockoutEnds.clear();  // NEW! Clear IP lockouts.
        ipLastAttempt.clear();  // NEW! Forget IP attempt times.
        getLogger().info("SecureAuth by excrele is offline. Stay secure out there!");
    }

    // SECTION 4: Player Join Event - Fires when someone connects. We check if they're premium or need to login/register.
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();  // Grab their unique ID.
        String ip = ((InetSocketAddress) player.getAddress()).getAddress().getHostAddress();  // NEW! Get their IP like a home address.

        // NEW! If they're (or their IP is) locked out, give a quick heads-up on join.
        long now = System.currentTimeMillis();
        if (lockoutEnds.containsKey(playerId) && now < lockoutEnds.get(playerId)) {
            long remaining = (lockoutEnds.get(playerId) - now) / 1000 / 60;
            player.sendMessage("§c[SecureAuth] You're locked out for " + remaining + " more minutes due to too many wrong logins!");
        }
        if (ipLockoutEnds.containsKey(ip) && now < ipLockoutEnds.get(ip)) {
            long remaining = (ipLockoutEnds.get(ip) - now) / 1000 / 60;
            player.sendMessage("§c[SecureAuth] Your IP is locked out for " + remaining + " more minutes - too many bad tries from here!");
        }

        // Don't freeze the game while checking premium status - do it in the background (async)!
        new BukkitRunnable() {
            @Override
            public void run() {
                checkPremiumAndHandle(player);
            }
        }.runTaskAsynchronously(this);
    }

    // SECTION 5: Player Quit Event - When they leave, forget their login status. Simple cleanup!
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        loggedInPlayers.remove(playerId);  // Bye, login!
        lastActivity.remove(playerId);  // Bye, activity timer too.
        // NEW! No need to clear fails/locks on quit - they persist for next join (fair punishment!). IP stuff stays too.
    }

    // SECTION 6: Chat Blocking Event - This watches when players try to chat. If not logged in, it stops the message and reminds them.
    // Like a bouncer at a club: "No login? No chatting!" Keeps spammers quiet until they secure their account.
    // If they ARE logged in and chatting, update their "last activity" so the timeout doesn't kick in.
    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();  // Who’s trying to chat?
        UUID playerId = player.getUniqueId();  // Get their ID.

        // Check our notebook: Are they logged in? If not (or unknown), block it!
        if (!loggedInPlayers.getOrDefault(playerId, false)) {
            event.setCancelled(true);  // Poof! Message vanishes - no one sees it.
            player.sendMessage("§c[SecureAuth] Hold up! Login first with §b/login <yourpassword> §cto chat and play safely.");
        } else {
            // They're chatting - mark as active! Reset their idle timer.
            lastActivity.put(playerId, System.currentTimeMillis());
        }
        // If logged in? Let the message fly - they're good!
    }

    // SECTION 7: Movement Blocking Event - This watches when players try to move (walk, jump, fly). If not logged in, it freezes them in place like a statue!
    // Imagine being stuck until you say the magic password - no running off to grief before login. Super secure, but fair once you're in.
    // If they ARE moving and logged in, bump their activity time.
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();  // Who's trying to wander?
        UUID playerId = player.getUniqueId();  // Get their ID.

        // Check our notebook: Are they logged in? If not (or unknown), no moving!
        if (!loggedInPlayers.getOrDefault(playerId, false)) {
            Location from = event.getFrom();  // Where they were (safe spot).
            Location to = event.getTo();  // Where they wanna go (naughty!).

            // Only block if it's a real move (not just looking around). Keeps it efficient!
            if (to.getBlockX() != from.getBlockX() || to.getBlockY() != from.getBlockY() || to.getBlockZ() != from.getBlockZ()) {
                event.setTo(from);  // Snap 'em back! Like rubber-banding to the spot.
                player.sendMessage("§c[SecureAuth] Frozen until login! Type §b/login <yourpassword> §cto roam free and stay safe.");
            }
        } else {
            // They're moving - count it as activity! (Only update on real moves to save a tiny bit of server juice.)
            Location from = event.getFrom();
            Location to = event.getTo();
            if (to.getBlockX() != from.getBlockX() || to.getBlockY() != from.getBlockY() || to.getBlockZ() != from.getBlockZ()) {
                lastActivity.put(playerId, System.currentTimeMillis());
            }
        }
        // If logged in? Walk, run, fly - adventure awaits!
    }

    // SECTION 8: Building Blocking Events - These two watch when players try to place or break blocks. If not logged in, it stops the action like a force field!
    // No building griefing before login - place a block? Nope. Punch a tree? Denied. Reminds them to secure their spot first. Two events for place/break, but same logic!
    // If they ARE building and logged in, refresh their activity clock.
    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();  // Who's trying to build?
        UUID playerId = player.getUniqueId();  // Get their ID.

        // Check our notebook: Are they logged in? If not, block the build!
        if (!loggedInPlayers.getOrDefault(playerId, false)) {
            event.setCancelled(true);  // Block stays unplaced - safe!
            player.sendMessage("§c[SecureAuth] Can't build yet! Login with §b/login <yourpassword> §cto create safely.");
        } else {
            // Building? Active status updated!
            lastActivity.put(playerId, System.currentTimeMillis());
        }
        // Logged in? Build away, architect!
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();  // Who's trying to break stuff?
        UUID playerId = player.getUniqueId();  // Get their ID.
        Block block = event.getBlock();  // The block they're eyeing.

        // Check our notebook: Are they logged in? If not, block the break!
        if (!loggedInPlayers.getOrDefault(playerId, false)) {
            event.setCancelled(true);  // Block stays intact - no destruction!
            player.sendMessage("§c[SecureAuth] Can't break yet! Login with §b/login <yourpassword> §cto mine safely.");
        } else {
            // Breaking blocks? You're alive and kicking - update timer!
            lastActivity.put(playerId, System.currentTimeMillis());
        }
        // Logged in? Dig deep, miner!
    }

    // SECTION 9: Premium Checker - The fun part! Calls Mojang's API to see if the player owns a premium account.
    // If yes, auto-login. If no, check if registered and prompt accordingly.
    // When auto-logging in premium players, start their activity clock right away.
    private void checkPremiumAndHandle(Player player) {
        String username = player.getName().toLowerCase();

        // Use a "future" to check without blocking - like ordering food and eating later.
        CompletableFuture<Boolean> premiumCheck = CompletableFuture.supplyAsync(() -> isPremiumUser(username));

        premiumCheck.thenAccept(isPremium -> {
            // Switch back to main thread to chat with the player (Bukkit rules!).
            Bukkit.getScheduler().runTask(this, () -> {
                UUID playerId = player.getUniqueId();
                String ip = ((InetSocketAddress) player.getAddress()).getAddress().getHostAddress();  // NEW! Grab IP for clears.
                if (isPremium) {
                    // Premium? VIP treatment - instant login!
                    loggedInPlayers.put(playerId, true);
                    lastActivity.put(playerId, System.currentTimeMillis());  // Clock starts now!
                    // Premiums don't get attempt limits - clear any old stuff for player & IP.
                    failedAttempts.remove(playerId);
                    lockoutEnds.remove(playerId);
                    playerLastAttempt.remove(playerId);
                    ipFailedAttempts.remove(ip);
                    ipLockoutEnds.remove(ip);
                    ipLastAttempt.remove(ip);
                    player.sendMessage("§a[SecureAuth] Welcome, premium player! You're auto-logged in. Play freely!");
                } else {
                    // Cracked player - do they have a password?
                    if (hasRegisteredPassword(playerId)) {
                        player.sendMessage("§e[SecureAuth] To play, type §b/login <yourpassword> §e- Stay secure! (3 wrong tries = 5 min lockout, resets after 5 min)");
                        player.sendMessage("§cUntil logged in, no chat, movement, or building. We got your back!");
                    } else {
                        player.sendMessage("§e[SecureAuth] Welcome to our cracked server! Create a password first:");
                        player.sendMessage("§b/register <password> <repeat-password> §e- Choose something strong & remember it!");
                        player.sendMessage("§cNo register = no play (stuck & can't build!). Let's secure your account!");
                    }
                    loggedInPlayers.put(playerId, false);  // Mark as not logged in yet.
                }
            });
        });
    }

    // Helper: Real Mojang API check! Premium users get a response with their UUID.
    private boolean isPremiumUser(String username) {
        try {
            URL url = new URL("https://api.mojang.com/users/profiles/minecraft/" + username);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);  // 5 second timeout - don't wait forever.
            conn.setReadTimeout(5000);
            int responseCode = conn.getResponseCode();
            return responseCode == 200;  // 200 means "yes, premium account exists!"
        } catch (Exception e) {
            getLogger().warning("Premium check failed for " + username + ". Assuming cracked for safety.");
            return false;  // Better safe: Treat unknown as cracked.
        }
    }

    // Helper: Check if player has a saved (hashed) password in our file.
    private boolean hasRegisteredPassword(UUID playerId) {
        try {
            String content = new String(Files.readAllBytes(passwordFile.toPath()));
            return content.contains(playerId.toString() + ":");  // Quick search for their line.
        } catch (IOException e) {
            return false;
        }
    }

    // Helper: Update or set a player's hashed password in the file. Like editing a line in a notebook - find the old one and scribble the new!
    // If no line for this player, it adds one at the end (handy for admin resets on newbies).
    private void updatePassword(UUID playerId, String newHashedPass) {
        try {
            String content = new String(Files.readAllBytes(passwordFile.toPath()));  // Read the whole book.
            String[] lines = content.split("\n");  // Split into pages (lines).
            String playerLine = playerId.toString() + ":" + newHashedPass;  // The new line we'll use.
            boolean found = false;

            // Flip through the pages - find the matching player?
            for (int i = 0; i < lines.length; i++) {
                if (lines[i].startsWith(playerId.toString() + ":")) {
                    lines[i] = playerLine;  // Scribble over the old password hash!
                    found = true;
                    break;  // Done searching!
                }
            }

            // If no match, add a new page at the end.
            if (!found) {
                // Append to the array (like adding to the end of the book).
                String[] newLines = new String[lines.length + 1];
                System.arraycopy(lines, 0, newLines, 0, lines.length);
                newLines[lines.length] = playerLine;
                lines = newLines;
            }

            // Glue it all back together and save the book.
            String newContent = String.join("\n", lines) + "\n";
            Files.write(Paths.get(passwordFile.getPath()), newContent.getBytes(), StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);
        } catch (IOException e) {
            getLogger().log(Level.WARNING, "Failed to update password for " + playerId, e);
        }
    }

    // SECTION 10: Command Handler - This runs when /register, /login, /changepass, or /setpass is typed. Checks args, hashes passwords, etc.
    // When login/register/changepass succeeds, kick off the activity timer.
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Some commands are player-only (like register/login/changepass), but setpass works for console too!
        if (command.getName().equalsIgnoreCase("setpass")) {
            // Admin command - can be console or player with perm.
            if (!sender.hasPermission("secureauth.setpass")) {
                sender.sendMessage("§c[SecureAuth] No permission for /setpass! Admins only.");
                return true;
            }
            if (args.length != 2) {
                sender.sendMessage("§c[SecureAuth] Use: /setpass <player-name> <new-password>");
                return true;
            }

            Player targetPlayer = Bukkit.getPlayer(args[0]);  // Find the player by name.
            if (targetPlayer == null) {
                sender.sendMessage("§c[SecureAuth] Player '" + args[0] + "' not online! Can't set password.");
                return true;
            }

            UUID targetId = targetPlayer.getUniqueId();
            String ip = ((InetSocketAddress) targetPlayer.getAddress()).getAddress().getHostAddress();  // NEW! Get target's IP.
            String newPass = args[1];
            if (newPass.length() < 4) {
                sender.sendMessage("§c[SecureAuth] New password too short! Use 4+ characters.");
                return true;
            }

            String newHashed = hashPassword(newPass);
            updatePassword(targetId, newHashed);  // Update or create in file.

            // Log 'em out if online, so they re-login with new pass. Also clear limits for player & IP!
            loggedInPlayers.put(targetId, false);
            lastActivity.remove(targetId);
            failedAttempts.remove(targetId);
            lockoutEnds.remove(targetId);
            playerLastAttempt.remove(targetId);
            ipFailedAttempts.remove(ip);
            ipLockoutEnds.remove(ip);
            ipLastAttempt.remove(ip);

            targetPlayer.sendMessage("§e[SecureAuth] Your password was reset by an admin! Log in with the new one to play.");
            sender.sendMessage("§a[SecureAuth] Set password for " + targetPlayer.getName() + " successfully!");
            return true;
        } else {
            // Player-only commands.
            if (!(sender instanceof Player)) {
                sender.sendMessage("§c[SecureAuth] Only players can register/login/change pass. Console, use /setpass!");
                return true;
            }

            Player player = (Player) sender;
            UUID playerId = player.getUniqueId();
            String ip = ((InetSocketAddress) player.getAddress()).getAddress().getHostAddress();  // NEW! Get player's IP.

            if (command.getName().equalsIgnoreCase("register")) {
                // Register needs exactly 2 args: password and repeat.
                if (args.length != 2) {
                    player.sendMessage("§c[SecureAuth] Use: /register <password> <repeat-password>");
                    return true;
                }

                String pass1 = args[0];
                String pass2 = args[1];

                // Safety checks: Match? Strong enough?
                if (!pass1.equals(pass2)) {
                    player.sendMessage("§c[SecureAuth] Passwords don't match! Double-check and try again.");
                    return true;
                }
                if (pass1.length() < 4) {
                    player.sendMessage("§c[SecureAuth] Password too short! Use 4+ characters for security.");
                    return true;
                }
                if (hasRegisteredPassword(playerId)) {
                    player.sendMessage("§c[SecureAuth] Already registered! Use /login <password> instead.");
                    return true;
                }

                // All good! Hash it (scramble securely) and save.
                String hashedPass = hashPassword(pass1);
                try {
                    String line = playerId.toString() + ":" + hashedPass + "\n";
                    // Pass options separately - like handing multiple tickets at once.
                    Files.write(Paths.get(passwordFile.getPath()), line.getBytes(), StandardOpenOption.APPEND, StandardOpenOption.CREATE);
                    player.sendMessage("§a[SecureAuth] Registered! Now you're auto-logged in. Use /login next time.");
                    loggedInPlayers.put(playerId, true);  // Login right away after register.
                    lastActivity.put(playerId, System.currentTimeMillis());  // Start the activity clock!
                    // NEW! No limits on register - clear any weird old stuff for player & IP.
                    failedAttempts.remove(playerId);
                    lockoutEnds.remove(playerId);
                    playerLastAttempt.remove(playerId);
                    ipFailedAttempts.remove(ip);
                    ipLockoutEnds.remove(ip);
                    ipLastAttempt.remove(ip);
                } catch (IOException e) {
                    player.sendMessage("§c[SecureAuth] Save failed! Try again or ping an admin.");
                    getLogger().log(Level.WARNING, "Couldn't save password for " + player.getName(), e);
                }
                return true;
            }

            if (command.getName().equalsIgnoreCase("login")) {
                // Login needs 1 arg: password.
                if (args.length != 1) {
                    player.sendMessage("§c[SecureAuth] Use: /login <password>");
                    return true;
                }

                String inputPass = args[0];
                if (!hasRegisteredPassword(playerId)) {
                    player.sendMessage("§c[SecureAuth] Not registered? /register first, then login!");
                    return true;
                }

                long now = System.currentTimeMillis();

                // NEW! Check for IP lockout first - like a "whole house" timeout.
                if (ipLockoutEnds.containsKey(ip) && now < ipLockoutEnds.get(ip)) {
                    long remaining = (ipLockoutEnds.get(ip) - now) / (1000 * 60);
                    player.sendMessage("§c[SecureAuth] Your IP is locked out! Wait " + remaining + " more minutes before trying again.");
                    return true;
                }

                // NEW! Then check player lockout.
                if (lockoutEnds.containsKey(playerId) && now < lockoutEnds.get(playerId)) {
                    long remaining = (lockoutEnds.get(playerId) - now) / (1000 * 60);
                    player.sendMessage("§c[SecureAuth] Locked out! Wait " + remaining + " more minutes before trying again.");
                    return true;
                }

                // Get saved hash and check if it matches.
                String savedHash = getSavedHash(playerId);
                if (savedHash != null && checkPassword(inputPass, savedHash)) {
                    loggedInPlayers.put(playerId, true);
                    lastActivity.put(playerId, now);  // Fresh start on login!
                    // NEW! Success! Reset the counters - clean slate for player & IP.
                    failedAttempts.remove(playerId);
                    lockoutEnds.remove(playerId);
                    playerLastAttempt.remove(playerId);
                    ipFailedAttempts.remove(ip);
                    ipLockoutEnds.remove(ip);
                    ipLastAttempt.remove(ip);
                    player.sendMessage("§a[SecureAuth] Login successful! You're secure and ready to adventure. (Session times out after 30 min idle.)");
                } else {
                    // NEW! Wrong pass - tally it up like strikes in baseball, for both player & IP.
                    playerLastAttempt.put(playerId, now);
                    ipLastAttempt.put(ip, now);

                    int playerAttempts = failedAttempts.getOrDefault(playerId, 0) + 1;
                    failedAttempts.put(playerId, playerAttempts);
                    int ipAttempts = ipFailedAttempts.getOrDefault(ip, 0) + 1;
                    ipFailedAttempts.put(ip, ipAttempts);

                    // Check player lock.
                    if (playerAttempts >= MAX_ATTEMPTS) {
                        lockoutEnds.put(playerId, now + LOCKOUT_DURATION);
                        player.sendMessage("§c[SecureAuth] Too many wrong tries (" + MAX_ATTEMPTS + "/" + MAX_ATTEMPTS + ")! Locked for 5 minutes. Take a break!");
                        failedAttempts.remove(playerId);  // Clear count during lockout.
                        playerLastAttempt.remove(playerId);
                    } else {
                        player.sendMessage("§c[SecureAuth] Wrong password! Player attempts: " + playerAttempts + "/" + MAX_ATTEMPTS + ". Careful!");
                    }

                    // Check IP lock - shared punishment!
                    if (ipAttempts >= MAX_ATTEMPTS) {
                        ipLockoutEnds.put(ip, now + LOCKOUT_DURATION);
                        player.sendMessage("§c[SecureAuth] Too many tries from your IP! Locked for 5 minutes - affects everyone here.");
                        ipFailedAttempts.remove(ip);  // Clear IP count during lockout.
                        ipLastAttempt.remove(ip);
                    } else {
                        player.sendMessage("§c[SecureAuth] IP attempts: " + ipAttempts + "/" + MAX_ATTEMPTS + ". Don't spam!");
                    }
                }
                return true;
            }

            if (command.getName().equalsIgnoreCase("changepass")) {
                // Changepass needs 3 args: old pass, new pass, repeat new.
                if (args.length != 3) {
                    player.sendMessage("§c[SecureAuth] Use: /changepass <old-password> <new-password> <repeat-new-password>");
                    return true;
                }

                if (!loggedInPlayers.getOrDefault(playerId, false)) {
                    player.sendMessage("§c[SecureAuth] You must be logged in to change your password! /login first.");
                    return true;
                }

                if (!hasRegisteredPassword(playerId)) {
                    player.sendMessage("§c[SecureAuth] Not registered? /register first, then try changing.");
                    return true;
                }

                String oldPass = args[0];
                String newPass1 = args[1];
                String newPass2 = args[2];

                // Check old password matches (security check!).
                String savedHash = getSavedHash(playerId);
                if (savedHash == null || !checkPassword(oldPass, savedHash)) {
                    player.sendMessage("§c[SecureAuth] Wrong old password! Can't change without it.");
                    return true;
                }

                // New ones match? Strong enough?
                if (!newPass1.equals(newPass2)) {
                    player.sendMessage("§c[SecureAuth] New passwords don't match! Try again.");
                    return true;
                }
                if (newPass1.length() < 4) {
                    player.sendMessage("§c[SecureAuth] New password too short! Use 4+ characters.");
                    return true;
                }

                // All clear! Hash the new one and update.
                String newHashed = hashPassword(newPass1);
                updatePassword(playerId, newHashed);
                player.sendMessage("§a[SecureAuth] Password changed successfully! Stay safe with your new one.");
                lastActivity.put(playerId, System.currentTimeMillis());  // Ping activity too!
                // NEW! Changepass doesn't affect login limits - you're already in!
                return true;
            }
        }

        return false;  // Not our command? Let other plugins handle it.
    }

    // Helper: Fetch the saved hash from the file.
    private String getSavedHash(UUID playerId) {
        try {
            String content = new String(Files.readAllBytes(passwordFile.toPath()));
            String[] lines = content.split("\n");
            for (String line : lines) {
                if (line.startsWith(playerId.toString() + ":")) {
                    return line.split(":", 2)[1].trim();  // Grab just the hash part.
                }
            }
        } catch (IOException e) {
            getLogger().warning("Couldn't read password file for " + playerId);
        }
        return null;
    }

    // SECTION 11: Security Magic - Hashing! Turns plain password into uncrackable code with a random "salt" (extra secret ingredient).
    // We use SHA-256 (strong scramble) + salt. Pro tip: For mega-security, add BCrypt library later.
    private String hashPassword(String password) {
        try {
            // Generate random salt: 16 bytes of chaos, unique per password.
            byte[] salt = new byte[16];
            new SecureRandom().nextBytes(salt);

            // Hash: Salt + password, then SHA-256 it.
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt);
            byte[] hashed = md.digest(password.getBytes("UTF-8"));

            // Combine salt + hash, encode to safe text (Base64).
            byte[] combined = new byte[salt.length + hashed.length];
            System.arraycopy(salt, 0, combined, 0, salt.length);
            System.arraycopy(hashed, 0, combined, salt.length, hashed.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {  // Covers NoSuchAlgorithmException + others.
            getLogger().severe("Hashing broken! Update Java or check errors.");
            return "";  // Fallback - but fix this in production!
        }
    }

    // Helper: Verify input password against saved hash. Re-hashes input with same salt and compares.
    private boolean checkPassword(String input, String saved) {
        try {
            byte[] combined = Base64.getDecoder().decode(saved);
            byte[] salt = new byte[16];
            System.arraycopy(combined, 0, salt, 0, 16);  // Extract salt.

            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt);
            byte[] hashedInput = md.digest(input.getBytes("UTF-8"));

            byte[] savedHash = new byte[combined.length - 16];
            System.arraycopy(combined, 16, savedHash, 0, savedHash.length);

            // Secure compare: No timing leaks for hackers!
            return MessageDigest.isEqual(hashedInput, savedHash);
        } catch (Exception e) {
            return false;
        }
    }
}