package gg.corn.DunceChat.command;

import gg.corn.DunceChat.util.MessageManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Refactored clear chat command using MessageManager
 */
public class ClearChatCommandNew implements CommandExecutor {

    private final MessageManager messageManager;

    public ClearChatCommandNew(MessageManager messageManager) {
        this.messageManager = messageManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("dunce.clearchat")) {
            sender.sendMessage(messageManager.get("no_permission"));
            return true;
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            for (int i = 0; i < 100; i++) {
                player.sendMessage(Component.empty());
            }
        }

        sender.sendMessage(messageManager.getPrefixed("clear_chat_success"));
        return true;
    }
}

