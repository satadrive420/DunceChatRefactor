package gg.corn.DunceChat;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.*;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

import com.google.common.collect.Sets;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import static gg.corn.DunceChat.DunceCommand.unduncePlayer;

public class DunceChat extends JavaPlugin implements Listener {

    public static DunceChat plugin;
    private NBTHandler nbtHandler;


    private FileConfiguration wordsConfig;

    public void onEnable() {
        plugin = gg.corn.DunceChat.DunceChat.getPlugin(DunceChat.class);

        getServer().getPluginManager().registerEvents(new Events(), this);
        getServer().getPluginManager().registerEvents(new Greentexter(), this);

        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        loadConfig();
        loadWordsConfig();
        setupMySQL();
        createTables();
        scheduleUndunceTask();

        Objects.requireNonNull(getServer().getPluginCommand("dunce")).setExecutor(new DunceCommand());
        Objects.requireNonNull(getServer().getPluginCommand("undunce")).setExecutor(new DunceCommand());
        Objects.requireNonNull(getServer().getPluginCommand("dc")).setExecutor(new DunceCommand());
        Objects.requireNonNull(getServer().getPluginCommand("dcon")).setExecutor(new DunceToggle());
        Objects.requireNonNull(getServer().getPluginCommand("dcoff")).setExecutor(new DunceToggle());
        Objects.requireNonNull(getServer().getPluginCommand("duncemenu")).setExecutor(new DunceGUI());
        Objects.requireNonNull(getServer().getPluginCommand("clearchat")).setExecutor(new ClearChat());
        Objects.requireNonNull(getServer().getPluginCommand("duncereload")).setExecutor(new ReloadCommand());
        Objects.requireNonNull(getServer().getPluginCommand("duncelookup")).setExecutor(new DunceLookupCommand());
    }

    static SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy/MM/dd HH:mm");


