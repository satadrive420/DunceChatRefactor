package gg.corn.DunceChat.command;

import gg.corn.DunceChat.repository.PlayerIPRepository;
import gg.corn.DunceChat.service.DunceService;
import gg.corn.DunceChat.service.PlayerService;
import gg.corn.DunceChat.util.MessageManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Command to look up all players associated with an IP address
 * Usage: /dunceiplookup <IP address> [page]
 */
public class IPLookupCommand implements CommandExecutor, TabCompleter {

    private final DunceService dunceService;
    private final PlayerService playerService;
    private final MessageManager messageManager;
    private final PlayerIPRepository playerIPRepository;
    private final FileConfiguration config;
    private static final SimpleDateFormat DATE_FORMATTER = new SimpleDateFormat("yyyy/MM/dd HH:mm");
    private static final int DEFAULT_ITEMS_PER_PAGE = 10;

    // Regex pattern for validating IPv4 addresses
    private static final Pattern IP_PATTERN = Pattern.compile(
        "^((25[0-5]|(2[0-4]|1\\d|[1-9]|)\\d)\\.?\\b){4}$"
    );

    public IPLookupCommand(DunceService dunceService, PlayerService playerService,
                          MessageManager messageManager, PlayerIPRepository playerIPRepository,
                          FileConfiguration config) {
        this.dunceService = dunceService;
        this.playerService = playerService;
        this.messageManager = messageManager;
        this.playerIPRepository = playerIPRepository;
        this.config = config;
    }

    private int getItemsPerPage() {
        return Math.max(5, config.getInt("ip-tracking.items-per-page", DEFAULT_ITEMS_PER_PAGE));
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("duncechat.admin")) {
            sender.sendMessage(messageManager.get("no_permission"));
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(messageManager.get("usage_iplookup"));
            return true;
        }

        String ipAddress = args[0];
        int page = 1;

        // Parse page number if provided
        if (args.length > 1) {
            try {
                page = Integer.parseInt(args[1]);
                if (page < 1) page = 1;
            } catch (NumberFormatException e) {
                page = 1;
            }
        }

        // Validate IP address format
        if (!IP_PATTERN.matcher(ipAddress).matches()) {
            sender.sendMessage(messageManager.get("iplookup_invalid_ip", ipAddress));
            return true;
        }

        // Get all players associated with this IP
        Set<UUID> directPlayers = playerIPRepository.getPlayersByIP(ipAddress);

        if (directPlayers.isEmpty()) {
            sender.sendMessage(messageManager.get("iplookup_no_players", ipAddress));
            return true;
        }

        // Build the report
        sendIPReport(sender, ipAddress, directPlayers, page);

