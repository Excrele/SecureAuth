# SecureAuth - Secure Login for Cracked Minecraft Servers

![SecureAuth Banner](https://img.freepik.com/premium-vector/3d-protection-shield-realistic-icon-concept-vector-image_937058-540.jpg)  
*(A shiny 3D shield - protects your server like a fortress!)*

## 🚀 What is SecureAuth?
Hey, future Minecraft mod master! SecureAuth is a super-smart Spigot plugin I (excrele) built to keep your cracked server safe from sneaky impersonators. On cracked servers, anyone can join with any name - but this plugin fixes that! Premium players (real Mojang accounts) zoom in automatically. Cracked players? They register a super-secure password once, then login quick. No more griefers stealing your spot!

It's like a VIP bouncer: Fast for good guys, locked tight for randos. And it's all in **one Java file** (modular sections for easy tweaks) - no mess!

- **Version**: 1.0
- **Spigot API**: 1.20+ (works on newer too!)
- **Author**: excrele (that's me - hit me up for custom mods!)
- **License**: Free for non-commercial use - credit me if you share!

## ✨ Key Features (Like Superpowers!)
- **Premium Auto-Login**: Mojang API checks - real accounts skip the hassle!
- **Secure Passwords**: Hashed with SHA-256 + salt (gibberish in files - hackers cry!). No plain text, even for staff!
- **Full Lockdown Mode**: Until logged in? No chat, no walking, no building/breaking. Stay safe!
- **Session Timeouts**: AFK too long (30 min)? Auto-logout for security.
- **Password Changes**: /changepass to update your secret (needs old pass first).
- **Admin Resets**: /setpass to force-reset a player's pass (ops only).
- **Login Limits**: 3 wrong tries = 5 min lockout (player + IP!). Resets after 5 min quiet. Stops bots cold!
- **IP Protection**: Bad guesses from one IP lock the whole connection - no alt-account spam!

Pro Tip: All data in `plugins/SecureAuth/passwords.txt` - hashed, so even if stolen, useless!

## 📥 Installation (Easy as Pie!)
1. **Download**: Build the JAR from source (Maven: `mvn clean install`) or grab from [my repo](https://github.com/excrele/SecureAuth) (coming soon!). File: `SecureAuth-1.0.jar`.
2. **Drop It In**: Put the JAR in your server's `plugins/` folder.
3. **Restart**: Fire up the server - watch console for "SecureAuth by excrele is online!"
4. **Test**: Join with a premium name (like "Notch") - auto-welcome! Cracked name? Register first!

**Requirements**:
- Spigot/Paper 1.20+ (cracked mode: `online-mode=false` in server.properties).
- Java 21+ (for fancy hashing).

**No Config?** Yup! All tunable in code (e.g., timeouts in SecureAuth.java). Edit & rebuild for your vibe.

## 🎮 Commands & Permissions (Your Magic Words)
All commands work in-game. Permissions default friendly - tweak in LuckPerms or whatever!

| Command | Description | Usage | Permission (Default) |
|---------|-------------|-------|----------------------|
| `/register <pass> <repeat>` | Create your secure password (first time only!). | `/register mypass mypass` | `secureauth.register` (true) |
| `/login <pass>` | Log in with your saved password. | `/login mypass` | `secureauth.login` (true) |
| `/changepass <old> <new> <repeat>` | Update to a fresh password (must be logged in!). | `/changepass myold newpass newpass` | `secureauth.changepass` (true) |
| `/setpass <player> <newpass>` | Admin: Reset a player's password (logs 'em out). | `/setpass Steve newpass` | `secureauth.setpass` (op) |

**Bypass Perk**: `secureauth.bypass` (op) - Skips all checks for trusted staff. Use wisely!

## ⚙️ Customization (Tweak Like a Pro!)
Open `SecureAuth.java` - it's one file, sectioned like a comic book!
- **Timeouts**: Change `SESSION_TIMEOUT` (30 min), `LOCKOUT_DURATION` (5 min), `RESET_DURATION` (5 min for attempt forgiveness).
- **Limits**: `MAX_ATTEMPTS = 3;` - Bump to 5 for chill servers.
- **Messages**: Edit the `player.sendMessage()` lines - add colors or emojis!
- **More Blocks?** Add events like `PlayerInteractEvent` for doors/chests.

Rebuild with Maven, restart - done! (Teen tip: IntelliJ makes this a breeze.)

## 🛠️ Troubleshooting (Fix It Fast!)
- **"Premium check failed"**: Internet hiccup? Mojang API down - treats as cracked (safe!).
- **Passwords visible?** Nope! Check `passwords.txt` - all hashes like `uuid:abc123def...` (unreadable!).
- **Not blocking chat/move?** Ensure `online-mode=false`. Reload plugin? `/reload confirm` (but restart better).
- **Errors in console?** Paste 'em - I'm here to debug! (Common: Java version - need 21+.)
- **Too strict?** Comment out event handlers (e.g., `@EventHandler public void onPlayerMove...`) for testing.

**Logs**: Check `latest.log` for `[SecureAuth]` warnings. All async - no lag!

## ❤️ Support & Credits
Love it? Star/fork on GitHub! Questions? DM @excrele on Discord or forums. Custom features? Paid gigs welcome - let's build your dream server!

- **Built with**: Spigot 1.20, Java 21.
- **Inspired by**: AuthMe (but simpler & teen-friendly!).
- **Shoutout**: To young coders reading this - tweak it, learn, share! Coding's your superpower.

**Stay Secure, Mine On!** 🚀  
*excrele - Professional Spigot Dev*  
[GitHub](https://github.com/excrele) | [SpigotMC](https://www.spigotmc.org/resources/secureauth.12345/) *(Link soon!)*