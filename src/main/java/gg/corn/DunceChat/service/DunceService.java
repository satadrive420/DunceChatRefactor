package gg.corn.DunceChat.service;

import gg.corn.DunceChat.model.AltDetectionResult;
import gg.corn.DunceChat.model.DunceRecord;
import gg.corn.DunceChat.repository.DunceRepository;
import gg.corn.DunceChat.repository.PendingMessageRepository;
import gg.corn.DunceChat.repository.PlayerIPRepository;
import gg.corn.DunceChat.util.MessageManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Service for dunce-related business logic
 * Uses in-memory caching to minimize database queries for frequently-accessed data
 */
public class DunceService {

    private final DunceRepository dunceRepository;
    private final PendingMessageRepository pendingMessageRepository;
    private final PlayerIPRepository playerIPRepository;
    private final PlayerService playerService;
    private final PreferencesService preferencesService;
    private final MessageManager messageManager;
    private static final SimpleDateFormat DATE_FORMATTER = new SimpleDateFormat("yyyy/MM/dd HH:mm");
    private static final Logger logger = Logger.getLogger("DunceChat");

    // In-memory cache for dunced players - key: player UUID, value: DunceRecord (null if not dunced)
    private final Map<UUID, Optional<DunceRecord>> dunceCache = new ConcurrentHashMap<>();

    public DunceService(DunceRepository dunceRepository, PendingMessageRepository pendingMessageRepository,
                       PlayerIPRepository playerIPRepository, PlayerService playerService,
                       PreferencesService preferencesService, MessageManager messageManager) {
        this.dunceRepository = dunceRepository;
        this.pendingMessageRepository = pendingMessageRepository;
        this.playerIPRepository = playerIPRepository;
        this.playerService = playerService;
        this.preferencesService = preferencesService;
        this.messageManager = messageManager;
    }

    /**
     * Initialize cache on plugin enable - loads all active dunces into memory
     */
    public void initializeCache() {
        dunceCache.clear();
        Set<UUID> duncedPlayers = dunceRepository.getAllActiveDuncedPlayers();
        for (UUID uuid : duncedPlayers) {
            Optional<DunceRecord> record = dunceRepository.getActiveDunceRecord(uuid);
            dunceCache.put(uuid, record);
        }
        logger.info("[DunceChat] Loaded " + duncedPlayers.size() + " dunced players into cache");
    }

    /**
     * Check if a player is dunced (uses cache)
     */
    public boolean isDunced(UUID playerUuid) {
        return getCachedDunceRecord(playerUuid)
                .map(DunceRecord::isDunced)
                .orElse(false);
    }

    /**
     * Check if a player was IP-dunced (dunced via IP dunce command, not regular dunce)
     * This is determined by checking if the reason contains "IP Dunce" or "IP Link" or "IP:"
     */
    public boolean isIPDunced(UUID playerUuid) {
        return getCachedDunceRecord(playerUuid)
                .filter(DunceRecord::isDunced)
                .map(record -> {
                    String reason = record.getReason();
                    if (reason == null) return false;
                    return reason.contains("IP Dunce") ||
                           reason.contains("IP Link") ||
                           reason.contains("(IP:") ||
                           reason.contains("IP match");
                })
                .orElse(false);
    }

    /**
     * Get active dunce record for a player (uses cache)
     */
    public Optional<DunceRecord> getActiveDunceRecord(UUID playerUuid) {
        return getCachedDunceRecord(playerUuid);
    }

    /**
     * Get cached dunce record, loading from DB if not in cache
     */
    private Optional<DunceRecord> getCachedDunceRecord(UUID playerUuid) {
        return dunceCache.computeIfAbsent(playerUuid, dunceRepository::getActiveDunceRecord);
    }

    /**
     * Invalidate cache for a specific player
     */
    public void invalidateCache(UUID playerUuid) {
        dunceCache.remove(playerUuid);
    }

    /**
     * Clear entire cache (useful for reload)
     */
    public void clearCache() {
        dunceCache.clear();
    }

    /**
     * Get all currently dunced player UUIDs
     */
    public Set<UUID> getAllDuncedPlayers() {
        return dunceRepository.getAllActiveDuncedPlayers();
    }

