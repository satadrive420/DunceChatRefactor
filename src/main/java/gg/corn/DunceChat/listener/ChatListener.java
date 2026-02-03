package gg.corn.DunceChat.listener;

import gg.corn.DunceChat.service.DunceService;
import gg.corn.DunceChat.service.IPTrackingService;
import gg.corn.DunceChat.service.PlayerService;
import gg.corn.DunceChat.service.PreferencesService;
import gg.corn.DunceChat.util.MessageManager;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Refactored chat event listener using services
 * Optimized for performance with pre-compiled regex and minimal allocations
 */
public class ChatListener implements Listener {

    private final DunceService dunceService;
    private final PlayerService playerService;
    private final PreferencesService preferencesService;
    private final IPTrackingService ipTrackingService;
    private final MessageManager messageManager;
    private static final Logger logger = Logger.getLogger("DunceChat");

    // Pre-compiled regex pattern for word filtering (much faster than loop + contains)
    private final Pattern disallowedWordsPattern;

    // Pre-compiled whisper command set (avoid creating new list on every command)
    private static final Set<String> WHISPER_COMMANDS = Set.of("whisper", "w", "msg", "r", "reply");

    public ChatListener(DunceService dunceService, PlayerService playerService,
                       PreferencesService preferencesService, IPTrackingService ipTrackingService,
                       MessageManager messageManager, List<String> disallowedWords) {
        this.dunceService = dunceService;
        this.playerService = playerService;
        this.preferencesService = preferencesService;
        this.ipTrackingService = ipTrackingService;
        this.messageManager = messageManager;

        // Pre-compile the disallowed words into a single regex pattern
        // This is O(1) matching vs O(n) loop through words
        this.disallowedWordsPattern = compileDisallowedWordsPattern(disallowedWords);
    }

    /**
     * Compile disallowed words into a single case-insensitive regex pattern
     * Returns null if no words are configured (skip filtering entirely)
     */
    private Pattern compileDisallowedWordsPattern(List<String> words) {
        if (words == null || words.isEmpty()) {
            return null;
        }

        StringBuilder regex = new StringBuilder("(?i)(");
        boolean first = true;
        for (String word : words) {
            if (word == null || word.isBlank()) continue;
            if (!first) regex.append("|");
            regex.append(Pattern.quote(word.trim()));
            first = false;
        }
        regex.append(")");

        // If no valid words, return null
        if (first) return null;

        return Pattern.compile(regex.toString());
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        playerService.handlePlayerJoin(player);

        // Check if player's dunce has expired (handles edge case before scheduled task runs)
        dunceService.checkAndProcessExpiredDunceOnLogin(player.getUniqueId());

        // Pre-load preferences into cache for faster access during chat
        preferencesService.loadIntoCache(player.getUniqueId());

        // Handle IP tracking, alt detection, watchlist, and auto-dunce
        ipTrackingService.handlePlayerJoin(player);

        // Send any pending messages (like dunce expiry notifications)
        dunceService.sendPendingMessages(player.getUniqueId());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerUuid = player.getUniqueId();

        playerService.handlePlayerQuit(player);

        // Clean up caches to free memory
        preferencesService.invalidateCache(playerUuid);
        dunceService.invalidateCache(playerUuid);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerChat(@NotNull AsyncChatEvent event) {
        Player player = event.getPlayer();
        UUID playerUuid = player.getUniqueId();

        Component displayNameComponent = playerService.getDisplayNameComponent(player);
        Component prefixComponent = playerService.getPrefixComponent(player);

        // Combine prefix and name into a single component
        Component fullNameComponent = prefixComponent.append(displayNameComponent);

        // Get plain text message for logging
        String plainMessage = PlainTextComponentSerializer.plainText().serialize(event.message());

        if (dunceService.isDunced(playerUuid)) {
            // Handle dunced player chat - use dunce_chat_format
            Set<Player> recipients = preferencesService.getPlayersWithDunceChatVisible();
            event.viewers().retainAll(recipients);

            // Create placeholder map for MiniMessage
            Map<String, Component> placeholders = new HashMap<>();
            placeholders.put("player", fullNameComponent);
            placeholders.put("message", event.message());

            Component message = messageManager.getWithComponents("dunce_chat_format", placeholders);
            event.renderer((source, sourceDisplayName, msg, viewer) -> message);

            // Log dunce chat message
            logger.info("[DunceChat] [Dunced] " + player.getName() + ": " + plainMessage);

        } else if (preferencesService.isInDunceChat(playerUuid)) {
            // Handle staff/observer in dunce chat - use dunce_chat_observer_format
            Set<Player> recipients = preferencesService.getPlayersWithDunceChatVisible();
            recipients.add(player);

            event.viewers().retainAll(recipients);

            // Create placeholder map for MiniMessage
            Map<String, Component> placeholders = new HashMap<>();
            placeholders.put("player", fullNameComponent);
            placeholders.put("message", event.message());

            Component message = messageManager.getWithComponents("dunce_chat_observer_format", placeholders);
            event.renderer((source, sourceDisplayName, msg, viewer) -> message);

            // Log dunce chat message from observer
            logger.info("[DunceChat] [Observer] " + player.getName() + ": " + plainMessage);
        }

    }

    @EventHandler(priority = EventPriority.LOW)
    public void onWordFilter(AsyncChatEvent event) {
        // Skip if no disallowed words configured
        if (disallowedWordsPattern == null) {
            return;
        }

        Player player = event.getPlayer();

        // Skip if player has admin permission
        if (player.hasPermission("duncechat.admin")) {
            return;
        }

        // Skip if already in unmoderated chat
        if (preferencesService.isInDunceChat(player.getUniqueId())) {
            return;
        }

        String message = PlainTextComponentSerializer.plainText().serialize(event.message());
        String lowerMessage = message.toLowerCase();

        // Use the pre-compiled regex pattern for fast matching
        Matcher matcher = disallowedWordsPattern.matcher(lowerMessage);
        if (matcher.find()) {
            UUID playerUuid = player.getUniqueId();

            if (!dunceService.isDunced(playerUuid)) {
                // Auto-dunce with the trigger message stored
                dunceService.duncePlayer(playerUuid, "AutoDunced", null, null, message);
            }

            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onCommandPreprocess(@NotNull PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage();

        // Block /me command for dunced players
        if (message.startsWith("/me") && dunceService.isDunced(player.getUniqueId())) {
            event.setCancelled(true);
            player.sendMessage(messageManager.get("me_command_blocked"));
            return;
        }

        // Block whispers to players without dunce chat visible
        if (!message.contains(" ") || !dunceService.isDunced(player.getUniqueId())) {
            return;
        }

        String command = message.substring(1, message.contains(" ") ? message.indexOf(" ") : message.length()).toLowerCase();

        if (WHISPER_COMMANDS.contains(command)) {
            String[] parts = message.split(" ");
            if (parts.length >= 2) {
                String recipientName = parts[1];
                playerService.getUuidByName(recipientName).ifPresent(recipientUuid -> {
                    if (!preferencesService.isDunceChatVisible(recipientUuid)) {
                        event.setCancelled(true);
                        player.sendMessage(messageManager.get("whisper_blocked"));
                    }
                });
            }
        }
    }
}

