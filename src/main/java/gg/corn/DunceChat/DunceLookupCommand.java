package gg.corn.DunceChat;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import java.util.UUID;

public class DunceLookupCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Check if the command is /duncelookup or /dl
        if (label.equalsIgnoreCase("duncelookup") || label.equalsIgnoreCase("dl")) {
            // Check if the command includes the player's name
            if (args.length == 1) {
                String playerName = args[0];
                // Here, implement your method to fetch the player's UUID using the playerName
                UUID playerUUID = MySQLHandler.getUUIDByName(playerName);

                // Check if player has permission
                if (!sender.hasPermission("duncechat.admin")){
                    sender.sendMessage("You don't have permission to execute this command.");
                }

                // If the playerUUID is null, it means the player does not exist in the database
                if (playerUUID == null) {
                    sender.sendMessage(ChatColor.RED + "Player '" + playerName + "' has never joined, or has recently changed their name!");
                    return true;
                }

                if (!DunceChat.isDunced(playerUUID)) {
                    sender.sendMessage(ChatColor.RED + "Player '" + playerName + "' is not dunced!");
                    return true;
                }

                // Fetch dunce details using the player's UUID
                String staffName = MySQLHandler.getNameByUUID(UUID.fromString(MySQLHandler.getWhoDunced(playerUUID)));
                String date = MySQLHandler.getDunceDate(playerUUID);
                String reason = MySQLHandler.getDunceReason(playerUUID);
                String expiry = MySQLHandler.getDunceExpiry(playerUUID);

                // Send dunce details to the command sender
                sender.sendMessage(ChatColor.GOLD + "Dunce Details for " + playerName + ":");
                sender.sendMessage(ChatColor.GREEN + "Dunced on: " + ChatColor.WHITE + date);
                sender.sendMessage(ChatColor.GREEN + "Expires on: " + ChatColor.WHITE + expiry);
                sender.sendMessage(ChatColor.GREEN + "Marked by: " + ChatColor.WHITE + staffName);
                sender.sendMessage(ChatColor.GREEN + "Reason: " + ChatColor.WHITE + reason);

                return true;
            } else {
                sender.sendMessage(ChatColor.RED + "Usage: /duncelookup <player>");
                return false;
            }
        }
        return false;
    }

}