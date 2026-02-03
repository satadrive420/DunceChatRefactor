package gg.corn.DunceChat.service;

import gg.corn.DunceChat.model.AltDetectionResult;
import gg.corn.DunceChat.repository.PlayerIPRepository;
import gg.corn.DunceChat.util.MessageManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.logging.Logger;

/**
 * Service for IP tracking, alt detection, and watchlist management
 */
public class IPTrackingService {

    private final PlayerIPRepository playerIPRepository;
    private final PlayerService playerService;
    private final DunceService dunceService;
    private final MessageManager messageManager;
    private final FileConfiguration config;
    private static final Logger logger = Logger.getLogger("DunceChat");
    private static final String ADMIN_PERMISSION = "duncechat.admin";

    // Cached config values
    private boolean ipTrackingEnabled;
    private boolean autoDunceOnIPMatch;
    private boolean notifyAdminsOnAltJoin;
    private int altCheckDepth;
    private boolean whitelistEnabled;
    private Set<String> whitelistedIPs;
    private Set<String> whitelistedSubnets;
    private boolean watchlistEnabled;
    private boolean watchlistNotifyOnJoin;
    private Map<String, String> watchlistedIPs; // IP -> reason

    public IPTrackingService(PlayerIPRepository playerIPRepository, PlayerService playerService,
                            DunceService dunceService, MessageManager messageManager,
                            FileConfiguration config) {
        this.playerIPRepository = playerIPRepository;
        this.playerService = playerService;
        this.dunceService = dunceService;
        this.messageManager = messageManager;
        this.config = config;
        loadConfig();
    }

    /**
     * Load/reload configuration values
     */
    public void loadConfig() {
        this.ipTrackingEnabled = config.getBoolean("ip-tracking.enabled", true);
        this.autoDunceOnIPMatch = config.getBoolean("ip-tracking.auto-dunce-on-ip-match", false);
        this.notifyAdminsOnAltJoin = config.getBoolean("ip-tracking.notify-admins-on-alt-join", true);
        this.altCheckDepth = Math.min(5, Math.max(1, config.getInt("ip-tracking.alt-check-depth", 2)));

        // Load whitelist
        this.whitelistEnabled = config.getBoolean("ip-whitelist.enabled", false);
        this.whitelistedIPs = new HashSet<>();
        this.whitelistedSubnets = new HashSet<>();

        List<String> whitelistAddresses = config.getStringList("ip-whitelist.addresses");
        for (String addr : whitelistAddresses) {
            if (addr.contains("/")) {
                whitelistedSubnets.add(addr);
            } else {
                whitelistedIPs.add(addr);
            }
        }

        // Load watchlist
        this.watchlistEnabled = config.getBoolean("ip-watchlist.enabled", false);
        this.watchlistNotifyOnJoin = config.getBoolean("ip-watchlist.notify-on-join", true);
        this.watchlistedIPs = new HashMap<>();

        ConfigurationSection watchlistSection = config.getConfigurationSection("ip-watchlist.addresses");
        if (watchlistSection != null) {
            for (String key : watchlistSection.getKeys(false)) {
                ConfigurationSection entry = watchlistSection.getConfigurationSection(key);
                if (entry != null) {
                    String ip = entry.getString("ip");
                    String reason = entry.getString("reason", "No reason specified");
                    if (ip != null) {
                        watchlistedIPs.put(ip, reason);
                    }
                }
            }
        }

        // Also support simple list format
        List<?> watchlistList = config.getList("ip-watchlist.addresses");
        if (watchlistList != null) {
            for (Object item : watchlistList) {
                if (item instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> map = (Map<String, Object>) item;
                    String ip = (String) map.get("ip");
                    String reason = (String) map.getOrDefault("reason", "No reason specified");
                    if (ip != null) {
                        watchlistedIPs.put(ip, reason);
                    }
                }
            }
        }

        logger.info("[DunceChat] IP Tracking config loaded - Enabled: " + ipTrackingEnabled +
                   ", Auto-dunce: " + autoDunceOnIPMatch +
                   ", Whitelist: " + whitelistEnabled + " (" + (whitelistedIPs.size() + whitelistedSubnets.size()) + " entries)" +
                   ", Watchlist: " + watchlistEnabled + " (" + watchlistedIPs.size() + " entries)");
    }

