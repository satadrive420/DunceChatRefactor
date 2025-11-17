package gg.corn.DunceChat;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class ReloadCommand implements CommandExecutor {

    public static DunceChat plugin = gg.corn.DunceChat.DunceChat.getPlugin(DunceChat.class);

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender.hasPermission("duncechat.reload")) {
            reloadPluginConfig();
            sender.sendMessage("Plugin configuration reloaded!");
            return true;
        } else {
            sender.sendMessage("You don't have permission to execute this command.");
            return false;
        }
    }

    private void reloadPluginConfig() {
        plugin.reloadConfig();
        // If you have other configurations or services to reload, do it here
        // For example: plugin.getSomeManager().reload();
    }
}