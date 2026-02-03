package gg.corn.DunceChat.command;

import gg.corn.DunceChat.model.AltDetectionResult;
import gg.corn.DunceChat.service.DunceService;
import gg.corn.DunceChat.service.PlayerService;
import gg.corn.DunceChat.util.MessageManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
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
 * Command handler for /duncealtlookup (alias /duncealts)
 * Performs comprehensive alt account detection
 */
public class AltLookupCommand implements CommandExecutor, TabCompleter {

    private final DunceService dunceService;
    private final PlayerService playerService;
    private final MessageManager messageManager;
    private final FileConfiguration config;
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy/MM/dd HH:mm");
    private static final int DEFAULT_DEPTH = 2;

    public AltLookupCommand(DunceService dunceService, PlayerService playerService,
                           MessageManager messageManager, FileConfiguration config) {
        this.dunceService = dunceService;
        this.playerService = playerService;
        this.messageManager = messageManager;
        this.config = config;
    }

    private int getMaxDepth() {
        return Math.min(10, Math.max(1, config.getInt("ip-tracking.max-lookup-depth", 5)));
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                            @NotNull String label, @NotNull String[] args) {
        // Permission check
        if (!sender.hasPermission("duncechat.admin")) {
            sender.sendMessage(messageManager.get("no_permission"));
            return true;
        }

        // Usage: /duncealtlookup <player> [depth]
        if (args.length < 1) {
            sender.sendMessage(messageManager.get("usage_altlookup"));
            return true;
        }

        String targetName = args[0];
        int maxDepth = getMaxDepth();
        int depth = DEFAULT_DEPTH;

        if (args.length > 1) {
            try {
                depth = Integer.parseInt(args[1]);
                if (depth < 1 || depth > maxDepth) {
                    sender.sendMessage(messageManager.get("altlookup_invalid_depth", String.valueOf(maxDepth)));
                    return true;
                }
            } catch (NumberFormatException e) {
                sender.sendMessage(messageManager.get("altlookup_invalid_depth", String.valueOf(maxDepth)));
                return true;
            }
        }

        // Get target UUID
        UUID targetUuid = playerService.getUuidByName(targetName).orElse(null);
        if (targetUuid == null) {
            sender.sendMessage(messageManager.get("player_not_found", targetName));
            return true;
        }

        // Perform alt detection
        sender.sendMessage(messageManager.get("altlookup_scanning", targetName));

        final int finalDepth = depth;

        // Run async to avoid blocking main thread
        Bukkit.getScheduler().runTaskAsynchronously(
            Bukkit.getPluginManager().getPlugin("DunceChat"),
            () -> {
                AltDetectionResult result = dunceService.detectAlts(targetUuid, finalDepth);

                // Send results on main thread
                Bukkit.getScheduler().runTask(
                    Bukkit.getPluginManager().getPlugin("DunceChat"),
                    () -> displayResults(sender, result, finalDepth)
                );
            }
        );

        return true;
    }

    private void displayResults(CommandSender sender, AltDetectionResult result, int depth) {
        sender.sendMessage(Component.empty());

        // Header
        sender.sendMessage(messageManager.get("altlookup_header"));

        sender.sendMessage(messageManager.get("altlookup_target", result.getTargetPlayerName()));
        sender.sendMessage(messageManager.get("altlookup_depth", String.valueOf(depth)));

        // Player's IPs
        List<String> playerIPs = dunceService.getPlayerIPs(result.getTargetPlayer());
        sender.sendMessage(messageManager.get("altlookup_known_ips", String.valueOf(playerIPs.size())));

        Optional<String> currentIP = dunceService.getPlayerCurrentIP(result.getTargetPlayer());
        if (currentIP.isPresent()) {
            sender.sendMessage(messageManager.get("altlookup_current_ip", currentIP.get())
                .clickEvent(ClickEvent.copyToClipboard(currentIP.get()))
                .hoverEvent(HoverEvent.showText(messageManager.get("iplookup_click_copy"))));
        }

        sender.sendMessage(Component.empty());

        // Direct alts (current IP match)
        Set<AltDetectionResult.AltAccount> directAlts = result.getDirectAlts();
        sender.sendMessage(messageManager.get("altlookup_direct_alts", String.valueOf(directAlts.size())));

        if (directAlts.isEmpty()) {
            sender.sendMessage(messageManager.get("altlookup_none_detected"));
        } else {
            for (AltDetectionResult.AltAccount alt : directAlts) {
                displayAltAccount(sender, alt, result.getTargetPlayer());
            }
        }

        sender.sendMessage(Component.empty());

        // Historical alts
        Set<AltDetectionResult.AltAccount> historicalAlts = result.getHistoricalAlts();
        sender.sendMessage(messageManager.get("altlookup_historical_alts", String.valueOf(historicalAlts.size())));

        if (historicalAlts.isEmpty()) {
            sender.sendMessage(messageManager.get("altlookup_none_detected"));
        } else {
            for (AltDetectionResult.AltAccount alt : historicalAlts) {
                displayAltAccount(sender, alt, result.getTargetPlayer());
            }
        }

        sender.sendMessage(Component.empty());

        // Summary
        int totalAlts = result.getTotalAltCount();
        NamedTextColor summaryColor = totalAlts == 0 ? NamedTextColor.GREEN :
                                      (directAlts.isEmpty() ? NamedTextColor.YELLOW : NamedTextColor.RED);

        sender.sendMessage(Component.text()
            .append(messageManager.get("altlookup_total_alts", ""))
            .append(Component.text(String.valueOf(totalAlts))
                .color(summaryColor)
                .decorate(TextDecoration.BOLD))
            .build());

        sender.sendMessage(messageManager.get("altlookup_footer"));
    }

    private void displayAltAccount(CommandSender sender, AltDetectionResult.AltAccount alt, UUID targetUuid) {
        // Check if this player is currently dunced
        boolean isDunced = dunceService.isDunced(alt.getPlayerUuid());

        // Build the alt display line
        Component line = Component.text("  - ")
            .color(NamedTextColor.GRAY);

        // Player name with dunce indicator
        Component nameComponent = Component.text(alt.getPlayerName())
            .color(isDunced ? NamedTextColor.RED : NamedTextColor.WHITE)
            .clickEvent(ClickEvent.runCommand("/duncelookup " + alt.getPlayerName()))
            .hoverEvent(HoverEvent.showText(messageManager.get("iplookup_click_lookup")));

        if (isDunced) {
            nameComponent = nameComponent.append(Component.text(" "))
                .append(messageManager.get("iplookup_dunced_tag"));
        }

        line = line.append(nameComponent);

        // Shared IPs info
        if (!alt.getSharedIPs().isEmpty()) {
            String sharedIPText = alt.getSharedIPs().size() > 2
                ? alt.getSharedIPs().iterator().next() + " +" + (alt.getSharedIPs().size() - 1) + " more"
                : String.join(", ", alt.getSharedIPs());
            line = line.append(Component.text(" "))
                .append(messageManager.get("iplookup_via_ips", sharedIPText));
        }

        sender.sendMessage(line);
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("duncechat.admin")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            // Complete player names
            return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        } else if (args.length == 2) {
            // Complete depth options
            List<String> depths = new ArrayList<>();
            int maxDepth = getMaxDepth();
            for (int i = 1; i <= maxDepth; i++) {
                depths.add(String.valueOf(i));
            }
            return depths.stream()
                .filter(d -> d.startsWith(args[1]))
                .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }
}
