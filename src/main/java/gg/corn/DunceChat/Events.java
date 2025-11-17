package gg.corn.DunceChat;

import java.util.*;
import java.util.logging.Logger;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.apache.commons.lang3.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import me.clip.placeholderapi.PlaceholderAPI;


public class Events implements Listener {

    private NBTHandler nbtHandler;
    public static DunceChat plugin = gg.corn.DunceChat.DunceChat.getPlugin(DunceChat.class);
    private static final Logger logger = Bukkit.getLogger();
    public static MySQLHandler mySQLHandler;
    private List<String> disallowedWords;
    NamespacedKey dunceVisibility = new NamespacedKey(plugin, "dunce-visibility");
    NamespacedKey talkingInDunceChat = new NamespacedKey(plugin, "talking-in-dunce-chat");
    NamespacedKey page = new NamespacedKey(plugin, "page");

    public Events() {
        loadDisallowedWords();
    }


    @EventHandler
    public void processCommand(@NotNull PlayerCommandPreprocessEvent event) {
        if (!event.getMessage().contains(" ") || !gg.corn.DunceChat.DunceChat.isDunced(event.getPlayer()))
            return;

        List<String> list = Lists.newArrayList("whisper", "w", "msg", "r", "reply");
        String message = event.getMessage();
        String command = message.substring(1, message.contains(" ") ? message.indexOf(" ") : message.length())
                .toLowerCase();
        String recipient = message.substring(message.indexOf(" ") + 1, StringUtils.ordinalIndexOf(message, " ", 2) != -1
                ? StringUtils.ordinalIndexOf(message, " ", 2) : message.length() - 1);
        UUID uuid = mySQLHandler.getUUIDByName(recipient);

        if (list.contains(command) && uuid != null && !gg.corn.DunceChat.DunceChat.dunceVisible(uuid)) {
            event.getPlayer().sendMessage(gg.corn.DunceChat.DunceChat.baseColor()
                    + "You have been dunced, and the player you're trying to message has unmoderated chat disabled.");
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void clickDunceMenu(@NotNull InventoryClickEvent event) {

        if (event.getView().title().equals(Component.text("DunceChat Menu")) && event.getView().getTopInventory().getHolder() == null) {
            event.setCancelled(true);

            if (event.getCurrentItem() == null)
                return;

            ItemStack item = event.getCurrentItem();
            Player player = (Player) event.getWhoClicked();

            if (NBTHandler.hasString(item, dunceVisibility)) {
                gg.corn.DunceChat.DunceChat.setDunceChatVisible(player, !gg.corn.DunceChat.DunceChat.dunceVisible(player));
                DunceGUI.constructDunceGUI(player);
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
            } else if (NBTHandler.hasString(item, talkingInDunceChat)) {
                gg.corn.DunceChat.DunceChat.setInDunceChat(player, !gg.corn.DunceChat.DunceChat.getInDunceChat(player));
                DunceGUI.constructDunceGUI(player);
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
            }
        }
    }

    @EventHandler
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
        // Check if the command is '/me'
        if (event.getMessage().startsWith("/me")) {
            // Check if the player is dunced
            if (gg.corn.DunceChat.DunceChat.isDunced(event.getPlayer())) {
                // Cancel the command and possibly notify the player
                event.setCancelled(true);
                event.getPlayer().sendMessage(Component.text("You are dunced and cannot use this command.", NamedTextColor.RED));
            }
        }
    }


    @EventHandler
    public void playerJoin(@NotNull PlayerJoinEvent event) {
        UserData.updateUser(event.getPlayer(), true, false);
    }

    @EventHandler
    public void playerQuit(@NotNull PlayerQuitEvent event) {
        UserData.updateUser(event.getPlayer(), false, true);
    }

    @EventHandler
    public void playerTalk(@NotNull AsyncPlayerChatEvent event) {

        Player player = event.getPlayer();
        String playerName = getDisplayName(player);
        String prefix = getPrefix(player);

        Component duncedPrefix = Component.text("[", NamedTextColor.DARK_GRAY)
                .append(Component.text(plugin.getConfig().getString("dunced-prefix"), NamedTextColor.GOLD))
                .append(Component.text("]", NamedTextColor.DARK_GRAY));

        Component unmoderatedPrefix = Component.text("[", NamedTextColor.DARK_GRAY)
                .append(Component.text(plugin.getConfig().getString("unmoderated-chat-prefix"), NamedTextColor.GOLD))
                .append(Component.text("]", NamedTextColor.DARK_GRAY));

        if (gg.corn.DunceChat.DunceChat.isDunced(event.getPlayer())) {
            event.getRecipients().retainAll(gg.corn.DunceChat.DunceChat.getPlayersDunceChatVisible());
            event.setCancelled(true);

            Component message = Component.text("<")
                    .append(duncedPrefix)
                    .append(LegacyComponentSerializer.legacySection().deserialize(prefix))
                    .append(LegacyComponentSerializer.legacySection().deserialize(playerName))
                    .append(Component.text("> "))
                    .append(Component.text(event.getMessage()));

            for (Player recipient : event.getRecipients())
                recipient.sendMessage(message);

            logger.info(LegacyComponentSerializer.legacySection().serialize(message));
        } else if (gg.corn.DunceChat.DunceChat.getInDunceChat(event.getPlayer())) {
            Set<Player> set = Sets.newHashSet(event.getPlayer());
            set.addAll(gg.corn.DunceChat.DunceChat.getPlayersDunceChatVisible());

            event.getRecipients().retainAll(set);
            event.setCancelled(true);

            Component message = Component.text("<")
                    .append(unmoderatedPrefix)
                    .append(LegacyComponentSerializer.legacySection().deserialize(prefix))
                    .append(LegacyComponentSerializer.legacySection().deserialize(playerName))
                    .append(Component.text("> "))
                    .append(Component.text(event.getMessage()));

            for (Player recipient : event.getRecipients())
                recipient.sendMessage(message);

            logger.info(LegacyComponentSerializer.legacySection().serialize(message));
        }

        if (plugin.getConfig().getBoolean("dunceStar"))
            if (!gg.corn.DunceChat.DunceChat.isDunced(event.getPlayer()) && gg.corn.DunceChat.DunceChat.dunceVisible(event.getPlayer()))
                event.setFormat(event.getFormat().replace("%1$s", "%1$s" + "§a*§r"));

    }

    public String getDisplayName(Player player) {
        String papiDisplayName = plugin.getConfig().getString("display-name-placeholder");
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            String placeholderName = PlaceholderAPI.setPlaceholders(player, papiDisplayName);
            return placeholderName;
        } else {
            return player.getName();
        }
    }
    public String getPrefix(Player player) {
        String papiPrefix = plugin.getConfig().getString("prefix-placeholder");
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            String placeholderName = PlaceholderAPI.setPlaceholders(player, papiPrefix);
            return placeholderName;
        } else {
            return "";
        }
    }

    private void loadDisallowedWords() {
        disallowedWords = plugin.getWordsConfig().getStringList("disallowed-words");
    }

    @EventHandler
    public void onAsyncPlayerChat(AsyncPlayerChatEvent event) {
        String message = event.getMessage().toLowerCase();

        if (event.getPlayer().hasPermission("duncechat.admin")) {
            // Player has the bypass permission, do not check for disallowed words
            return;
        }

        if (gg.corn.DunceChat.DunceChat.getInDunceChat(event.getPlayer())){
            //player is in unmoderated chat already
            return;
        }

        for (String word : disallowedWords) {
            if (message.contains(word.toLowerCase())) {
                UUID uuid = event.getPlayer().getUniqueId();
                String player = MySQLHandler.getNameByUUID(uuid);

                if(!gg.corn.DunceChat.DunceChat.isDunced(event.getPlayer())) {
                    //gg.corn.DunceChat.DunceChat.setDunced(uuid, !DunceChat.isDunced(uuid), reason, new UUID(0,0), null);
                    DunceChat.setDunced(uuid,true,"AutoDunced",null,null);
                    //DunceCommand.tryDunce(player,reason,Bukkit.getConsoleSender());
                    //this is being stupid so i will directly manipulate the database here.
                    MySQLHandler.setDunceReason(uuid, "AutoDunced");
                }

                // You can optionally cancel the event to prevent the message from being sent
                event.setCancelled(true);

                // Notify the player or take other actions as needed
                break;
            }
        }
    }
}