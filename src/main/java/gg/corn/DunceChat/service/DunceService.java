package gg.corn.DunceChat.service;

import gg.corn.DunceChat.model.DunceRecord;
import gg.corn.DunceChat.repository.DunceRepository;
import gg.corn.DunceChat.repository.PendingMessageRepository;
import gg.corn.DunceChat.util.MessageManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Logger;

/**
 * Service for dunce-related business logic
 */
public class DunceService {

    private final DunceRepository dunceRepository;
    private final PendingMessageRepository pendingMessageRepository;
    private final PlayerService playerService;
    private final PreferencesService preferencesService;
    private final MessageManager messageManager;
    private static final SimpleDateFormat DATE_FORMATTER = new SimpleDateFormat("yyyy/MM/dd HH:mm");
    private static final Logger logger = Logger.getLogger("DunceChat");

    public DunceService(DunceRepository dunceRepository, PendingMessageRepository pendingMessageRepository,
                       PlayerService playerService, PreferencesService preferencesService,
                       MessageManager messageManager) {
        this.dunceRepository = dunceRepository;
        this.pendingMessageRepository = pendingMessageRepository;
        this.playerService = playerService;
        this.preferencesService = preferencesService;
        this.messageManager = messageManager;
    }

    /**
     * Check if a player is dunced
     */
    public boolean isDunced(UUID playerUuid) {
        return dunceRepository.getActiveDunceRecord(playerUuid)
                .map(DunceRecord::isDunced)
                .orElse(false);
    }

    /**
     * Get active dunce record for a player
     */
    public Optional<DunceRecord> getActiveDunceRecord(UUID playerUuid) {
        return dunceRepository.getActiveDunceRecord(playerUuid);
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
        // Check if already dunced
        Optional<DunceRecord> existingRecord = dunceRepository.getActiveDunceRecord(playerUuid);
        if (existingRecord.isPresent() && existingRecord.get().isDunced()) {
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

        // Set default preferences
        preferencesService.setDunceChatVisible(playerUuid, true);
        preferencesService.setInDunceChat(playerUuid, true);

        // Broadcast dunce message
        broadcastDunceMessage(playerUuid, reason, staffUuid, expiresAt);
    }

    /**
     * Undunce a player
     * @param playerUuid The player to undunce
     * @param staffUuid The staff member who undunced (null for auto-expiry)
     * @param isExpiry True if this is from auto-expiry, false if staff action
     */
    public void unduncePlayer(UUID playerUuid, UUID staffUuid, boolean isExpiry) {
        Optional<DunceRecord> record = dunceRepository.getActiveDunceRecord(playerUuid);
        if (record.isEmpty() || !record.get().isDunced()) {
            return; // Not dunced
        }

        dunceRepository.undunce(playerUuid);
        preferencesService.setInDunceChat(playerUuid, false);

        if (isExpiry) {
            // Send expiry message to player (or store for later if offline)
            sendDunceExpiryMessage(playerUuid);
        } else {
            // Broadcast undunce message for staff actions
            broadcastUndunceMessage(playerUuid, staffUuid);
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
        if (isDunced) {
            // Dunced player message - uses messages.properties format with Component placeholders
            formattedMessage = messageManager.getWithComponents("dunce_chat_format", placeholders);
        } else if (inDunceChat || canSeeDunceChat) {
            // Staff/observer message - uses messages.properties format with Component placeholders
            formattedMessage = messageManager.getWithComponents("dunce_chat_observer_format", placeholders);
        } else {
            // Should not happen due to command checks, but handle gracefully
            sender.sendMessage(messageManager.get("no_permission"));
            return;
        }

        // Send to all players who can see dunce chat
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (isDunced(online.getUniqueId()) ||
                preferencesService.isInDunceChat(online.getUniqueId()) ||
                preferencesService.isDunceChatVisible(online.getUniqueId())) {
                online.sendMessage(formattedMessage);
            }
        }
    }
}
