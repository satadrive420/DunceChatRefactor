package gg.corn.DunceChat.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

/**
 * Handles greentext formatting for messages starting with >
 */
public class GreentextListener implements Listener {

    private final Plugin plugin;

    public GreentextListener(Plugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerChat(AsyncChatEvent event) {
        boolean isEnabled = plugin.getConfig().getBoolean("auto-green-text", true);

        if (!isEnabled) {
            return;
        }

        // Get the plain text version of the message
        String message = PlainTextComponentSerializer.plainText().serialize(event.message());

        // Check if the message starts with ">"
        if (message.startsWith(">")) {
            // Check if the character following ">" is not typically used in emoticons
            if (message.length() > 1 &&
                !Character.isWhitespace(message.charAt(1)) &&
                Character.isLetterOrDigit(message.charAt(1))) {
                // Set the message color to green using Adventure Component API
                event.message(Component.text(message, NamedTextColor.GREEN));
            }
        }
    }
}