    /**
     * Dunce a player
     */
    public void duncePlayer(UUID playerUuid, String reason, UUID staffUuid, Timestamp expiresAt, String triggerMessage) {
        duncePlayerInternal(playerUuid, reason, staffUuid, expiresAt, triggerMessage, true);
    }

    /**
     * Dunce a player silently (no broadcast) - used for IP-linked dunces
     */
    public void duncePlayerSilent(UUID playerUuid, String reason, UUID staffUuid, Timestamp expiresAt, String triggerMessage) {
        duncePlayerInternal(playerUuid, reason, staffUuid, expiresAt, triggerMessage, false);
    }

    /**
     * Internal dunce implementation
     */
    private void duncePlayerInternal(UUID playerUuid, String reason, UUID staffUuid, Timestamp expiresAt, String triggerMessage, boolean broadcast) {
        // Check if already dunced (from cache)
        if (isDunced(playerUuid)) {
            return; // Already dunced
        }

        // Create new dunce record
        DunceRecord record = new DunceRecord(playerUuid);
        record.setDunced(true);
        record.setReason(reason);
        record.setStaffUuid(staffUuid);
        record.setDuncedAt(new Timestamp(System.currentTimeMillis()));
        record.setExpiresAt(expiresAt);
        record.setTriggerMessage(triggerMessage);

        dunceRepository.create(record);

        // Update cache with the new record
        dunceCache.put(playerUuid, Optional.of(record));

        // Set default preferences
        preferencesService.setDunceChatVisible(playerUuid, true);
        preferencesService.setInDunceChat(playerUuid, true);

        // Broadcast dunce message only if requested
        if (broadcast) {
            broadcastDunceMessage(playerUuid, reason, staffUuid, expiresAt);
        }
    }

    /**
     * Undunce a player
     * @param playerUuid The player to undunce
     * @param staffUuid The staff member who undunced (null for auto-expiry)
     * @param isExpiry True if this is from auto-expiry, false if staff action
     */
    public void unduncePlayer(UUID playerUuid, UUID staffUuid, boolean isExpiry) {
        unduncePlayerInternal(playerUuid, staffUuid, isExpiry, true);
    }

    /**
     * Undunce a player silently (no broadcast) - used for IP-linked undunces
     */
    public void unduncePlayerSilent(UUID playerUuid, UUID staffUuid) {
        unduncePlayerInternal(playerUuid, staffUuid, false, false);
    }

    /**
     * Internal undunce implementation
     */
    private void unduncePlayerInternal(UUID playerUuid, UUID staffUuid, boolean isExpiry, boolean broadcast) {
        if (!isDunced(playerUuid)) {
            return; // Not dunced
        }

        // Get the player name before unduncing (needed for finding linked dunces)
        String playerName = playerService.getNameByUuid(playerUuid).orElse("Unknown");

        dunceRepository.undunce(playerUuid);

        // Update cache - remove the record
        dunceCache.put(playerUuid, Optional.empty());

        preferencesService.setInDunceChat(playerUuid, false);

        if (isExpiry) {
            // Send expiry message to player (or store for later if offline)
            sendDunceExpiryMessage(playerUuid);
        } else if (broadcast) {
            // Broadcast undunce message for staff actions (only if not silent)
            broadcastUndunceMessage(playerUuid, staffUuid);
        }

        // Check for and undunce any IP-linked dunces created from this player
        undunceIPLinkedAccounts(playerUuid, playerName, staffUuid, isExpiry);
    }

