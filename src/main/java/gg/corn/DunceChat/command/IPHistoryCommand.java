package gg.corn.DunceChat.command;

import gg.corn.DunceChat.repository.PlayerIPRepository;
import gg.corn.DunceChat.service.DunceService;
import gg.corn.DunceChat.service.PlayerService;
import gg.corn.DunceChat.util.MessageManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Command to view a player's IP address history
 * Usage: /dunceiphistory <player> [page]
 */
public class IPHistoryCommand implements CommandExecutor, TabCompleter {

    private final DunceService dunceService;
    private final PlayerService playerService;
    private final MessageManager messageManager;
    private final PlayerIPRepository playerIPRepository;
    private final FileConfiguration config;
    private static final SimpleDateFormat DATE_FORMATTER = new SimpleDateFormat("yyyy/MM/dd HH:mm");
    private static final int DEFAULT_ITEMS_PER_PAGE = 10;

    public IPHistoryCommand(DunceService dunceService, PlayerService playerService,
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
            sender.sendMessage(messageManager.get("usage_iphistory"));
            return true;
        }

        String playerName = args[0];
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

        // Get player UUID
        UUID playerUuid = playerService.getUuidByName(playerName).orElse(null);
        if (playerUuid == null) {
            sender.sendMessage(messageManager.get("player_not_found", playerName));
            return true;
        }

        // Get IP history
        List<PlayerIPRepository.IPRecord> ipHistory = playerIPRepository.getDetailedIPsByPlayer(playerUuid);

        if (ipHistory.isEmpty()) {
            sender.sendMessage(messageManager.get("iphistory_no_records", playerName));
            return true;
        }

        // Sort by last seen (most recent first)
        ipHistory.sort((a, b) -> b.lastSeen().compareTo(a.lastSeen()));

        // Send paginated report
        sendIPHistoryReport(sender, playerName, playerUuid, ipHistory, page);

        return true;
    }

    /**
     * Send a detailed IP history report with pagination
     */
    private void sendIPHistoryReport(CommandSender sender, String playerName, UUID playerUuid,
                                     List<PlayerIPRepository.IPRecord> ipHistory, int page) {
        // Header
        sender.sendMessage(Component.empty());
        sender.sendMessage(messageManager.get("iphistory_header"));
        sender.sendMessage(messageManager.get("iphistory_player", playerName));
        sender.sendMessage(messageManager.get("iphistory_total_ips", String.valueOf(ipHistory.size())));

        // Get current IP
        Optional<String> currentIP = dunceService.getPlayerCurrentIP(playerUuid);
        if (currentIP.isPresent()) {
            sender.sendMessage(messageManager.get("iphistory_current_ip", currentIP.get())
                .clickEvent(ClickEvent.copyToClipboard(currentIP.get()))
                .hoverEvent(HoverEvent.showText(messageManager.get("iplookup_click_copy"))));
        }

        sender.sendMessage(Component.empty());

        // Pagination
        int itemsPerPage = getItemsPerPage();
        int totalPages = (int) Math.ceil((double) ipHistory.size() / itemsPerPage);
        page = Math.min(page, totalPages);
        page = Math.max(1, page);

        int startIndex = (page - 1) * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, ipHistory.size());

        // Display IP entries
        for (int i = startIndex; i < endIndex; i++) {
            PlayerIPRepository.IPRecord record = ipHistory.get(i);
            sendIPEntry(sender, record, playerName, currentIP.orElse(null));
        }

        // Pagination controls
        if (totalPages > 1) {
            sender.sendMessage(Component.empty());

            Component pagination = Component.empty();

            // Previous page button
            if (page > 1) {
                pagination = pagination.append(
                    messageManager.get("iplookup_page_prev")
                        .clickEvent(ClickEvent.runCommand("/dunceiphistory " + playerName + " " + (page - 1)))
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
                        .clickEvent(ClickEvent.runCommand("/dunceiphistory " + playerName + " " + (page + 1)))
                        .hoverEvent(HoverEvent.showText(messageManager.get("iplookup_page_next_hover")))
                );
            }

            sender.sendMessage(pagination);
        }

        // Footer
        sender.sendMessage(Component.empty());
        sender.sendMessage(messageManager.get("iphistory_footer"));
    }

    /**
     * Send a single IP entry in the report
     */
    private void sendIPEntry(CommandSender sender, PlayerIPRepository.IPRecord record,
                             String playerName, String currentIP) {
        String ipAddress = record.ipAddress();
        boolean isCurrent = ipAddress.equals(currentIP);

        // Build hover text with first/last seen info
        Component hoverText = messageManager.get("iphistory_click_lookup");
        if (record.firstSeen() != null || record.lastSeen() != null) {
            hoverText = Component.empty();
            if (record.firstSeen() != null) {
                hoverText = hoverText.append(messageManager.get("iphistory_first_seen", DATE_FORMATTER.format(record.firstSeen())));
            }
            if (record.lastSeen() != null) {
                if (record.firstSeen() != null) {
                    hoverText = hoverText.append(Component.newline());
                }
                hoverText = hoverText.append(messageManager.get("iphistory_last_seen", DATE_FORMATTER.format(record.lastSeen())));
            }
            hoverText = hoverText.append(Component.newline()).append(messageManager.get("iphistory_click_lookup"));
        }

        // Build the entry
        Component entry = Component.text("  - ")
            .color(NamedTextColor.GRAY)
            .append(Component.text(ipAddress)
                .color(isCurrent ? NamedTextColor.GREEN : NamedTextColor.WHITE)
                .clickEvent(ClickEvent.runCommand("/dunceiplookup " + ipAddress))
                .hoverEvent(HoverEvent.showText(hoverText)));

        // Current IP indicator
        if (isCurrent) {
            entry = entry.append(Component.text(" "))
                .append(messageManager.get("iphistory_current_tag"));
        }


        // Other players on this IP
        Set<UUID> otherPlayers = playerIPRepository.getPlayersByIP(ipAddress);
        otherPlayers.removeIf(uuid -> playerService.getNameByUuid(uuid).orElse("").equalsIgnoreCase(playerName));

        if (!otherPlayers.isEmpty()) {
            int otherCount = otherPlayers.size();
            entry = entry.append(Component.text(" "))
                .append(messageManager.get("iphistory_shared_count", String.valueOf(otherCount))
                    .clickEvent(ClickEvent.runCommand("/dunceiplookup " + ipAddress))
                    .hoverEvent(HoverEvent.showText(messageManager.get("iphistory_click_view_shared"))));
        }

        sender.sendMessage(entry);
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
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

