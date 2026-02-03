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

import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Command handler for /dunceip and /undunceip
 */
public class IPDunceCommand implements CommandExecutor, TabCompleter {

    private final DunceService dunceService;
    private final PlayerService playerService;
    private final MessageManager messageManager;

    private static final Pattern IP_PATTERN = Pattern.compile(
        "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$"
    );

    // Pattern to detect duration strings (e.g., 1h, 30m, 1d)
    private static final Pattern DURATION_PATTERN = Pattern.compile("^\\d+[smhdw]$", Pattern.CASE_INSENSITIVE);

    public IPDunceCommand(DunceService dunceService, PlayerService playerService, MessageManager messageManager) {
        this.dunceService = dunceService;
        this.playerService = playerService;
        this.messageManager = messageManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (command.getName().equalsIgnoreCase("dunceip")) {
            return handleIPDunce(sender, args);
        } else if (command.getName().equalsIgnoreCase("undunceip")) {
            return handleUndunceIP(sender, args);
        }
        return false;
    }

    private boolean handleIPDunce(@NotNull CommandSender sender, @NotNull String[] args) {
        // Permission check
        if (!sender.hasPermission("duncechat.admin")) {
            sender.sendMessage(messageManager.get("no_permission"));
            return true;
        }

        // Usage: /dunceip <player|IP> [duration] [reason]
        // If no duration specified, it's permanent
        if (args.length < 1) {
            sender.sendMessage(messageManager.get("usage_dunceip"));
            return true;
        }

        String target = args[0];
        Timestamp expiresAt = null;
        String reason;
        int reasonStartIndex = 1;

        // Check if second argument is a duration
        if (args.length > 1 && isDurationString(args[1])) {
            // Parse duration - if it's "perm", leave expiresAt as null
            if (!args[1].equalsIgnoreCase("perm")) {
                try {
                    long duration = parseDuration(args[1]);
                    expiresAt = new Timestamp(System.currentTimeMillis() + duration);
                } catch (IllegalArgumentException e) {
                    sender.sendMessage(messageManager.get("invalid_duration"));
                    return true;
                }
            }
            reasonStartIndex = 2;
        }
        // If no duration specified, expiresAt stays null (permanent)

        // Build reason from remaining arguments
        if (args.length > reasonStartIndex) {
            reason = String.join(" ", Arrays.copyOfRange(args, reasonStartIndex, args.length));
        } else {
            reason = "No reason specified";
        }

        // Get staff UUID (null for console)
        UUID staffUuid = sender instanceof Player ? ((Player) sender).getUniqueId() : null;

        // Check if target is an IP address
        if (isValidIP(target)) {
            // Dunce by IP address
            dunceService.ipDunceByAddress(target, reason, staffUuid, expiresAt);
            sender.sendMessage(messageManager.get("ipdunce_ip_success", target));
        } else {
            // Dunce by player name
            UUID targetUuid = playerService.getUuidByName(target).orElse(null);

            if (targetUuid == null) {
                sender.sendMessage(messageManager.get("player_not_found", target));
                return true;
            }

            dunceService.ipDuncePlayer(targetUuid, reason, staffUuid, expiresAt);
            sender.sendMessage(messageManager.get("ipdunce_player_success", target));
        }

        return true;
    }

    private boolean handleUndunceIP(@NotNull CommandSender sender, @NotNull String[] args) {
        // Permission check
        if (!sender.hasPermission("duncechat.admin")) {
            sender.sendMessage(messageManager.get("no_permission"));
            return true;
        }

        // Usage: /undunceip <player|IP>
        if (args.length < 1) {
            sender.sendMessage(messageManager.get("usage_undunceip"));
            return true;
        }

        String target = args[0];
        UUID staffUuid = sender instanceof Player ? ((Player) sender).getUniqueId() : null;

        // Check if target is an IP address
        if (isValidIP(target)) {
            // Undunce by IP address
            dunceService.ipUndunceByAddress(target, staffUuid);
            sender.sendMessage(messageManager.get("undunceip_ip_success", target));
        } else {
            // Undunce by player name
            UUID targetUuid = playerService.getUuidByName(target).orElse(null);

            if (targetUuid == null) {
                sender.sendMessage(messageManager.get("player_not_found", target));
                return true;
            }

            dunceService.ipUnduncePlayer(targetUuid, staffUuid);
            sender.sendMessage(messageManager.get("undunceip_player_success", target));
        }

        return true;
    }

    /**
     * Check if a string is a valid IP address
     */
    private boolean isValidIP(String ip) {
        return IP_PATTERN.matcher(ip).matches();
    }

    /**
     * Check if a string looks like a duration (e.g., 1h, 30m, 1d, perm)
     */
    private boolean isDurationString(String str) {
        if (str.equalsIgnoreCase("perm")) {
            return true;
        }
        return DURATION_PATTERN.matcher(str).matches();
    }

    /**
     * Parse duration string (e.g., "1h", "30m", "1d")
     */
    private long parseDuration(String duration) {
        if (duration == null || duration.isEmpty()) {
            throw new IllegalArgumentException("Duration cannot be empty");
        }

        char unit = duration.charAt(duration.length() - 1);
        String numberPart = duration.substring(0, duration.length() - 1);

        try {
            long value = Long.parseLong(numberPart);

            return switch (Character.toLowerCase(unit)) {
                case 's' -> TimeUnit.SECONDS.toMillis(value);
                case 'm' -> TimeUnit.MINUTES.toMillis(value);
                case 'h' -> TimeUnit.HOURS.toMillis(value);
                case 'd' -> TimeUnit.DAYS.toMillis(value);
                case 'w' -> TimeUnit.DAYS.toMillis(value * 7);
                default -> throw new IllegalArgumentException("Invalid time unit: " + unit);
            };
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid duration format: " + duration);
        }
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

        if (command.getName().equalsIgnoreCase("dunceip") && args.length == 2) {
            // Suggest duration options (optional)
            return Arrays.asList("1h", "6h", "1d", "3d", "7d", "perm");
        }

        return Collections.emptyList();
    }
}