        return true;
    }

    /**
     * Send a detailed IP lookup report with pagination
     */
    private void sendIPReport(CommandSender sender, String ipAddress, Set<UUID> directPlayers, int page) {
        // Header
        sender.sendMessage(Component.empty());
        sender.sendMessage(messageManager.get("iplookup_header"));

        // IP Address (with click to copy)
        sender.sendMessage(messageManager.get("iplookup_ip_label", ipAddress)
            .clickEvent(ClickEvent.copyToClipboard(ipAddress))
            .hoverEvent(HoverEvent.showText(messageManager.get("iplookup_click_copy"))));

        // Direct players count
        sender.sendMessage(messageManager.get("iplookup_direct_accounts", String.valueOf(directPlayers.size())));

        // Categorize players
        List<PlayerInfo> duncedPlayers = new ArrayList<>();
        List<PlayerInfo> normalPlayers = new ArrayList<>();

        for (UUID uuid : directPlayers) {
            String name = playerService.getNameByUuid(uuid).orElse("Unknown");
            boolean isDunced = dunceService.isDunced(uuid);
            Optional<java.sql.Timestamp> lastSeen = playerIPRepository.getLastSeenTimestamp(uuid);

            PlayerInfo info = new PlayerInfo(uuid, name, isDunced, lastSeen.orElse(null));

            if (isDunced) {
                duncedPlayers.add(info);
            } else {
                normalPlayers.add(info);
            }
        }

        // Combine all players for pagination
        List<PlayerInfo> allPlayers = new ArrayList<>();
        allPlayers.addAll(duncedPlayers);
        allPlayers.addAll(normalPlayers);

        int itemsPerPage = getItemsPerPage();
        int totalPages = (int) Math.ceil((double) allPlayers.size() / itemsPerPage);
        page = Math.min(page, totalPages);
        page = Math.max(1, page);

        int startIndex = (page - 1) * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, allPlayers.size());

        // Show dunced header if applicable
        if (!duncedPlayers.isEmpty()) {
            sender.sendMessage(messageManager.get("iplookup_dunced_header", String.valueOf(duncedPlayers.size())));
        }

        // Show other header if applicable
        if (!normalPlayers.isEmpty()) {
            sender.sendMessage(messageManager.get("iplookup_other_header", String.valueOf(normalPlayers.size())));
        }

        sender.sendMessage(Component.empty());

        // Display paginated players
        for (int i = startIndex; i < endIndex; i++) {
            PlayerInfo player = allPlayers.get(i);
            sendPlayerEntry(sender, player, player.isDunced());
        }

        // Pagination info and controls
        if (totalPages > 1) {
            sender.sendMessage(Component.empty());

            Component pagination = Component.empty();

            // Previous page button
            if (page > 1) {
                pagination = pagination.append(
                    messageManager.get("iplookup_page_prev")
                        .clickEvent(ClickEvent.runCommand("/dunceiplookup " + ipAddress + " " + (page - 1)))
                        .hoverEvent(HoverEvent.showText(messageManager.get("iplookup_page_prev_hover")))
                );
            }

            // Page info
            pagination = pagination.append(Component.text(" "))
                .append(messageManager.get("iplookup_page_info", String.valueOf(page), String.valueOf(totalPages)))
                .append(Component.text(" "));

            // Next page button
            if (page < totalPages) {
                pagination = pagination.append(
                    messageManager.get("iplookup_page_next")
                        .clickEvent(ClickEvent.runCommand("/dunceiplookup " + ipAddress + " " + (page + 1)))
                        .hoverEvent(HoverEvent.showText(messageManager.get("iplookup_page_next_hover")))
                );
            }

            sender.sendMessage(pagination);
        }

        // Historical links section (only on first page)
        if (page == 1) {
            sender.sendMessage(Component.empty());
            sender.sendMessage(messageManager.get("iplookup_historical_header"));

            // Get all historical links through the direct players
            Set<UUID> historicalLinks = new HashSet<>();
            Map<UUID, Set<String>> playerToSharedIPs = new HashMap<>();

            for (UUID directPlayer : directPlayers) {
                Set<UUID> linkedPlayers = playerIPRepository.getPlayersWithHistoricalIP(directPlayer);
                linkedPlayers.remove(directPlayer);
                linkedPlayers.removeAll(directPlayers);

                for (UUID linked : linkedPlayers) {
                    if (!historicalLinks.contains(linked)) {
                        historicalLinks.add(linked);
                        Set<String> sharedIPs = new HashSet<>();
                        for (UUID dp : directPlayers) {
                            sharedIPs.addAll(playerIPRepository.getSharedIPs(linked, dp));
                        }
                        playerToSharedIPs.put(linked, sharedIPs);
                    }
                }
            }

            if (historicalLinks.isEmpty()) {
                sender.sendMessage(messageManager.get("iplookup_no_historical"));
            } else {
                sender.sendMessage(messageManager.get("iplookup_historical_count", String.valueOf(historicalLinks.size())));

                int count = 0;
                int maxHistoricalDisplay = 5;
                for (UUID linkedUuid : historicalLinks) {
                    if (count >= maxHistoricalDisplay) {
                        sender.sendMessage(messageManager.get("iplookup_more_results", String.valueOf(historicalLinks.size() - maxHistoricalDisplay)));
                        break;
                    }

                    String name = playerService.getNameByUuid(linkedUuid).orElse("Unknown");
                    boolean isDunced = dunceService.isDunced(linkedUuid);
                    Set<String> sharedIPs = playerToSharedIPs.get(linkedUuid);

                    Component entry = Component.text("  - ")
                        .color(NamedTextColor.GRAY)
                        .append(Component.text(name)
                            .color(isDunced ? NamedTextColor.RED : NamedTextColor.WHITE)
                            .clickEvent(ClickEvent.runCommand("/duncealtlookup " + name))
                            .hoverEvent(HoverEvent.showText(messageManager.get("iplookup_click_altlookup"))));

                    if (isDunced) {
                        entry = entry.append(Component.text(" ")).append(messageManager.get("iplookup_dunced_tag"));
                    }

                    if (sharedIPs != null && !sharedIPs.isEmpty()) {
                        String ipList = sharedIPs.size() > 2
                            ? sharedIPs.iterator().next() + " +" + (sharedIPs.size() - 1) + " more"
                            : String.join(", ", sharedIPs);
                        entry = entry.append(Component.text(" ")).append(messageManager.get("iplookup_via_ips", ipList));
                    }

                    sender.sendMessage(entry);
                    count++;
                }
            }
        }

        // Footer with action hints
        sender.sendMessage(Component.empty());
        sender.sendMessage(
            messageManager.get("iplookup_action_ipdunce")
                .clickEvent(ClickEvent.suggestCommand("/ipdunce " + ipAddress + " "))
                .hoverEvent(HoverEvent.showText(messageManager.get("iplookup_click_dunce_all")))
                .append(Component.text(" "))
                .append(messageManager.get("iplookup_action_undunceip")
                    .clickEvent(ClickEvent.suggestCommand("/undunceip " + ipAddress))
                    .hoverEvent(HoverEvent.showText(messageManager.get("iplookup_click_undunce_all"))))
        );

        sender.sendMessage(messageManager.get("iplookup_footer"));
    }

    /**
     * Send a single player entry in the report
     */
    private void sendPlayerEntry(CommandSender sender, PlayerInfo player, boolean isDunced) {
        Component entry = Component.text("  - ")
            .color(NamedTextColor.GRAY)
            .append(Component.text(player.name)
                .color(isDunced ? NamedTextColor.RED : NamedTextColor.WHITE)
                .clickEvent(ClickEvent.runCommand("/duncelookup " + player.name))
                .hoverEvent(HoverEvent.showText(messageManager.get("iplookup_click_lookup"))));

        if (isDunced) {
            entry = entry.append(Component.text(" ")).append(messageManager.get("iplookup_dunced_tag"));
        }

        // Add last seen
        if (player.lastSeen != null) {
            entry = entry.append(Component.text(" ")).append(messageManager.get("iplookup_last_seen", DATE_FORMATTER.format(player.lastSeen)));
        }

        // Add IP count for this player
        List<String> playerIPs = dunceService.getPlayerIPs(player.uuid);
        if (playerIPs.size() > 1) {
            entry = entry.append(Component.text(" "))
                .append(messageManager.get("iplookup_ip_count", String.valueOf(playerIPs.size()))
                    .clickEvent(ClickEvent.runCommand("/duncealtlookup " + player.name))
                    .hoverEvent(HoverEvent.showText(messageManager.get("iplookup_click_altlookup"))));
        }

        sender.sendMessage(entry);
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("duncechat.admin")) {
            return Collections.emptyList();
        }

        // No tab completion for IP addresses - they need to type it manually
        return Collections.emptyList();
    }

    /**
     * Helper class to hold player info for display
     */
    private record PlayerInfo(UUID uuid, String name, boolean isDunced, java.sql.Timestamp lastSeen) {}
}
