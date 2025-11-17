package gg.corn.DunceChat.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.Plugin;

/**
 * Handles greentext formatting for messages starting with >
 */
public class GreentextListener implements Listener {

    private final Plugin plugin;

    public GreentextListener(Plugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        boolean isEnabled = plugin.getConfig().getBoolean("auto-green-text", true);

        if (!isEnabled) {
            return;
        }

        String message = event.getMessage();

        // Check if the message starts with ">"
        if (message.startsWith(">")) {
            // Check if the character following ">" is not typically used in emoticons
            if (message.length() > 1 &&
                !Character.isWhitespace(message.charAt(1)) &&
                Character.isLetterOrDigit(message.charAt(1))) {
                // Set the message color to green using legacy format code
                event.setMessage("§a" + message);
            }
        }
    }
}

