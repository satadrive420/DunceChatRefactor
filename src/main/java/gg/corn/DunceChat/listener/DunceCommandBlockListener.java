package gg.corn.DunceChat.listener;

import gg.corn.DunceChat.service.DunceService;
import gg.corn.DunceChat.service.PlayerService;
import gg.corn.DunceChat.service.PreferencesService;
import gg.corn.DunceChat.util.MessageManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * Handles command blocking for dunced players
 * Separated from ChatListener for better separation of concerns
 */
public class DunceCommandBlockListener implements Listener {

    private final DunceService dunceService;
    private final PlayerService playerService;
    private final PreferencesService preferencesService;
    private final MessageManager messageManager;

    // Pre-compiled whisper command set (avoid creating new list on every command)
    private static final Set<String> WHISPER_COMMANDS = Set.of("whisper", "w", "msg", "r", "reply", "tell");

    public DunceCommandBlockListener(DunceService dunceService, PlayerService playerService,
                                    PreferencesService preferencesService, MessageManager messageManager) {
        this.dunceService = dunceService;
        this.playerService = playerService;
        this.preferencesService = preferencesService;
        this.messageManager = messageManager;
    }

    /**
     * Block certain commands for dunced players
     * - /me command (action messages)
     * - Whisper commands to players who can't see dunce chat
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onCommandPreprocess(@NotNull PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage();

        // Only process if player is dunced
        if (!dunceService.isDunced(player.getUniqueId())) {
            return;
        }

        // Block /me command for dunced players
        if (message.startsWith("/me")) {
            event.setCancelled(true);
            player.sendMessage(messageManager.get("me_command_blocked"));
            return;
        }

        // Block whispers to players without dunce chat visible
        if (!message.contains(" ")) {
            return;
        }

        String command = message.substring(1, message.indexOf(" ")).toLowerCase();

        if (WHISPER_COMMANDS.contains(command)) {
            String[] parts = message.split(" ", 3); // Split into: command, recipient, message
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

