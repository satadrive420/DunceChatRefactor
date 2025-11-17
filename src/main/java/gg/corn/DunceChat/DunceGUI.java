package gg.corn.DunceChat;

import com.google.common.collect.Lists;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;


import java.util.*;

import static gg.corn.DunceChat.DunceCommand.plugin;


public class DunceGUI implements CommandExecutor {

    private NBTHandler nbtHandler;
    static NamespacedKey dunceVisibility = new NamespacedKey(plugin, "dunce-visibility");
    static NamespacedKey talkingInDunceChat = new NamespacedKey(plugin, "talking-in-dunce-chat");


    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] strings) {

        if (label.equalsIgnoreCase("duncemenu")) {
            if (sender instanceof Player) {

                constructDunceGUI((Player) sender);
            }
        }

        return true;
    }



    public static void constructDunceGUI(Player player) {
        Inventory inventory = player.getServer().createInventory(null, 9, Component.text("DunceChat Menu"));


        if (!gg.corn.DunceChat.DunceChat.isDunced(player)) {
            inventory.addItem(itemMessageVisibility(player));
            inventory.addItem(itemModeratedChat(player));
        } else
            inventory.addItem(itemDunceInfo(player));

        player.openInventory(inventory);
    }

    public static @NotNull ItemStack itemDunceInfo(Player player) {
        String staffName =  MySQLHandler.getNameByUUID(UUID.fromString(MySQLHandler.getWhoDunced(player.getUniqueId())));
        String date = MySQLHandler.getDunceDate(player.getUniqueId());
        String reason = MySQLHandler.getDunceReason(player.getUniqueId());
        String expiry = MySQLHandler.getDunceExpiry(player.getUniqueId());

        ItemStack item = new ItemStack(Material.GREEN_STAINED_GLASS_PANE, 1);
        ItemMeta meta = item.getItemMeta();

        assert meta != null;

        List<Component> lore = Lists.newArrayList(
                Component.text("Dunced on: ", NamedTextColor.GRAY)
                        .append(Component.text(date, NamedTextColor.GOLD)));

        lore.add(Component.text("Expires on: ", NamedTextColor.GRAY)
                .append(Component.text(expiry != null ? expiry : "never", NamedTextColor.GOLD)));
        if (staffName != null){
            lore.add(Component.text("Marked by: ", NamedTextColor.GRAY)
                    .append(Component.text(staffName, NamedTextColor.GOLD)));
        }
        if (reason != null)
            lore.add(Component.text("Reason: ", NamedTextColor.GRAY)
                    .append(Component.text(reason, NamedTextColor.GOLD)));

        meta.displayName(Component.text("You are dunced!", NamedTextColor.GREEN));
        meta.lore(lore);
        item.setItemMeta(meta);

        return item;
    }

    public static @NotNull ItemStack itemMessageVisibility(Player player) {
        boolean visible = gg.corn.DunceChat.DunceChat.dunceVisible(player);
        ItemStack item = new ItemStack(visible ? Material.ORANGE_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE,
                1);
        ItemMeta meta = item.getItemMeta();

        assert meta != null;
        meta.displayName(Component.text("Dunce Chat is " + (visible ? "visible" : "hidden"),
                visible ? NamedTextColor.GREEN : NamedTextColor.RED));
        meta.lore(Lists.newArrayList(
                Component.text("Click to " + (visible ? "hide" : "unhide") + " the Dunce Chat.", NamedTextColor.GRAY)));
        item.setItemMeta(meta);

        return NBTHandler.addString(item, dunceVisibility, "tag");
    }

    public static @NotNull ItemStack itemModeratedChat(Player player) {
        boolean inDunceChat = gg.corn.DunceChat.DunceChat.getInDunceChat(player);
        ItemStack item = new ItemStack(inDunceChat ? Material.ORANGE_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE,
                1);
        ItemMeta meta = item.getItemMeta();
        assert meta != null;
        meta.displayName(Component.text(inDunceChat ? "Speaking in Dunce Chat" : "Speaking in Public Chat",
                inDunceChat ? NamedTextColor.GREEN : NamedTextColor.RED));
        meta.lore(Lists.newArrayList(Component.text("Click to switch to " + (inDunceChat ? "Public" : "Dunce") + " chat.", NamedTextColor.GRAY)));
        item.setItemMeta(meta);

        return NBTHandler.addString(item, talkingInDunceChat, "tag");
    }
}