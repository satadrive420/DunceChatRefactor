package gg.corn.DunceChat.listener;

import gg.corn.DunceChat.service.DunceService;
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
import org.bukkit.event.player.AsyncPlayerChatEvent;
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
 * Handles chat events for dunced players and dunce chat observers
 * Optimized for performance with pre-compiled regex and minimal allocations
 *
 * Note: Player join/quit events moved to PlayerConnectionListener
 * Note: Command blocking moved to DunceCommandBlockListener
 */
public class ChatListener implements Listener {

    private final DunceService dunceService;
    private final PlayerService playerService;
    private final PreferencesService preferencesService;
    private final MessageManager messageManager;
    private static final Logger logger = Logger.getLogger("DunceChat");

    // Pre-compiled regex pattern for word filtering (much faster than loop + contains)
    private final Pattern disallowedWordsPattern;

    public ChatListener(DunceService dunceService, PlayerService playerService,
                       PreferencesService preferencesService, MessageManager messageManager,
                       List<String> disallowedWords) {
        this.dunceService = dunceService;
        this.playerService = playerService;
        this.preferencesService = preferencesService;
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


    /**
     * Cancel chat events for dunced players and dunce chat observers at LOWEST priority.
     * This prevents third-party plugins (Dynmap, etc.) from seeing these messages.
     * Runs before all other plugins process the chat event.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onChatPreProcess(@NotNull AsyncChatEvent event) {
        Player player = event.getPlayer();
        UUID playerUuid = player.getUniqueId();

        // Cancel event if player is dunced or in dunce chat observer mode
        // This blocks the event from being seen by third-party chat plugins
        if (dunceService.isDunced(playerUuid) || preferencesService.isInDunceChat(playerUuid)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onPlayerChat(@NotNull AsyncChatEvent event) {
        // Skip if event not cancelled (normal chat handled by other plugins/default system)
        if (!event.isCancelled()) {
            return;
        }

        Player player = event.getPlayer();
        UUID playerUuid = player.getUniqueId();

        // Skip if not cancelled by our pre-processor (could be cancelled by another plugin)
        if (!dunceService.isDunced(playerUuid) && !preferencesService.isInDunceChat(playerUuid)) {
            return;
        }

        Component displayNameComponent = playerService.getDisplayNameComponent(player);
        Component prefixComponent = playerService.getPrefixComponent(player);

        // Combine prefix and name into a single component
        Component fullNameComponent = prefixComponent.append(displayNameComponent);

        // Get plain text message for logging
        String plainMessage = PlainTextComponentSerializer.plainText().serialize(event.message());

        if (dunceService.isDunced(playerUuid)) {
            // Handle dunced player chat - use dunce_chat_format
            Set<Player> recipients = preferencesService.getPlayersWithDunceChatVisible();

            // Create placeholder map for MiniMessage
            Map<String, Component> placeholders = new HashMap<>();
            placeholders.put("player", fullNameComponent);
            placeholders.put("message", event.message());

            Component message = messageManager.getWithComponents("dunce_chat_format", placeholders);

            // Manually send message to each recipient (event is cancelled, so we send directly)
            for (Player recipient : recipients) {
                recipient.sendMessage(message);
            }

            // Log dunce chat message
            logger.info("[DunceChat] [Dunced] " + player.getName() + ": " + plainMessage);

        } else if (preferencesService.isInDunceChat(playerUuid)) {
            // Handle staff/observer in dunce chat - use dunce_chat_observer_format
            Set<Player> recipients = preferencesService.getPlayersWithDunceChatVisible();
            recipients.add(player);

            // Create placeholder map for MiniMessage
            Map<String, Component> placeholders = new HashMap<>();
            placeholders.put("player", fullNameComponent);
            placeholders.put("message", event.message());

            Component message = messageManager.getWithComponents("dunce_chat_observer_format", placeholders);

            // Manually send message to each recipient (event is cancelled, so we send directly)
            for (Player recipient : recipients) {
                recipient.sendMessage(message);
            }

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

    // ===== LEGACY BUKKIT CHAT EVENT SUPPORT =====
    // These handlers support the legacy Bukkit AsyncPlayerChatEvent for compatibility
    // with plugins (like DiscordSRV by default) that still use the deprecated event system.

    /**
     * Cancel legacy chat events for dunced players and dunce chat observers at LOWEST priority.
     * This provides compatibility with plugins that use the deprecated AsyncPlayerChatEvent.
     */
    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.LOWEST)
    public void onLegacyChatPreProcess(@NotNull AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID playerUuid = player.getUniqueId();

        // Cancel event if player is dunced or in dunce chat observer mode
        if (dunceService.isDunced(playerUuid) || preferencesService.isInDunceChat(playerUuid)) {
            event.setCancelled(true);
        }
    }

    /**
     * Handle cancelled legacy chat events at MONITOR priority.
     * This ensures dunced player chat is still processed even when using legacy event system.
     */
    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onLegacyPlayerChat(@NotNull AsyncPlayerChatEvent event) {
        // Skip if event not cancelled (normal chat handled by other plugins/default system)
        if (!event.isCancelled()) {
            return;
        }

        Player player = event.getPlayer();
        UUID playerUuid = player.getUniqueId();

        // Skip if not cancelled by our pre-processor (could be cancelled by another plugin)
        if (!dunceService.isDunced(playerUuid) && !preferencesService.isInDunceChat(playerUuid)) {
            return;
        }

        Component displayNameComponent = playerService.getDisplayNameComponent(player);
        Component prefixComponent = playerService.getPrefixComponent(player);

        // Combine prefix and name into a single component
        Component fullNameComponent = prefixComponent.append(displayNameComponent);

        // Convert legacy string message to Component
        Component messageComponent = Component.text(event.getMessage());

        // Get plain text message for logging
        String plainMessage = event.getMessage();

        if (dunceService.isDunced(playerUuid)) {
            // Handle dunced player chat - use dunce_chat_format
            Set<Player> recipients = preferencesService.getPlayersWithDunceChatVisible();

            // Create placeholder map for MiniMessage
            Map<String, Component> placeholders = new HashMap<>();
            placeholders.put("player", fullNameComponent);
            placeholders.put("message", messageComponent);

            Component message = messageManager.getWithComponents("dunce_chat_format", placeholders);

            // Manually send message to each recipient (event is cancelled, so we send directly)
            for (Player recipient : recipients) {
                recipient.sendMessage(message);
            }

            // Log dunce chat message
            logger.info("[DunceChat] [Dunced] " + player.getName() + ": " + plainMessage);

        } else if (preferencesService.isInDunceChat(playerUuid)) {
            // Handle staff/observer in dunce chat - use dunce_chat_observer_format
            Set<Player> recipients = preferencesService.getPlayersWithDunceChatVisible();
            recipients.add(player);

            // Create placeholder map for MiniMessage
            Map<String, Component> placeholders = new HashMap<>();
            placeholders.put("player", fullNameComponent);
            placeholders.put("message", messageComponent);

            Component message = messageManager.getWithComponents("dunce_chat_observer_format", placeholders);

            // Manually send message to each recipient (event is cancelled, so we send directly)
            for (Player recipient : recipients) {
                recipient.sendMessage(message);
            }

            // Log dunce chat message from observer
            logger.info("[DunceChat] [Observer] " + player.getName() + ": " + plainMessage);
        }
    }

    /**
     * Word filter for legacy chat event.
     */
    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.LOW)
    public void onLegacyWordFilter(AsyncPlayerChatEvent event) {
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

        String message = event.getMessage();
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
}

