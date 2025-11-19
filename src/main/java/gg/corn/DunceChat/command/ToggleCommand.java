package gg.corn.DunceChat.command;

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

    private final PreferencesService preferencesService;
    private final MessageManager messageManager;

    public ToggleCommand(PreferencesService preferencesService, MessageManager messageManager) {
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
            preferencesService.setDunceChatVisible(player.getUniqueId(), false);
            String status = messageManager.getRaw("dunce_chat_hidden");
            player.sendMessage(messageManager.get("dunce_chat_visible", status));
        }

        return true;
    }
}

