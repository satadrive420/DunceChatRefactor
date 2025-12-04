package gg.corn.DunceChat.command;

import gg.corn.DunceChat.service.DunceService;
import gg.corn.DunceChat.service.PreferencesService;
import gg.corn.DunceChat.util.MessageManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Refactored toggle command using services
 */
public class ToggleCommand implements CommandExecutor {

    private final DunceService dunceService;
    private final PreferencesService preferencesService;
    private final MessageManager messageManager;

    public ToggleCommand(DunceService dunceService, PreferencesService preferencesService, MessageManager messageManager) {
        this.dunceService = dunceService;
        this.preferencesService = preferencesService;
        this.messageManager = messageManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            return true;
        }

        if (label.equalsIgnoreCase("dcon")) {
            preferencesService.setDunceChatVisible(player.getUniqueId(), true);
            String status = messageManager.getRaw("dunce_chat_shown");
            player.sendMessage(messageManager.get("dunce_chat_visible", status));
        } else if (label.equalsIgnoreCase("dcoff")) {
            // Dunced players cannot hide dunce chat
            if (dunceService.isDunced(player.getUniqueId())) {
                player.sendMessage(messageManager.get("dunce_chat_forced_visible"));
                return true;
            }

            preferencesService.setDunceChatVisible(player.getUniqueId(), false);
            String status = messageManager.getRaw("dunce_chat_hidden");
            player.sendMessage(messageManager.get("dunce_chat_visible", status));
        }

        return true;
    }
}

