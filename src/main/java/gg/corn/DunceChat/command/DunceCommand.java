package gg.corn.DunceChat.command;

import gg.corn.DunceChat.service.DunceService;
import gg.corn.DunceChat.service.PlayerService;
import gg.corn.DunceChat.util.MessageManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.sql.Timestamp;
import java.util.*;

/**
 * Refactored dunce command using services
 */
public class DunceCommand implements CommandExecutor, TabCompleter {

    private final DunceService dunceService;
    private final PlayerService playerService;
    private final MessageManager messageManager;

    public DunceCommand(DunceService dunceService, PlayerService playerService, MessageManager messageManager) {
        this.dunceService = dunceService;
        this.playerService = playerService;
        this.messageManager = messageManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {

        if (label.equalsIgnoreCase("dunce")) {
            handleDunceCommand(sender, args);
        } else if (label.equalsIgnoreCase("undunce")) {
            handleUndunceCommand(sender, args);
        }

        return true;
    }

    private void handleDunceCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("duncechat.admin")) {
            if (args.length == 0) {
                sendPlayerHelp(sender);
            } else {
                sender.sendMessage(messageManager.get("no_permission"));
            }
            return;
        }

        if (args.length == 0) {
            sendAdminHelp(sender);
            return;
        }

        String playerName = args[0];

        // Parse duration if provided
        Timestamp expiry = null;
        int reasonStartIndex = 1;

        if (args.length >= 2 && isDuration(args[1])) {
            expiry = parseDuration(args[1]);
            reasonStartIndex = 2;
        }

        // Parse reason
        StringBuilder reasonBuilder = new StringBuilder();
        for (int i = reasonStartIndex; i < args.length; i++) {
            if (i > reasonStartIndex) reasonBuilder.append(" ");
            reasonBuilder.append(args[i]);
        }
        String reason = reasonBuilder.toString().trim();
        if (reason.isEmpty()) reason = null;

        // Get player UUID
        Optional<UUID> targetUuid = playerService.getUuidByName(playerName);
        if (targetUuid.isEmpty()) {
            sender.sendMessage(messageManager.get("player_not_found", playerName));
            return;
        }

        // Check if player can be dunced
        Player targetPlayer = sender.getServer().getPlayer(targetUuid.get());
        if (targetPlayer != null && targetPlayer.hasPermission("duncechat.admin")) {
            sender.sendMessage(messageManager.get("player_cannot_be_dunced"));
            return;
        }

        // Check if already dunced
        if (dunceService.isDunced(targetUuid.get())) {
            sender.sendMessage(messageManager.get("already_dunced"));
            return;
        }

        // Dunce the player
        UUID staffUuid = sender instanceof Player ? ((Player) sender).getUniqueId() : null;
        dunceService.duncePlayer(targetUuid.get(), reason, staffUuid, expiry, null);
    }

    private void handleUndunceCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("duncechat.admin")) {
            sender.sendMessage(messageManager.get("no_permission"));
            return;
        }

        if (args.length == 0) {
            sender.sendMessage(Component.text("Usage: /undunce <player>", NamedTextColor.RED));
            return;
        }

        String playerName = args[0];
        Optional<UUID> targetUuid = playerService.getUuidByName(playerName);

        if (targetUuid.isEmpty()) {
            sender.sendMessage(messageManager.get("player_not_found", playerName));
            return;
        }

        if (!dunceService.isDunced(targetUuid.get())) {
            sender.sendMessage(messageManager.get("not_dunced"));
            return;
        }

        UUID staffUuid = sender instanceof Player ? ((Player) sender).getUniqueId() : null;dunceService.unduncePlayer(targetUuid.get(), staffUuid, false);  // false = staff action
    }

    private void sendAdminHelp(CommandSender sender) {
        sender.sendMessage(messageManager.get("help_header"));
        sender.sendMessage(messageManager.get("help_dunce"));
        sender.sendMessage(messageManager.get("help_undunce"));
        sender.sendMessage(messageManager.get("help_clearchat"));
        sender.sendMessage(messageManager.get("help_dcon"));
        sender.sendMessage(messageManager.get("help_dcoff"));
        sender.sendMessage(messageManager.get("help_duncemenu"));
        sender.sendMessage(messageManager.get("help_duncelookup"));
    }

    private void sendPlayerHelp(CommandSender sender) {
        sender.sendMessage(messageManager.get("help_player_header"));
        sender.sendMessage(messageManager.get("help_player_dcon"));
        sender.sendMessage(messageManager.get("help_player_dcoff"));
        sender.sendMessage(messageManager.get("help_player_duncemenu"));
    }

    private boolean isDuration(String arg) {
        if (arg.length() < 2) return false;
        char last = arg.charAt(arg.length() - 1);
        if (last != 's' && last != 'm' && last != 'h' && last != 'd' && last != 'w') return false;

        try {
            Integer.parseInt(arg.substring(0, arg.length() - 1));
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private Timestamp parseDuration(String duration) {
        long seconds = 0;
        String number = duration.substring(0, duration.length() - 1);
        char unit = duration.charAt(duration.length() - 1);

        long value = Long.parseLong(number);

        seconds = switch (unit) {
            case 's' -> value;
            case 'm' -> value * 60;
            case 'h' -> value * 3600;
            case 'd' -> value * 86400;
            case 'w' -> value * 604800;
            default -> 0;
        };

        return new Timestamp(System.currentTimeMillis() + (seconds * 1000));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();

        if (!sender.hasPermission("duncechat.admin")) {
            return completions;
        }

        if (command.getName().equalsIgnoreCase("dunce")) {
            if (args.length == 1) {
                // Player names
                for (Player player : sender.getServer().getOnlinePlayers()) {
                    if (!player.hasPermission("duncechat.admin") &&
                        player.getName().toLowerCase().startsWith(args[0].toLowerCase())) {
                        completions.add(player.getName());
                    }
                }
            } else if (args.length == 2) {
                // Duration suggestions
                completions.addAll(Arrays.asList("1s", "1m", "1h", "1d", "1w"));
            } else if (args.length == 3) {
                // Reason suggestions
                completions.addAll(Arrays.asList("Racism/Nazism/Hate Speech", "Spam", "Blockgame Lawyer"));
            }
        } else if (command.getName().equalsIgnoreCase("undunce")) {
            if (args.length == 1) {
                // Dunced players
                for (Player player : sender.getServer().getOnlinePlayers()) {
                    if (player.getName().toLowerCase().startsWith(args[0].toLowerCase())) {
                        completions.add(player.getName());
                    }
                }
            }
        }

        Collections.sort(completions);
        return completions;
    }
}

