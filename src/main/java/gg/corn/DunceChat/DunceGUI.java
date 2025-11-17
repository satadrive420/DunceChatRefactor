package gg.corn.DunceChat;

import com.google.common.collect.Lists;

import org.bukkit.ChatColor;
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
        Inventory inventory = player.getServer().createInventory(null, 9, "DunceChat Menu");


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

        List<String> lore = Lists.newArrayList(
                DunceChat.baseColor() + "Dunced on: " + DunceChat.highlightColor() +
                        date);

        lore.add(DunceChat.baseColor() + "Expires on: " + DunceChat.highlightColor() + Objects.requireNonNullElse(expiry, "permanent"));
        if (staffName != null){
            lore.add(gg.corn.DunceChat.DunceChat.baseColor() + "Marked by: " + gg.corn.DunceChat.DunceChat.highlightColor() + staffName);
        }
        if (reason != null)
            lore.add(ChatColor.GRAY + "Reason: " + gg.corn.DunceChat.DunceChat.highlightColor() + reason);

        assert meta != null;
        meta.setDisplayName(ChatColor.GREEN + "You are dunced!");
        meta.setLore(lore);
        item.setItemMeta(meta);

        return item;
    }

    public static @NotNull ItemStack itemMessageVisibility(Player player) {
        boolean visible = gg.corn.DunceChat.DunceChat.dunceVisible(player);
        ItemStack item = new ItemStack(visible ? Material.ORANGE_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE,
                1);
        ItemMeta meta = item.getItemMeta();

        assert meta != null;
        meta.setDisplayName(visible ? ChatColor.GREEN + "Dunce Chat is visible" : ChatColor.RED + "Dunce Chat is hidden");
        meta.setLore(Lists.newArrayList(
                ChatColor.GRAY + "Click to " + (visible ? "hide" : "unhide") + " the Dunce Chat."));
        item.setItemMeta(meta);

        return NBTHandler.addString(item, dunceVisibility, "tag");
    }

    public static @NotNull ItemStack itemModeratedChat(Player player) {
        boolean inDunceChat = gg.corn.DunceChat.DunceChat.getInDunceChat(player);
        ItemStack item = new ItemStack(inDunceChat ? Material.ORANGE_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE,
                1);
        ItemMeta meta = item.getItemMeta();
        assert meta != null;
        meta.setDisplayName(
                inDunceChat ? ChatColor.GREEN + "Speaking in Dunce Chat" : ChatColor.RED + "Speaking in Public Chat");
        meta.setLore(
                Lists.newArrayList(ChatColor.GRAY + "Click to switch to " + (inDunceChat ? "Public" : "Dunce") + " chat."));
        item.setItemMeta(meta);

        return NBTHandler.addString(item, talkingInDunceChat, "tag");
    }
}