    /**
     * Check if an IP is whitelisted
     */
    public boolean isWhitelisted(String ipAddress) {
        if (!whitelistEnabled) {
            return false;
        }

        // Check exact match
        if (whitelistedIPs.contains(ipAddress)) {
            return true;
        }

        // Check CIDR subnets
        for (String subnet : whitelistedSubnets) {
            if (isInSubnet(ipAddress, subnet)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Check if an IP is on the watchlist
     */
    public boolean isWatchlisted(String ipAddress) {
        return watchlistEnabled && watchlistedIPs.containsKey(ipAddress);
    }

    /**
     * Get the watchlist reason for an IP
     */
    public String getWatchlistReason(String ipAddress) {
        return watchlistedIPs.getOrDefault(ipAddress, "Unknown");
    }

    /**
     * Handle player join - performs all IP-related checks
     */
    public void handlePlayerJoin(Player player) {
        if (!ipTrackingEnabled) {
            return;
        }

        String ipAddress = player.getAddress() != null ?
            player.getAddress().getAddress().getHostAddress() : null;

        if (ipAddress == null) {
            return;
        }

        UUID playerUuid = player.getUniqueId();

        // Log the IP
        dunceService.logPlayerIP(playerUuid, ipAddress);

        // Check if IP is whitelisted - skip all checks if so
        if (isWhitelisted(ipAddress)) {
            logger.info("[DunceChat] Player " + player.getName() + " joined from whitelisted IP: " + ipAddress);
            return;
        }

        // Check watchlist
        if (isWatchlisted(ipAddress)) {
            handleWatchlistJoin(player, ipAddress);
        }

        // Check for alt accounts
        checkForAlts(player, ipAddress);
    }

    /**
     * Handle watchlist match on join
     */
    private void handleWatchlistJoin(Player player, String ipAddress) {
        if (!watchlistNotifyOnJoin) {
            return;
        }

        String reason = getWatchlistReason(ipAddress);

        // Build notification using message keys
        Component notification = messageManager.get("watchlist_alert_header", player.getName())
            .append(Component.newline())
            .append(Component.text("  "))
            .append(messageManager.get("watchlist_alert_ip", ipAddress)
                .clickEvent(ClickEvent.copyToClipboard(ipAddress))
                .hoverEvent(HoverEvent.showText(messageManager.get("iplookup_click_copy"))))
            .append(Component.newline())
            .append(Component.text("  "))
            .append(messageManager.get("watchlist_alert_reason", reason));

        notifyAdmins(notification);
        logger.warning("[DunceChat] WATCHLIST: " + player.getName() + " joined from " + ipAddress + " - " + reason);
    }

    /**
     * Check for alt accounts on player join
     * Only alerts admins if a dunced alt is detected (not for every alt)
     */
    private void checkForAlts(Player player, String ipAddress) {
        UUID playerUuid = player.getUniqueId();

        // Skip if player is already dunced - no need to check further
        if (dunceService.isDunced(playerUuid)) {
            return;
        }

        // Get all players sharing this IP
        Set<UUID> playersOnIP = playerIPRepository.getPlayersByIP(ipAddress);
        playersOnIP.remove(playerUuid); // Remove self

        if (playersOnIP.isEmpty()) {
            return;
        }

        // Check if any of them are currently dunced (any dunce type)
        Set<UUID> duncedAlts = new HashSet<>();
        // Check if any of them are specifically IP-dunced
        Set<UUID> ipDuncedAlts = new HashSet<>();

        for (UUID altUuid : playersOnIP) {
            if (dunceService.isDunced(altUuid)) {
                duncedAlts.add(altUuid);
                // Check if this was an IP dunce specifically
                if (dunceService.isIPDunced(altUuid)) {
                    ipDuncedAlts.add(altUuid);
                }
            }
        }

        // Only proceed if there are dunced alts
        if (duncedAlts.isEmpty()) {
            return;
        }

        // Auto-dunce ONLY if there's an IP-dunced alt (not just regular dunced)
        if (autoDunceOnIPMatch && !ipDuncedAlts.isEmpty()) {
            // Get one of the IP-dunced alts to link the reason
            UUID linkedDuncedPlayer = ipDuncedAlts.iterator().next();
            String linkedName = playerService.getNameByUuid(linkedDuncedPlayer).orElse("Unknown");

            String reason = "Auto-dunced: IP match with dunced player " + linkedName;
            // Use silent dunce - the admin notification below serves as the alert
            dunceService.duncePlayerSilent(playerUuid, reason, null, null, null);

            logger.info("[DunceChat] Auto-dunced " + player.getName() + " - IP match with " + linkedName);

            // Notify the player
            player.sendMessage(messageManager.get("auto_dunced_ip_match", linkedName));
        }

        // Notify admins about dunced alt detection (only for dunced alts, not all alts)
        // But only if there are IP-dunced alts (to avoid spam for regular dunces)
        if (notifyAdminsOnAltJoin && !ipDuncedAlts.isEmpty()) {
            sendDuncedAltNotification(player, ipDuncedAlts);
        }
    }

    /**
     * Send dunced alt detection notification to admins
     * Only called when a player joins with a dunced alt on the same IP
     */
    private void sendDuncedAltNotification(Player player, Set<UUID> duncedAlts) {
        // Build list of dunced alt names
        List<String> duncedNames = new ArrayList<>();
        for (UUID uuid : duncedAlts) {
            duncedNames.add(playerService.getNameByUuid(uuid).orElse("Unknown"));
        }
        String duncedNamesStr = String.join(", ", duncedNames);

        // Build notification using message keys
        Component notification = messageManager.get("alt_detected_header", player.getName())
            .clickEvent(ClickEvent.runCommand("/duncealtlookup " + player.getName()))
            .hoverEvent(HoverEvent.showText(messageManager.get("iplookup_click_altlookup")))
            .append(Component.newline())
            .append(Component.text("  "))
            .append(messageManager.get("alt_detected_dunced_accounts", duncedNamesStr))
            .append(Component.newline())
            .append(Component.text("  "))
            .append(Component.newline())
            .append(Component.text("  "))
            .append(autoDunceOnIPMatch
                ? messageManager.get("alt_detected_auto_dunced")
                : messageManager.get("alt_detected_manual_required"));

        notifyAdmins(notification);
    }

    /**
     * Send a notification to all online admins
     */
    private void notifyAdmins(Component message) {
        for (Player admin : Bukkit.getOnlinePlayers()) {
            if (admin.hasPermission(ADMIN_PERMISSION)) {
                admin.sendMessage(message);
            }
        }
    }

    /**
     * Check if an IP is within a CIDR subnet
     */
    private boolean isInSubnet(String ip, String cidr) {
        try {
            String[] parts = cidr.split("/");
            if (parts.length != 2) {
                return false;
            }

            String subnetIp = parts[0];
            int prefixLength = Integer.parseInt(parts[1]);

            long ipLong = ipToLong(ip);
            long subnetLong = ipToLong(subnetIp);
            long mask = ~((1L << (32 - prefixLength)) - 1);

            return (ipLong & mask) == (subnetLong & mask);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Convert IP string to long for subnet calculations
     */
    private long ipToLong(String ip) {
        String[] parts = ip.split("\\.");
        if (parts.length != 4) {
            throw new IllegalArgumentException("Invalid IP: " + ip);
        }

        long result = 0;
        for (int i = 0; i < 4; i++) {
            result = (result << 8) | Integer.parseInt(parts[i]);
        }
        return result;
    }

    /**
     * Get comprehensive alt detection result for a player
     */
    public AltDetectionResult getAltDetectionResult(UUID playerUuid) {
        return dunceService.detectAlts(playerUuid, altCheckDepth);
    }

    // Getters for config values (for external use)
    public boolean isIpTrackingEnabled() {
        return ipTrackingEnabled;
    }

    public boolean isAutoDunceOnIPMatch() {
        return autoDunceOnIPMatch;
    }

    public boolean isNotifyAdminsOnAltJoin() {
        return notifyAdminsOnAltJoin;
    }

    public boolean isWhitelistEnabled() {
        return whitelistEnabled;
    }

    public boolean isWatchlistEnabled() {
        return watchlistEnabled;
    }

    public Set<String> getWhitelistedIPs() {
        return Collections.unmodifiableSet(whitelistedIPs);
    }

    public Map<String, String> getWatchlistedIPs() {
        return Collections.unmodifiableMap(watchlistedIPs);
    }
}

