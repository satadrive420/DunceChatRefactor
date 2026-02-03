package gg.corn.DunceChat.command;

import gg.corn.DunceChat.model.AltDetectionResult;
import gg.corn.DunceChat.model.DunceRecord;
import gg.corn.DunceChat.service.DunceService;
import gg.corn.DunceChat.service.PlayerService;
import gg.corn.DunceChat.util.MessageManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Refactored lookup command using services
 */
public class LookupCommand implements CommandExecutor {

    private final DunceService dunceService;
    private final PlayerService playerService;
    private final MessageManager messageManager;
    private static final SimpleDateFormat DATE_FORMATTER = new SimpleDateFormat("yyyy/MM/dd HH:mm");

    public LookupCommand(DunceService dunceService, PlayerService playerService, MessageManager messageManager) {
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

        UUID uuid = playerUuid.get();
        Optional<DunceRecord> record = dunceService.getActiveDunceRecord(uuid);

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
        sender.sendMessage(Component.empty());
        sender.sendMessage(messageManager.get("lookup_header"));
        sender.sendMessage(messageManager.get("lookup_target", playerName));
        sender.sendMessage(messageManager.get("lookup_dunced_on", duncedDate));
        sender.sendMessage(messageManager.get("lookup_expires_on", expiryDate));
        sender.sendMessage(messageManager.get("lookup_marked_by", staffName));
        sender.sendMessage(messageManager.get("lookup_reason", reason));

        // Show trigger message if it exists (for auto-dunces)
        if (dunceRecord.getTriggerMessage() != null && !dunceRecord.getTriggerMessage().isEmpty()) {
            sender.sendMessage(messageManager.get("lookup_trigger_message", dunceRecord.getTriggerMessage()));
        }

        // Show IP information
        showIPInfo(sender, uuid, playerName);

        // Footer
        sender.sendMessage(messageManager.get("lookup_footer"));

        return true;
    }

    /**
     * Show IP-related information for the player
     */
    private void showIPInfo(CommandSender sender, UUID playerUuid, String playerName) {
        // Get current IP
        Optional<String> currentIP = dunceService.getPlayerCurrentIP(playerUuid);

        // Get all IPs
        List<String> allIPs = dunceService.getPlayerIPs(playerUuid);

        // Get alt detection
        Set<UUID> currentIPLinks = dunceService.getCurrentIPLinks(playerUuid);
        Set<UUID> historicalIPLinks = dunceService.getHistoricalIPLinks(playerUuid);

        // Remove self from links
        currentIPLinks.remove(playerUuid);
        historicalIPLinks.remove(playerUuid);
        historicalIPLinks.removeAll(currentIPLinks); // Remove duplicates

        // Send IP info header
        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text("--- IP Information ---")
            .color(NamedTextColor.GOLD));

        // Current IP
        if (currentIP.isPresent()) {
            sender.sendMessage(Component.text("Current IP: ")
                .color(NamedTextColor.GRAY)
                .append(Component.text(currentIP.get())
                    .color(NamedTextColor.WHITE)
                    .clickEvent(ClickEvent.copyToClipboard(currentIP.get()))
                    .hoverEvent(HoverEvent.showText(Component.text("Click to copy")))));
        }

        // Total IPs
        sender.sendMessage(Component.text("Known IPs: ")
            .color(NamedTextColor.GRAY)
            .append(Component.text(String.valueOf(allIPs.size()))
                .color(NamedTextColor.WHITE)));

        // Direct IP Links (current IP match)
        if (!currentIPLinks.isEmpty()) {
            Component directLinks = Component.text("[!] Direct Links: ")
                .color(NamedTextColor.RED)
                .append(Component.text(String.valueOf(currentIPLinks.size()))
                    .color(NamedTextColor.WHITE)
                    .decorate(TextDecoration.BOLD));

            // Add names
            List<String> directNames = new ArrayList<>();
            int count = 0;
            for (UUID linkUuid : currentIPLinks) {
                if (count >= 3) {
                    directNames.add("+" + (currentIPLinks.size() - 3) + " more");
                    break;
                }
                String name = playerService.getNameByUuid(linkUuid).orElse("Unknown");
                boolean isDunced = dunceService.isDunced(linkUuid);
                directNames.add(isDunced ? name + " [D]" : name);
                count++;
            }

            directLinks = directLinks.append(Component.text(" (" + String.join(", ", directNames) + ")")
                .color(NamedTextColor.GRAY));

            sender.sendMessage(directLinks);
        }

        // Historical IP Links
        if (!historicalIPLinks.isEmpty()) {
            Component histLinks = Component.text("  Historical Links: ")
                .color(NamedTextColor.YELLOW)
                .append(Component.text(String.valueOf(historicalIPLinks.size()))
                    .color(NamedTextColor.WHITE));

            sender.sendMessage(histLinks);
        }

        // Full lookup hint
        int totalLinks = currentIPLinks.size() + historicalIPLinks.size();
        if (totalLinks > 0) {
            sender.sendMessage(Component.text("Use ")
                .color(NamedTextColor.GRAY)
                .append(Component.text("/duncealtlookup " + playerName)
                    .color(NamedTextColor.AQUA)
                    .decorate(TextDecoration.UNDERLINED)
                    .clickEvent(ClickEvent.runCommand("/duncealtlookup " + playerName))
                    .hoverEvent(HoverEvent.showText(Component.text("Click to run"))))
                .append(Component.text(" for full alt analysis")
                    .color(NamedTextColor.GRAY)));
        }
    }
}

