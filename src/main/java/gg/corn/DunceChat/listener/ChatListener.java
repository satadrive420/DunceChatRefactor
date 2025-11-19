package gg.corn.DunceChat.listener;

import gg.corn.DunceChat.service.DunceService;
import gg.corn.DunceChat.service.PlayerService;
import gg.corn.DunceChat.service.PreferencesService;
import gg.corn.DunceChat.util.MessageManager;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Refactored chat event listener using services
 */
public class ChatListener implements Listener {

    private final DunceService dunceService;
    private final PlayerService playerService;
    private final PreferencesService preferencesService;
    private final MessageManager messageManager;
    private final FileConfiguration config;
    private final List<String> disallowedWords;
    private static final Logger logger = Logger.getLogger("DunceChat");

    public ChatListener(DunceService dunceService, PlayerService playerService,
                       PreferencesService preferencesService, MessageManager messageManager,
                       FileConfiguration config, List<String> disallowedWords) {
        this.dunceService = dunceService;
        this.playerService = playerService;
        this.preferencesService = preferencesService;
        this.messageManager = messageManager;
        this.config = config;
        this.disallowedWords = disallowedWords;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        playerService.handlePlayerJoin(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        playerService.handlePlayerQuit(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerChat(@NotNull AsyncChatEvent event) {
        Player player = event.getPlayer();
        UUID playerUuid = player.getUniqueId();

        Component playerNameComponent = playerService.getDisplayNameComponent(player);
        Component prefixComponent = playerService.getPrefixComponent(player);

        String duncedPrefixText = config.getString("dunced-prefix", "Dunced");
        String unmoderatedPrefixText = config.getString("unmoderated-chat-prefix", "UC");

        Component duncedPrefix = Component.text("[")
                .color(net.kyori.adventure.text.format.NamedTextColor.DARK_GRAY)
                .append(Component.text(duncedPrefixText).color(net.kyori.adventure.text.format.NamedTextColor.GOLD))
                .append(Component.text("]").color(net.kyori.adventure.text.format.NamedTextColor.DARK_GRAY));

        Component unmoderatedPrefix = Component.text("[")
                .color(net.kyori.adventure.text.format.NamedTextColor.DARK_GRAY)
                .append(Component.text(unmoderatedPrefixText).color(net.kyori.adventure.text.format.NamedTextColor.GOLD))
                .append(Component.text("]").color(net.kyori.adventure.text.format.NamedTextColor.DARK_GRAY));

        if (dunceService.isDunced(playerUuid)) {
            // Handle dunced player chat
            Set<Player> recipients = preferencesService.getPlayersWithDunceChatVisible();
            event.viewers().retainAll(recipients);

            Component message = Component.text("<")
                    .append(duncedPrefix)
                    .append(prefixComponent)
                    .append(playerNameComponent)
                    .append(Component.text("> "))
                    .append(event.message());

            event.renderer((source, sourceDisplayName, msg, viewer) -> message);

            logger.info(PlainTextComponentSerializer.plainText().serialize(message));

        } else if (preferencesService.isInDunceChat(playerUuid)) {
            // Handle unmoderated chat
            Set<Player> recipients = preferencesService.getPlayersWithDunceChatVisible();
            recipients.add(player);

            event.viewers().retainAll(recipients);

            Component message = Component.text("<")
                    .append(unmoderatedPrefix)
                    .append(prefixComponent)
                    .append(playerNameComponent)
                    .append(Component.text("> "))
                    .append(event.message());

            event.renderer((source, sourceDisplayName, msg, viewer) -> message);

            logger.info(PlainTextComponentSerializer.plainText().serialize(message));
        }

        // Add dunce star if enabled
        if (config.getBoolean("dunceStar", false)) {
            if (!dunceService.isDunced(playerUuid) && preferencesService.isDunceChatVisible(playerUuid)) {
                // Use renderer to add the star to the player name
                event.renderer((source, sourceDisplayName, msg, viewer) -> {
                    Component nameWithStar = sourceDisplayName.append(Component.text("*")
                            .color(net.kyori.adventure.text.format.NamedTextColor.GREEN));
                    return Component.translatable("chat.type.text", nameWithStar, msg);
                });
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onWordFilter(AsyncChatEvent event) {
        Player player = event.getPlayer();

        // Skip if player has admin permission
        if (player.hasPermission("duncechat.admin")) {
            return;
        }

        // Skip if already in unmoderated chat
        if (preferencesService.isInDunceChat(player.getUniqueId())) {
            return;
        }

        String message = PlainTextComponentSerializer.plainText().serialize(event.message()).toLowerCase();

        for (String word : disallowedWords) {
            if (message.contains(word.toLowerCase())) {
                UUID playerUuid = player.getUniqueId();

                if (!dunceService.isDunced(playerUuid)) {
                    dunceService.duncePlayer(playerUuid, "AutoDunced", null, null);
                }

                event.setCancelled(true);
                break;
            }
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

        List<String> whisperCommands = Arrays.asList("whisper", "w", "msg", "r", "reply");
        String command = message.substring(1, message.contains(" ") ? message.indexOf(" ") : message.length()).toLowerCase();

        if (whisperCommands.contains(command)) {
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

