package gg.corn.DunceChat.command;

import gg.corn.DunceChat.gui.DunceGUIBuilder;
import gg.corn.DunceChat.service.DunceService;
import gg.corn.DunceChat.service.PreferencesService;
import gg.corn.DunceChat.util.MessageManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Command handler for /duncechat (alias /dc)
 * Opens the DunceChat menu when run without arguments
 * Allows players to send messages in Dunce Chat when run with arguments
 */
public class DunceChatCommand implements CommandExecutor {

    private final DunceService dunceService;
    private final PreferencesService preferencesService;
    private final MessageManager messageManager;
    private final DunceGUIBuilder guiBuilder;

    public DunceChatCommand(DunceService dunceService, PreferencesService preferencesService, MessageManager messageManager, DunceGUIBuilder guiBuilder) {
        this.dunceService = dunceService;
        this.preferencesService = preferencesService;
        this.messageManager = messageManager;
        this.guiBuilder = guiBuilder;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        // Must be a player
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command can only be used by players.", NamedTextColor.RED));
            return true;
        }


        // If no arguments, open the DunceChat menu
        if (args.length == 0) {
            guiBuilder.openGUI(player);
            return true;
        }

        // Build the message
        String message = String.join(" ", args);

        // Check if player is dunced
        boolean isDunced = dunceService.isDunced(player.getUniqueId());

        // Check if player is in dunce chat mode
        boolean inDunceChat = preferencesService.isInDunceChat(player.getUniqueId());

        // Check if player can see/send dunce chat
        boolean canSeeDunceChat = preferencesService.isDunceChatVisible(player.getUniqueId());

        // Player must be either:
        // 1. Dunced (automatically in dunce chat)
        // 2. In dunce chat mode (opted in)
        // 3. Have visibility enabled (staff monitoring)
        if (!isDunced && !inDunceChat && !canSeeDunceChat) {
            player.sendMessage(Component.text("You cannot send messages in Dunce Chat.", NamedTextColor.RED));
            player.sendMessage(Component.text("Use /dcon to enable Dunce Chat visibility.", NamedTextColor.GRAY));
            return true;
        }

        // Send the message through dunce service
        dunceService.sendDunceChatMessage(player, message);

        return true;
    }
}
