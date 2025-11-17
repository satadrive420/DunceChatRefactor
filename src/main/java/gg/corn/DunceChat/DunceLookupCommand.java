package gg.corn.DunceChat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
                    sender.sendMessage(Component.text("You don't have permission to execute this command.", NamedTextColor.RED));
                }

                // If the playerUUID is null, it means the player does not exist in the database
                if (playerUUID == null) {
                    sender.sendMessage(Component.text("Player '" + playerName + "' has never joined, or has recently changed their name!", NamedTextColor.RED));
                    return true;
                }

                if (!DunceChat.isDunced(playerUUID)) {
                    sender.sendMessage(Component.text("Player '" + playerName + "' is not dunced!", NamedTextColor.RED));
                    return true;
                }

                // Fetch dunce details using the player's UUID
                String staffName = MySQLHandler.getNameByUUID(UUID.fromString(MySQLHandler.getWhoDunced(playerUUID)));
                String date = MySQLHandler.getDunceDate(playerUUID);
                String reason = MySQLHandler.getDunceReason(playerUUID);
                String expiry = MySQLHandler.getDunceExpiry(playerUUID);

                // Send dunce details to the command sender
                sender.sendMessage(Component.text("Dunce Details for " + playerName + ":", NamedTextColor.GOLD));
                sender.sendMessage(Component.text("Dunced on: ", NamedTextColor.GREEN)
                        .append(Component.text(date, NamedTextColor.WHITE)));
                sender.sendMessage(Component.text("Expires on: ", NamedTextColor.GREEN)
                        .append(Component.text(expiry, NamedTextColor.WHITE)));
                sender.sendMessage(Component.text("Marked by: ", NamedTextColor.GREEN)
                        .append(Component.text(staffName, NamedTextColor.WHITE)));
                sender.sendMessage(Component.text("Reason: ", NamedTextColor.GREEN)
                        .append(Component.text(reason, NamedTextColor.WHITE)));

                return true;
            } else {
                sender.sendMessage(Component.text("Usage: /duncelookup <player>", NamedTextColor.RED));
                return false;
            }
        }
        return false;
    }

}