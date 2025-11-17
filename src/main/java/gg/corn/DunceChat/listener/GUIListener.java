package gg.corn.DunceChat.listener;

import gg.corn.DunceChat.service.DunceService;
import gg.corn.DunceChat.service.PreferencesService;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

/**
 * Handles GUI inventory clicks
 */
public class GUIListener implements Listener {

    private final DunceService dunceService;
    private final PreferencesService preferencesService;
    private final NamespacedKey dunceVisibility;
    private final NamespacedKey talkingInDunceChat;

    public GUIListener(DunceService dunceService, PreferencesService preferencesService, Plugin plugin) {
        this.dunceService = dunceService;
        this.preferencesService = preferencesService;
        this.dunceVisibility = new NamespacedKey(plugin, "dunce-visibility");
        this.talkingInDunceChat = new NamespacedKey(plugin, "talking-in-dunce-chat");
    }

    @EventHandler
    public void onInventoryClick(@NotNull InventoryClickEvent event) {
        // Check if it's our GUI
        if (!event.getView().title().equals(Component.text("DunceChat Menu")) ||
            event.getView().getTopInventory().getHolder() != null) {
            return;
        }

        event.setCancelled(true);

        if (event.getCurrentItem() == null) {
            return;
        }

        ItemStack item = event.getCurrentItem();
        Player player = (Player) event.getWhoClicked();
        ItemMeta meta = item.getItemMeta();

        if (meta == null) {
            return;
        }

        // Check for visibility toggle
        if (meta.getPersistentDataContainer().has(dunceVisibility, PersistentDataType.STRING)) {
            preferencesService.toggleDunceChatVisibility(player.getUniqueId());
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
            // Refresh GUI
            refreshGUI(player);
        }
        // Check for chat toggle
        else if (meta.getPersistentDataContainer().has(talkingInDunceChat, PersistentDataType.STRING)) {
            preferencesService.toggleInDunceChat(player.getUniqueId());
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
            // Refresh GUI
            refreshGUI(player);
        }
    }

    private void refreshGUI(Player player) {
        // Close and reopen - the GUI command will handle this
        player.closeInventory();
        player.performCommand("duncemenu");
    }
}

