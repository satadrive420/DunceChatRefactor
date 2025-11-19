package gg.corn.DunceChat.command;

import gg.corn.DunceChat.DunceChat;
import gg.corn.DunceChat.util.MessageManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

/**
 * Refactored reload command using MessageManager
 */
public class ReloadCommand implements CommandExecutor {

    private final DunceChat plugin;
    private final MessageManager messageManager;

    public ReloadCommand(DunceChat plugin, MessageManager messageManager) {
        this.plugin = plugin;
        this.messageManager = messageManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("duncechat.reload")) {
            sender.sendMessage(messageManager.get("no_permission"));
            return true;
        }

        try {
            plugin.reloadConfig();
            messageManager.reload();
            sender.sendMessage(messageManager.getPrefixed("reload_success"));
        } catch (Exception e) {
            sender.sendMessage(messageManager.getPrefixed("reload_failed"));
            e.printStackTrace();
        }

        return true;
    }
}

