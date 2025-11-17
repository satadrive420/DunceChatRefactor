package gg.corn.DunceChat.command;

import gg.corn.DunceChat.model.DunceRecord;
import gg.corn.DunceChat.service.DunceService;
import gg.corn.DunceChat.service.PlayerService;
import gg.corn.DunceChat.util.MessageManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.text.SimpleDateFormat;
import java.util.Optional;
import java.util.UUID;

/**
 * Refactored lookup command using services
 */
public class LookupCommandNew implements CommandExecutor {

    private final DunceService dunceService;
    private final PlayerService playerService;
    private final MessageManager messageManager;
    private static final SimpleDateFormat DATE_FORMATTER = new SimpleDateFormat("yyyy/MM/dd HH:mm");

    public LookupCommandNew(DunceService dunceService, PlayerService playerService, MessageManager messageManager) {
        this.dunceService = dunceService;
        this.playerService = playerService;
        this.messageManager = messageManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("duncechat.admin")) {
            sender.sendMessage(messageManager.get("no_permission"));
            return true;
        }

        if (args.length != 1) {
            sender.sendMessage(messageManager.get("lookup_usage"));
            return true;
        }

        String playerName = args[0];
        Optional<UUID> playerUuid = playerService.getUuidByName(playerName);

        if (playerUuid.isEmpty()) {
            sender.sendMessage(messageManager.get("player_not_found", playerName));
            return true;
        }

        Optional<DunceRecord> record = dunceService.getActiveDunceRecord(playerUuid.get());

        if (record.isEmpty() || !record.get().isDunced()) {
            sender.sendMessage(messageManager.get("lookup_not_dunced", playerName));
            return true;
        }

        DunceRecord dunceRecord = record.get();

        // Get staff name
        String staffName = "CONSOLE";
        if (dunceRecord.getStaffUuid() != null) {
            staffName = playerService.getNameByUuid(dunceRecord.getStaffUuid()).orElse("Unknown");
        }

        // Format dates
        String duncedDate = dunceRecord.getDuncedAt() != null ?
            DATE_FORMATTER.format(dunceRecord.getDuncedAt()) : "Unknown";
        String expiryDate = dunceRecord.getExpiresAt() != null ?
            DATE_FORMATTER.format(dunceRecord.getExpiresAt()) : messageManager.getRaw("dunce_expires_never");
        String reason = dunceRecord.getReason() != null ? dunceRecord.getReason() : "No reason provided";

        // Send messages
        sender.sendMessage(messageManager.get("lookup_header", playerName));
        sender.sendMessage(messageManager.get("lookup_dunced_on", duncedDate));
        sender.sendMessage(messageManager.get("lookup_expires_on", expiryDate));
        sender.sendMessage(messageManager.get("lookup_marked_by", staffName));
        sender.sendMessage(messageManager.get("lookup_reason", reason));

        return true;
    }
}

