package gg.corn.DunceChat.gui;

import gg.corn.DunceChat.model.DunceRecord;
import gg.corn.DunceChat.service.DunceService;
import gg.corn.DunceChat.service.PlayerService;
import gg.corn.DunceChat.service.PreferencesService;
import gg.corn.DunceChat.util.MessageManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Builds the DunceChat GUI
 */
public class DunceGUIBuilder {

    private final DunceService dunceService;
    private final PlayerService playerService;
    private final PreferencesService preferencesService;
    private final MessageManager messageManager;
    private final NamespacedKey dunceVisibility;
    private final NamespacedKey talkingInDunceChat;
    private static final SimpleDateFormat DATE_FORMATTER = new SimpleDateFormat("yyyy/MM/dd HH:mm");

    public DunceGUIBuilder(DunceService dunceService, PlayerService playerService,
                          PreferencesService preferencesService, MessageManager messageManager, Plugin plugin) {
        this.dunceService = dunceService;
        this.playerService = playerService;
        this.preferencesService = preferencesService;
        this.messageManager = messageManager;
        this.dunceVisibility = new NamespacedKey(plugin, "dunce-visibility");
        this.talkingInDunceChat = new NamespacedKey(plugin, "talking-in-dunce-chat");
    }

    public void openGUI(Player player) {
        Inventory inventory = player.getServer().createInventory(
            null,
            9,
            Component.text(messageManager.getRaw("gui_title"))
        );

        if (dunceService.isDunced(player.getUniqueId())) {
            // Dunced players only see dunce info
            inventory.addItem(createDunceInfoItem(player));
        } else {
            // Non-dunced players see: visibility toggle and chat toggle
            inventory.addItem(createVisibilityToggleItem(player));
            inventory.addItem(createChatToggleItem(player));
        }

        player.openInventory(inventory);
    }

    private ItemStack createDunceInfoItem(Player player) {
        Optional<DunceRecord> recordOpt = dunceService.getActiveDunceRecord(player.getUniqueId());

        if (recordOpt.isEmpty()) {
            return new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        }

        DunceRecord record = recordOpt.get();
        ItemStack item = new ItemStack(Material.GREEN_STAINED_GLASS_PANE, 1);
        ItemMeta meta = item.getItemMeta();

        List<Component> lore = new ArrayList<>();

        String duncedDate = record.getDuncedAt() != null ?
            DATE_FORMATTER.format(record.getDuncedAt()) : "Unknown";
        lore.add(messageManager.get("gui_dunced_on", duncedDate));

        String expiryDate = record.getExpiresAt() != null ?
            DATE_FORMATTER.format(record.getExpiresAt()) : messageManager.getRaw("dunce_expires_never");
        lore.add(messageManager.get("gui_expires_on", expiryDate));

        // Show who dunced the player (CONSOLE if staffUuid is null)
        String staffName = record.getStaffUuid() != null ?
            playerService.getNameByUuid(record.getStaffUuid()).orElse("Unknown") : "CONSOLE";
        lore.add(messageManager.get("gui_marked_by", staffName));

        if (record.getReason() != null) {
            lore.add(messageManager.get("gui_reason", record.getReason()));
        }

        // Show trigger message if it exists (for auto-dunces)
        if (record.getTriggerMessage() != null && !record.getTriggerMessage().isEmpty()) {
            lore.add(messageManager.get("gui_trigger_message", record.getTriggerMessage()));
        }

        meta.displayName(messageManager.get("gui_dunced_title"));
        meta.lore(lore);
        item.setItemMeta(meta);

        return item;
    }

    private ItemStack createVisibilityToggleItem(Player player) {
        boolean visible = preferencesService.isDunceChatVisible(player.getUniqueId());
        boolean isDunced = dunceService.isDunced(player.getUniqueId());

        Material material = visible ? Material.ORANGE_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE;
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();

        String titleKey = visible ? "gui_chat_visible" : "gui_chat_hidden";
        String loreKey;

        if (isDunced) {
            // Dunced players always have visibility on and cannot toggle it
            loreKey = "gui_chat_visible_lore_dunced";
        } else {
            loreKey = visible ? "gui_chat_visible_lore" : "gui_chat_hidden_lore";
        }

        meta.displayName(messageManager.get(titleKey));
        meta.lore(List.of(messageManager.get(loreKey)));

        // Add NBT tag
        meta.getPersistentDataContainer().set(dunceVisibility, PersistentDataType.STRING, "tag");

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createChatToggleItem(Player player) {
        boolean inDunceChat = preferencesService.isInDunceChat(player.getUniqueId());

        Material material = inDunceChat ? Material.ORANGE_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE;
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();

        String titleKey = inDunceChat ? "gui_speaking_dunce" : "gui_speaking_public";
        String loreKey = inDunceChat ? "gui_speaking_dunce_lore" : "gui_speaking_public_lore";

        meta.displayName(messageManager.get(titleKey));
        meta.lore(List.of(messageManager.get(loreKey)));

        // Add NBT tag
        meta.getPersistentDataContainer().set(talkingInDunceChat, PersistentDataType.STRING, "tag");

        item.setItemMeta(meta);
        return item;
    }
}

