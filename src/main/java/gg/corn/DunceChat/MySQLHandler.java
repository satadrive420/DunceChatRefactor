package gg.corn.DunceChat;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MySQLHandler {

    private static String url;
    private static  String username;
    private static String password;

    public MySQLHandler(String host, int port, String database, String username, String password) {
        url = "jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false&allowPublicKeyRetrieval=true";
        MySQLHandler.username = username;
        MySQLHandler.password = password;
        testConnection();
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url, username, password);
    }


    private void testConnection() {
        try (Connection connection = getConnection()) {
            // Connection successful
            Bukkit.getLogger().info("[DunceChat] MySQL connection successful!");
        } catch (SQLException e) {
            // Connection failed
            Bukkit.getLogger().warning("[DunceChat] Failed to establish MySQL connection:");
            e.printStackTrace();
            Bukkit.getPluginManager().disablePlugin((Plugin) this);
        }
    }

    public static UUID getUUIDByName(String name) {
        UUID uuid = null;
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT uuid FROM users WHERE display_name = ?")) {
            statement.setString(1, name);
            ResultSet resultSet = statement.executeQuery();
            if (resultSet.next()) {
                uuid = UUID.fromString(resultSet.getString("uuid"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return uuid;
    }

    public static String getNameByUUID(UUID uuid) {
        String name = null;
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT display_name FROM users WHERE uuid = ?")) {
            statement.setString(1, uuid.toString());
            ResultSet resultSet = statement.executeQuery();
            if (resultSet.next()) {
                name = resultSet.getString("display_name");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return name;
    }


    public static String getWhoDunced(UUID uuid) {
        String whoDunced = null;
        try (Connection connection = MySQLHandler.getConnection()) {
            String query = "SELECT staff_uuid FROM dunced_players WHERE uuid = ?";
            PreparedStatement preparedStatement = connection.prepareStatement(query);
            preparedStatement.setString(1, uuid.toString());
            ResultSet resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) {
                whoDunced = resultSet.getString("staff_uuid");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        if(whoDunced == null){
            return "Console";
        }else

        return whoDunced;
    }

    public static String getDunceReason(UUID uuid) {
        String reason = null;
        try (Connection connection = MySQLHandler.getConnection()) {
            String query = "SELECT reason FROM dunced_players WHERE uuid = ?";
            PreparedStatement preparedStatement = connection.prepareStatement(query);
            preparedStatement.setString(1, uuid.toString());
            ResultSet resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) {
                reason = resultSet.getString("reason");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return reason;
    }

    public static String getDunceExpiry(UUID uuid) {
        String expiry = null;
        try (Connection connection = MySQLHandler.getConnection()) {
            String query = "SELECT expiry FROM dunced_players WHERE uuid = ?";
            PreparedStatement preparedStatement = connection.prepareStatement(query);
            preparedStatement.setString(1, uuid.toString());
            ResultSet resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) {
                expiry = resultSet.getString("expiry");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        if(expiry == null){
            return "never";
        }else

        return expiry;
    }



    public static String getDunceDate(UUID uuid) {
        String date = null;
        try (Connection connection = MySQLHandler.getConnection()) {
            String query = "SELECT date FROM dunced_players WHERE uuid = ?";
            PreparedStatement preparedStatement = connection.prepareStatement(query);
            preparedStatement.setString(1, uuid.toString());
            ResultSet resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) {
                date = resultSet.getString("date");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return date;
    }
    public static void setDunceReason(UUID uuid, String reason) {
        try (Connection connection = MySQLHandler.getConnection()) {
            String query = "INSERT INTO dunced_players (uuid, reason) VALUES (?, ?) ON DUPLICATE KEY UPDATE reason = ?";
            PreparedStatement preparedStatement = connection.prepareStatement(query);
            preparedStatement.setString(1, uuid.toString());
            preparedStatement.setString(2, reason);
            preparedStatement.setString(3, reason);
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void addDuncedPlayer(UUID uuid, boolean dunced, String reason, UUID staffUUID, Timestamp expiry) {
        try (Connection connection = getConnection()) {
            String query = "INSERT INTO dunced_players (uuid, dunced, reason, staff_uuid, date, expiry) " +
                    "VALUES (?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE dunced = ?, reason = ?, staff_uuid = ?, date = ?, expiry = ?";
            PreparedStatement preparedStatement = connection.prepareStatement(query);
            preparedStatement.setString(1, uuid.toString());
            preparedStatement.setBoolean(2, dunced);
            preparedStatement.setString(3, reason);
            preparedStatement.setString(4, staffUUID == null ? null : staffUUID.toString());
            preparedStatement.setTimestamp(5, new java.sql.Timestamp(new java.util.Date().getTime()));
            preparedStatement.setTimestamp(6, expiry);
            preparedStatement.setBoolean(7, dunced);
            preparedStatement.setString(8, reason);
            preparedStatement.setString(9, staffUUID == null ? null : staffUUID.toString());
            preparedStatement.setTimestamp(10, new java.sql.Timestamp(new java.util.Date().getTime()));
            preparedStatement.setTimestamp(11, expiry);

            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }



}