    private void setupMySQL() {
        String host = getConfig().getString("mysql.host");
        int port = getConfig().getInt("mysql.port");
        String database = getConfig().getString("mysql.database");
        String username = getConfig().getString("mysql.username");
        String password = getConfig().getString("mysql.password");

        new MySQLHandler(host, port, database, username, password);
    }
    private void createTables() {
        try (Connection connection = MySQLHandler.getConnection()) {
            String query = "CREATE TABLE IF NOT EXISTS dunced_players (" +
                    "uuid VARCHAR(36) PRIMARY KEY," +
                    "dunced BOOLEAN," +
                    "reason TEXT," +
                    "staff_uuid VARCHAR(36)," +
                    "date DATETIME," +
                    "expiry_date DATETIME" +
                    ");";
            PreparedStatement preparedStatement = connection.prepareStatement(query);
            preparedStatement.executeUpdate();

            query = "CREATE TABLE IF NOT EXISTS dunce_visible (" +
                    "uuid VARCHAR(36) PRIMARY KEY," +
                    "visible BOOLEAN" +
                    ");";
            preparedStatement = connection.prepareStatement(query);
            preparedStatement.executeUpdate();

            query = "CREATE TABLE IF NOT EXISTS dunce_chat (" +
                    "uuid VARCHAR(36) PRIMARY KEY," +
                    "in_chat BOOLEAN" +
                    ");";
            preparedStatement = connection.prepareStatement(query);
            preparedStatement.executeUpdate();
            query = "CREATE TABLE IF NOT EXISTS users (" +
                    "uuid VARCHAR(36) PRIMARY KEY," +
                    "display_name TEXT," +
                    "last_login DATETIME," +
                    "last_logout DATETIME" +
                    ");";
            preparedStatement = connection.prepareStatement(query);
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    public static Set<String> getDunced() {
        Set<String> duncedSet = Sets.newHashSet();
        try (Connection connection = MySQLHandler.getConnection()) {
            String query = "SELECT uuid FROM dunced_players WHERE dunced = true";
            PreparedStatement preparedStatement = connection.prepareStatement(query);
            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                duncedSet.add(resultSet.getString("uuid"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return duncedSet;
    }


    public static @NotNull Set<Player> getPlayersDunceChatVisible() {
        Set<Player> players = new HashSet<>();

        for (Player player : Bukkit.getOnlinePlayers())
            if (dunceVisible(player))
                players.add(player);

        return players;
    }

    public static void setDunced(UUID uuid, boolean dunced, String reason, UUID staffUUID, Timestamp expiry) {

        Set<String> dunceSet = getDunced();
        OfflinePlayer player = plugin.getServer().getOfflinePlayer(uuid);
        Player staff = staffUUID == null ? null : plugin.getServer().getPlayer(staffUUID);

        setDunceChatVisible(uuid, true);
        setInDunceChat(uuid, true);


        if (dunced) {
            dunceSet.add(player.getUniqueId().toString());

            for (Player online : Bukkit.getOnlinePlayers()) {
                Component message;

                if (player.getPlayer() != null && online.equals(player.getPlayer())) {
                    message = Component.text("You have been dunced by ", baseColor())
                            .append(Component.text(staff == null ? "CONSOLE" : staff.getName(), highlightColor()));

                    if (reason != null) {
                        message = message.append(Component.text(" for \"", baseColor()))
                                .append(Component.text(reason, highlightColor()))
                                .append(Component.text("\"", baseColor()));
                    }

                    message = message.append(Component.text(". Only players with Dunce Chat visible can see your messages. Expires on: ", baseColor()))
                            .append(Component.text(expiry == null ? "never" : dateFormatter.format(expiry), highlightColor()));
                } else {
                    message = Component.text(player.getName(), highlightColor())
                            .append(Component.text(" has been dunced by ", baseColor()))
                            .append(Component.text(staff == null ? "CONSOLE" : staff.getName(), highlightColor()));

                    if (reason != null) {
                        message = message.append(Component.text(" for \"", baseColor()))
                                .append(Component.text(reason, highlightColor()))
                                .append(Component.text("\"", baseColor()));
                    }

                    message = message.append(Component.text(". You can show the unmoderated chat by using ", baseColor()))
                            .append(Component.text("/dcon", highlightColor()))
                            .append(Component.text(".", baseColor()));
                }

                online.sendMessage(message);
            }
        } else {
            dunceSet.remove(player.getUniqueId().toString());
            setInDunceChat(player.getUniqueId(), false);
            for (Player online : Bukkit.getOnlinePlayers()) {
                Component message = Component.text(staff.getName(), highlightColor())
                        .append(Component.text(" has undunced ", baseColor()))
                        .append(Component.text(player.getName(), highlightColor()));

                online.sendMessage(message);
            }
        }

        MySQLHandler.addDuncedPlayer(uuid,dunced,reason,staffUUID,expiry);

    }

    public static boolean isDunced(@NotNull Player player) {
        return isDunced(player.getUniqueId());
    }

    public static boolean isDunced(UUID uuid) {
        boolean dunced = false;
        try (Connection connection = MySQLHandler.getConnection()) {
            String query = "SELECT dunced FROM dunced_players WHERE uuid = ?";
            PreparedStatement preparedStatement = connection.prepareStatement(query);
            preparedStatement.setString(1, uuid.toString());
            ResultSet resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) {
                dunced = resultSet.getBoolean("dunced");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return dunced;
    }

    public static void setDunceChatVisible(@NotNull Player player, boolean visible) {
        setDunceChatVisible(player.getUniqueId(), visible);
    }

    public static void setDunceChatVisible(UUID uuid, boolean visible) {
        try (Connection connection = MySQLHandler.getConnection()) {
            String query = "INSERT INTO dunce_visible (uuid, visible) VALUES (?, ?) ON DUPLICATE KEY UPDATE visible = ?";
            PreparedStatement preparedStatement = connection.prepareStatement(query);
            preparedStatement.setString(1, uuid.toString());
            preparedStatement.setBoolean(2, visible);
            preparedStatement.setBoolean(3, visible);
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static boolean dunceVisible(@NotNull Player player) {
        return dunceVisible(player.getUniqueId());
    }

    public static boolean dunceVisible(UUID uuid) {
        boolean visible = plugin.getConfig().getBoolean("visible-by-default");
        try (Connection connection = MySQLHandler.getConnection()) {
            String query = "SELECT visible FROM dunce_visible WHERE uuid = ?";
            PreparedStatement preparedStatement = connection.prepareStatement(query);
            preparedStatement.setString(1, uuid.toString());
            ResultSet resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) {
                visible = resultSet.getBoolean("visible");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return visible;
    }


    public static void setInDunceChat(@NotNull Player player, boolean value) {
        setInDunceChat(player.getUniqueId(), value);
    }

    public static void setInDunceChat(UUID uuid, boolean value) {
        try (Connection connection = MySQLHandler.getConnection()) {
            String query = "INSERT INTO dunce_chat (uuid, in_chat) VALUES (?, ?) ON DUPLICATE KEY UPDATE in_chat = ?";
            PreparedStatement preparedStatement = connection.prepareStatement(query);
            preparedStatement.setString(1, uuid.toString());
            preparedStatement.setBoolean(2, value);
            preparedStatement.setBoolean(3, value);
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static boolean getInDunceChat(Player player) {
        boolean inChat = false;
        try (Connection connection = MySQLHandler.getConnection()) {
            String query = "SELECT in_chat FROM dunce_chat WHERE uuid = ?";
            PreparedStatement preparedStatement = connection.prepareStatement(query);
            preparedStatement.setString(1, player.getUniqueId().toString());
            ResultSet resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) {
                inChat = resultSet.getBoolean("in_chat");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return inChat;
    }

    public static TextColor baseColor() {
        try {
            String colorName = plugin.getConfig().getString("baseColor");
            return NamedTextColor.NAMES.value(colorName.toLowerCase());
        } catch (Exception e) {
            return NamedTextColor.GRAY;
        }
    }

    public static TextColor highlightColor() {
        try {
            String colorName = plugin.getConfig().getString("highlightColor");
            return NamedTextColor.NAMES.value(colorName.toLowerCase());
        } catch (Exception e) {
            return NamedTextColor.GOLD;
        }
    }

    public void loadConfig() {
        getConfig().options().copyDefaults(true);
        saveConfig();
    }

    public void loadWordsConfig() {
        File wordsFile = new File(getDataFolder(), "words.yml");
        if (!wordsFile.exists()) {
            try (InputStream in = getResource("words.yml")) {
                assert in != null;
                Files.copy(in, wordsFile.toPath());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        wordsConfig = YamlConfiguration.loadConfiguration(wordsFile);
    }

    public FileConfiguration getWordsConfig() {
        if (wordsConfig == null) {
            loadWordsConfig();
        }
        return wordsConfig;
    }

    public static Timestamp getDuncedExpiry(UUID uuid) {

        try (Connection connection = MySQLHandler.getConnection()) {
            String query = "SELECT expiry FROM dunced_players WHERE uuid = ?";
            PreparedStatement preparedStatement = connection.prepareStatement(query);
            preparedStatement.setString(1, uuid.toString());
            ResultSet resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) {
                return resultSet.getTimestamp("expiry");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    private final HashMap<UUID, BukkitTask> dunceTasks = new HashMap<>();


    public void scheduleUndunceTask() {
        // Get the Bukkit scheduler
        BukkitScheduler scheduler = getServer().getScheduler();


        // Schedule a task to run every second
        scheduler.runTaskTimerAsynchronously(this, () -> {
            // For every dunced player
            for (String uuid : DunceChat.getDunced()) {
                Timestamp expiry = DunceChat.getDuncedExpiry(UUID.fromString(uuid));
                // If the player is dunced and the expiry timestamp is reached
                if (expiry != null && new Timestamp(System.currentTimeMillis()).after(expiry)) {
                    String player = MySQLHandler.getNameByUUID(UUID.fromString(uuid));
                    // Undunce the player
                    unduncePlayer(player,Bukkit.getConsoleSender());

                }
            }
        }, 0L, 20L);  // 20 ticks == 1 second
    }

}