    /**
     * Find and undunce any accounts that were IP-linked to the specified player
     * This is called when a player is undunced (either manually or via expiry)
     */
    private void undunceIPLinkedAccounts(UUID originPlayerUuid, String originPlayerName, UUID staffUuid, boolean isExpiry) {
        // Get all currently dunced players
        Set<UUID> duncedPlayers = getAllDuncedPlayers();

        // Get the current IP of the original player to check for IP-based dunces
        Optional<String> originPlayerIP = playerIPRepository.getCurrentIP(originPlayerUuid);

        for (UUID duncedUuid : duncedPlayers) {
            if (duncedUuid.equals(originPlayerUuid)) {
                continue; // Skip the original player
            }

            // Check if this player was IP-linked to the original player
            Optional<DunceRecord> record = getActiveDunceRecord(duncedUuid);
            if (record.isPresent()) {
                String reason = record.get().getReason();
                if (reason == null) {
                    continue;
                }

                boolean shouldUndunce = false;

                // Check if dunced via "IP Link: PlayerName"
                if (reason.contains("(IP Link: " + originPlayerName + ")")) {
                    shouldUndunce = true;
                }
                // Check if dunced via "IP: address" and shares the same IP
                else if (originPlayerIP.isPresent() && reason.contains("(IP: " + originPlayerIP.get() + ")")) {
                    shouldUndunce = true;
                }

                if (shouldUndunce) {
                    // This player was IP-linked to the original player, undunce them
                    if (isExpiry) {
                        // If original expired, this should also expire (send expiry message)
                        unduncePlayerInternal(duncedUuid, null, true, false);
                    } else {
                        // If manually undunced, undunce silently
                        unduncePlayerSilent(duncedUuid, staffUuid);
                    }
                    String linkedName = playerService.getNameByUuid(duncedUuid).orElse("Unknown");
                    logger.info("[DunceChat] Auto-undunced IP-linked account: " + linkedName + " (linked to " + originPlayerName + ")");
                }
            }
        }
    }

    /**
     * Process expired dunce records
     */
    public void processExpiredDunces() {
        List<DunceRecord> expiredRecords = dunceRepository.getExpiredDunceRecords();

        for (DunceRecord record : expiredRecords) {
            unduncePlayer(record.getPlayerUuid(), null, true);  // true = expired

            String playerName = playerService.getNameByUuid(record.getPlayerUuid())
                    .orElse("Unknown");
            logger.info("[DunceChat] Auto-undunced " + playerName + " (expired)");
        }
    }

    /**
     * Check if a specific player's dunce has expired and process it on login
     * This handles the edge case where a player logs in before the scheduled expiry checker runs
     * @param playerUuid The player who just logged in
     */
    public void checkAndProcessExpiredDunceOnLogin(UUID playerUuid) {
        Optional<DunceRecord> record = getActiveDunceRecord(playerUuid);

        if (record.isPresent() && record.get().isExpired()) {
            // Player's dunce has expired but hasn't been processed yet
            unduncePlayer(playerUuid, null, true);  // true = expired

            String playerName = playerService.getNameByUuid(playerUuid).orElse("Unknown");
            logger.info("[DunceChat] Auto-undunced " + playerName + " on login (expired)");
        }
    }

    /**
     * Broadcast dunce message to all online players
     */
    private void broadcastDunceMessage(UUID playerUuid, String reason, UUID staffUuid, Timestamp expiresAt) {
        // Try to get the online player for PlaceholderAPI support
        Player onlinePlayer = Bukkit.getPlayer(playerUuid);
        String playerName = onlinePlayer != null
            ? playerService.getDisplayName(onlinePlayer)
            : playerService.getNameByUuid(playerUuid).orElse("Unknown");

        Player onlineStaff = staffUuid != null ? Bukkit.getPlayer(staffUuid) : null;
        String staffName = onlineStaff != null
            ? playerService.getDisplayName(onlineStaff)
            : (staffUuid != null
                ? playerService.getNameByUuid(staffUuid).orElse("CONSOLE")
                : "CONSOLE");

        String expiryText = expiresAt == null ? messageManager.getRaw("dunce_expires_never") : DATE_FORMATTER.format(expiresAt);
        String reasonText = (reason != null && !reason.isEmpty()) ? messageManager.getRaw("dunced_reason", reason) : "";

        for (Player online : Bukkit.getOnlinePlayers()) {
            Component message;

            if (online.getUniqueId().equals(playerUuid)) {
                // Message to the dunced player
                message = messageManager.get("dunced_self", staffName, reasonText, expiryText);
            } else {
                // Message to other players
                message = messageManager.get("dunced_broadcast", playerName, staffName, reasonText);
            }

            online.sendMessage(message);
        }
    }

