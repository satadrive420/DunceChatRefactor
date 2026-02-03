package gg.corn.DunceChat.command;

import gg.corn.DunceChat.service.DunceService;
import gg.corn.DunceChat.service.PlayerService;
import gg.corn.DunceChat.util.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Command handler for /dunceunlink
 * Removes a player from IP tracking by clearing their IP history
 */
public class UnlinkCommand implements CommandExecutor, TabCompleter {

    private final DunceService dunceService;
    private final PlayerService playerService;
    private final MessageManager messageManager;

    public UnlinkCommand(DunceService dunceService, PlayerService playerService, MessageManager messageManager) {
        this.dunceService = dunceService;
        this.playerService = playerService;
        this.messageManager = messageManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                            @NotNull String label, @NotNull String[] args) {
        // Permission check
        if (!sender.hasPermission("duncechat.admin")) {
            sender.sendMessage(messageManager.get("no_permission"));
            return true;
        }

        // Usage check
        if (args.length != 1) {
            sender.sendMessage(messageManager.get("usage_unlink"));
            return true;
        }

        String targetName = args[0];

        // Get target UUID
        Optional<UUID> targetUuid = playerService.getUuidByName(targetName);
        if (targetUuid.isEmpty()) {
            sender.sendMessage(messageManager.get("player_not_found", targetName));
            return true;
        }

        // Get IP count before unlinking
        List<String> ips = dunceService.getPlayerIPs(targetUuid.get());
        int ipCount = ips.size();

        if (ipCount == 0) {
            sender.sendMessage(messageManager.get("unlink_no_data", targetName));
            return true;
        }

        // Perform the unlink
        int deletedCount = dunceService.unlinkPlayerFromIPTracking(targetUuid.get());

        // Send success message
        sender.sendMessage(messageManager.get("unlink_success", targetName, String.valueOf(deletedCount)));

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("duncechat.admin")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            // Suggest online player names
            String partial = args[0].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(partial))
                    .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }
}

