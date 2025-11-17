package gg.corn.DunceChat;

import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.AsyncPlayerChatEvent;


public class Greentexter implements Listener {

    public static DunceChat plugin = gg.corn.DunceChat.DunceChat.getPlugin(DunceChat.class);

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        // Retrieve the toggle setting from config.yml
        boolean isColorChangeEnabled = plugin.getConfig().getBoolean("auto-green-text", true);

        if (isColorChangeEnabled) {
            String message = event.getMessage();

            // Check if the message starts with ">"
            if (message.startsWith(">")) {
                // Check if the character following ">" is not typically used in emoticons
                if (message.length() > 1 && !Character.isWhitespace(message.charAt(1)) && Character.isLetterOrDigit(message.charAt(1))) {
                    // It's more likely a command or statement, not an emoticon
                    // Set the message color to light green using legacy format code
                    event.setMessage("§a" + message);
                }
            }
        }
    }
}