    /**
     * Broadcast undunce message to all online players
     */
    private void broadcastUndunceMessage(UUID playerUuid, UUID staffUuid) {
        // Try to get the online player for PlaceholderAPI support
        Player onlinePlayer = Bukkit.getPlayer(playerUuid);
        String playerName = onlinePlayer != null
            ? playerService.getDisplayName(onlinePlayer)
            : playerService.getNameByUuid(playerUuid).orElse("Unknown");

        Player onlineStaff = staffUuid != null ? Bukkit.getPlayer(staffUuid) : null;
        String staffName = onlineStaff != null
            ? playerService.getDisplayName(onlineStaff)
            : (staffUuid != null
                ? playerService.getNameByUuid(staffUuid).orElse("CONSOLE")
                : "CONSOLE");

        Component message = messageManager.get("undunced_broadcast", staffName, playerName);

        for (Player online : Bukkit.getOnlinePlayers()) {
            online.sendMessage(message);
        }
    }

    /**
     * Send dunce expiry message to player (or store for later if offline)
     */
    private void sendDunceExpiryMessage(UUID playerUuid) {
        Player player = Bukkit.getPlayer(playerUuid);

        if (player != null && player.isOnline()) {
            // Player is online, send message directly
            player.sendMessage(messageManager.get("dunce_expired"));
            logger.info("[DunceChat] Sent dunce expiry message to online player: " + player.getName());
        } else {
            // Player is offline, store message for later
            pendingMessageRepository.addPendingMessage(playerUuid, "dunce_expired");
            String playerName = playerService.getNameByUuid(playerUuid).orElse("Unknown");
            logger.info("[DunceChat] Stored dunce expiry message for offline player: " + playerName);
        }
    }

    /**
     * Send all pending messages to a player on login
     */
    public void sendPendingMessages(UUID playerUuid) {
        List<String> messageKeys = pendingMessageRepository.getPendingMessages(playerUuid);

        if (messageKeys.isEmpty()) {
            return;
        }

        Player player = Bukkit.getPlayer(playerUuid);
        if (player == null || !player.isOnline()) {
            return;
        }

        for (String messageKey : messageKeys) {
            Component message = messageManager.get(messageKey);
            player.sendMessage(message);
        }

        // Delete the messages after sending
        pendingMessageRepository.deletePendingMessages(playerUuid);

        logger.info("[DunceChat] Sent " + messageKeys.size() + " pending message(s) to " + player.getName());
    }

    /**
     * Get dunce history for a player
     */
    public List<DunceRecord> getDunceHistory(UUID playerUuid) {
        return dunceRepository.getDunceHistory(playerUuid);
    }

    /**
     * Send a message in Dunce Chat via command
     * This is used by the /duncechat (/dc) command
     */
    public void sendDunceChatMessage(Player sender, String message) {
        boolean isDunced = isDunced(sender.getUniqueId());
        boolean inDunceChat = preferencesService.isInDunceChat(sender.getUniqueId());
        boolean canSeeDunceChat = preferencesService.isDunceChatVisible(sender.getUniqueId());

        // Get display name Components using PlaceholderAPI if configured (preserves colors)
        Component displayNameComponent = playerService.getDisplayNameComponent(sender);
        Component prefixComponent = playerService.getPrefixComponent(sender);

        // Combine prefix and name into a single component
        Component fullNameComponent = prefixComponent.append(displayNameComponent);

        // Create placeholder map for MiniMessage
        Map<String, Component> placeholders = new HashMap<>();
        placeholders.put("player", fullNameComponent);
        placeholders.put("message", Component.text(message));

        // Determine message format based on sender status
        Component formattedMessage;
        String logPrefix;
        if (isDunced) {
            // Dunced player message - uses messages.properties format with Component placeholders
            formattedMessage = messageManager.getWithComponents("dunce_chat_format", placeholders);
            logPrefix = "[Dunced]";
        } else if (inDunceChat || canSeeDunceChat) {
            // Staff/observer message - uses messages.properties format with Component placeholders
            formattedMessage = messageManager.getWithComponents("dunce_chat_observer_format", placeholders);
            logPrefix = "[Observer]";
        } else {
            // Should not happen due to command checks, but handle gracefully
            sender.sendMessage(messageManager.get("no_permission"));
            return;
        }

        // Log the dunce chat message
        logger.info("[DunceChat] " + logPrefix + " " + sender.getName() + ": " + message);

        // Send to all players who can see dunce chat
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (isDunced(online.getUniqueId()) ||
                preferencesService.isInDunceChat(online.getUniqueId()) ||
                preferencesService.isDunceChatVisible(online.getUniqueId())) {
                online.sendMessage(formattedMessage);
            }
        }
    }

    /**
     * Dunce a player and all accounts sharing their current IP address
     * Only broadcasts for the primary target, linked accounts are dunced silently
     */
    public void ipDuncePlayer(UUID targetUuid, String reason, UUID staffUuid, Timestamp expiresAt) {
        // Dunce the primary target (with broadcast)
        duncePlayer(targetUuid, reason , staffUuid, expiresAt, null);

        // Get all players sharing current IP with target
        Set<UUID> linkedPlayers = playerIPRepository.getPlayersWithCurrentIP(targetUuid);

        // Dunce all linked players silently (no broadcast)
        for (UUID linkedUuid : linkedPlayers) {
            if (!isDunced(linkedUuid)) {
                String linkedName = playerService.getNameByUuid(linkedUuid).orElse("Unknown");
                String linkedReason = reason + " (IP Link: " + playerService.getNameByUuid(targetUuid).orElse("Unknown") + ")";
                duncePlayerSilent(linkedUuid, linkedReason, staffUuid, expiresAt, null);
                logger.info("[DunceChat] IP-dunced " + linkedName + " (linked to " + playerService.getNameByUuid(targetUuid).orElse("Unknown") + ")");
            }
        }
    }

    /**
     * Dunce by IP address - dunces all accounts associated with the IP
     * First player is broadcast, rest are silent
     */
    public void ipDunceByAddress(String ipAddress, String reason, UUID staffUuid, Timestamp expiresAt) {
        Set<UUID> players = playerIPRepository.getPlayersByIP(ipAddress);

        if (players.isEmpty()) {
            return;
        }

        boolean firstPlayer = true;
        for (UUID playerUuid : players) {
            if (!isDunced(playerUuid)) {
                String linkedReason = reason + " (IP: " + ipAddress + ")";
                String playerName = playerService.getNameByUuid(playerUuid).orElse("Unknown");

                if (firstPlayer) {
                    // Broadcast for the first player only
                    duncePlayer(playerUuid, linkedReason, staffUuid, expiresAt, null);
                    firstPlayer = false;
                } else {
                    // Silent for subsequent players
                    duncePlayerSilent(playerUuid, linkedReason, staffUuid, expiresAt, null);
                }
                logger.info("[DunceChat] IP-dunced " + playerName + " (IP: " + ipAddress + ")");
            }
        }
    }

    /**
     * Undunce a player and all accounts sharing their current IP address
     * Only broadcasts for the primary target, linked accounts are undunced silently
     */
    public void ipUnduncePlayer(UUID targetUuid, UUID staffUuid) {
        // Undunce the primary target (with broadcast)
        unduncePlayer(targetUuid, staffUuid, false);

        // Get all players sharing current IP with target
        Set<UUID> linkedPlayers = playerIPRepository.getPlayersWithCurrentIP(targetUuid);

        // Undunce all linked players silently (no broadcast)
        for (UUID linkedUuid : linkedPlayers) {
            if (isDunced(linkedUuid)) {
                unduncePlayerSilent(linkedUuid, staffUuid);
                String linkedName = playerService.getNameByUuid(linkedUuid).orElse("Unknown");
                logger.info("[DunceChat] IP-undunced " + linkedName + " (linked to " + playerService.getNameByUuid(targetUuid).orElse("Unknown") + ")");
            }
        }
    }

    /**
     * Undunce by IP address - undunces all accounts associated with the IP
     * First player is broadcast, rest are silent
     */
    public void ipUndunceByAddress(String ipAddress, UUID staffUuid) {
        Set<UUID> players = playerIPRepository.getPlayersByIP(ipAddress);

        if (players.isEmpty()) {
            return;
        }

        boolean firstPlayer = true;
        for (UUID playerUuid : players) {
            if (isDunced(playerUuid)) {
                String playerName = playerService.getNameByUuid(playerUuid).orElse("Unknown");

                if (firstPlayer) {
                    // Broadcast for the first player only
                    unduncePlayer(playerUuid, staffUuid, false);
                    firstPlayer = false;
                } else {
                    // Silent for subsequent players
                    unduncePlayerSilent(playerUuid, staffUuid);
                }
                logger.info("[DunceChat] IP-undunced " + playerName + " (IP: " + ipAddress + ")");
            }
        }
    }

    /**
     * Get historical IP links for a player (for info display)
     */
    public Set<UUID> getHistoricalIPLinks(UUID playerUuid) {
        return playerIPRepository.getPlayersWithHistoricalIP(playerUuid);
    }

    /**
     * Get current IP links for a player (for info display)
     */
    public Set<UUID> getCurrentIPLinks(UUID playerUuid) {
        return playerIPRepository.getPlayersWithCurrentIP(playerUuid);
    }

    /**
     * Log a player's IP address (called on join)
     */
    public void logPlayerIP(UUID playerUuid, String ipAddress) {
        playerIPRepository.logPlayerIP(playerUuid, ipAddress);
    }

    /**
     * Perform comprehensive alt detection for a player
     * @param playerUuid The player to check
     * @param maxDepth How many levels deep to search (1 = direct links only, 2+ = chains)
     * @return AltDetectionResult containing all detected alts and their relationships
     */
    public AltDetectionResult detectAlts(UUID playerUuid, int maxDepth) {
        String playerName = playerService.getNameByUuid(playerUuid).orElse("Unknown");
        AltDetectionResult result = new AltDetectionResult(playerUuid, playerName);

        // Get current IP for the target player
        Optional<String> currentIP = playerIPRepository.getCurrentIP(playerUuid);

        // Get all connected players through shared IPs
        Map<UUID, Set<String>> connectedPlayers = playerIPRepository.findAllConnectedPlayers(playerUuid, maxDepth);

        // Get all IPs for target player to determine current vs historical
        List<String> targetPlayerIPs = playerIPRepository.getIPsByPlayer(playerUuid);
        for (String ip : targetPlayerIPs) {
            result.addPlayerIP(playerUuid, ip);
        }

        // Process each connected player
        for (Map.Entry<UUID, Set<String>> entry : connectedPlayers.entrySet()) {
            UUID altUuid = entry.getKey();
            Set<String> sharedIPs = entry.getValue();

            String altName = playerService.getNameByUuid(altUuid).orElse("Unknown");
            Optional<java.sql.Timestamp> lastSeen = playerIPRepository.getLastSeenTimestamp(altUuid);

            // Check if they share the current IP
            boolean isCurrentIPMatch = currentIP.isPresent() && sharedIPs.contains(currentIP.get());

            AltDetectionResult.AltAccount alt = new AltDetectionResult.AltAccount(
                altUuid,
                altName,
                sharedIPs,
                lastSeen.orElse(null),
                isCurrentIPMatch
            );

            // Categorize as direct (current IP) or historical
            if (isCurrentIPMatch) {
                result.addDirectAlt(alt);
            } else {
                result.addHistoricalAlt(alt);
            }

            // Build the IP mappings
            for (String ip : sharedIPs) {
                result.addIpMapping(ip, altUuid);
                result.addPlayerIP(altUuid, ip);
            }
        }

        return result;
    }

    /**
     * Get all IPs for a player
     */
    public List<String> getPlayerIPs(UUID playerUuid) {
        return playerIPRepository.getIPsByPlayer(playerUuid);
    }

    /**
     * Get detailed IP records for a player
     */
    public List<PlayerIPRepository.IPRecord> getDetailedPlayerIPs(UUID playerUuid) {
        return playerIPRepository.getDetailedIPsByPlayer(playerUuid);
    }

    /**
     * Get the current IP for a player
     */
    public Optional<String> getPlayerCurrentIP(UUID playerUuid) {
        return playerIPRepository.getCurrentIP(playerUuid);
    }

    /**
     * Get shared IPs between two players
     */
    public Set<String> getSharedIPs(UUID player1, UUID player2) {
        return playerIPRepository.getSharedIPs(player1, player2);
    }

    /**
     * Unlink a player from IP tracking by deleting all their IP history
     * This removes all associations to other players through IP addresses
     * @param playerUuid The player to unlink
     * @return The number of IP records deleted
     */
    public int unlinkPlayerFromIPTracking(UUID playerUuid) {
        int deletedCount = playerIPRepository.deletePlayerIPHistory(playerUuid);
        String playerName = playerService.getNameByUuid(playerUuid).orElse("Unknown");
        logger.info("[DunceChat] Unlinked " + playerName + " from IP tracking (" + deletedCount + " IP records deleted)");
        return deletedCount;
    }
}

