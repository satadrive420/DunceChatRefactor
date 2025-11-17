package gg.corn.DunceChat.repository;

import gg.corn.DunceChat.database.DatabaseManager;
import gg.corn.DunceChat.model.Player;

import java.sql.*;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for player data access
 */
public class PlayerRepository {

    private final DatabaseManager databaseManager;

    public PlayerRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    /**
     * Find a player by UUID
     */
    public Optional<Player> findByUuid(UUID uuid) {
        String query = "SELECT * FROM players WHERE uuid = ?";

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                Player player = new Player(uuid, rs.getString("username"));
                player.setFirstJoin(rs.getTimestamp("first_join"));
                player.setLastJoin(rs.getTimestamp("last_join"));
                player.setLastQuit(rs.getTimestamp("last_quit"));
                return Optional.of(player);
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return Optional.empty();
    }

    /**
     * Find a player by username
     */
    public Optional<Player> findByUsername(String username) {
        String query = "SELECT * FROM players WHERE username = ? ORDER BY last_join DESC LIMIT 1";

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                UUID uuid = UUID.fromString(rs.getString("uuid"));
                Player player = new Player(uuid, username);
                player.setFirstJoin(rs.getTimestamp("first_join"));
                player.setLastJoin(rs.getTimestamp("last_join"));
                player.setLastQuit(rs.getTimestamp("last_quit"));
                return Optional.of(player);
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return Optional.empty();
    }

    /**
     * Save or update a player
     */
    public void save(Player player) {
        String query = """
            INSERT INTO players (uuid, username, first_join, last_join, last_quit)
            VALUES (?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                username = VALUES(username),
                last_join = VALUES(last_join),
                last_quit = VALUES(last_quit)
        """;

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setString(1, player.getUuid().toString());
            stmt.setString(2, player.getUsername());
            stmt.setTimestamp(3, player.getFirstJoin());
            stmt.setTimestamp(4, player.getLastJoin());
            stmt.setTimestamp(5, player.getLastQuit());

            stmt.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Update player join time
     */
    public void updateJoinTime(UUID uuid, Timestamp joinTime) {
        String query = "UPDATE players SET last_join = ? WHERE uuid = ?";

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setTimestamp(1, joinTime);
            stmt.setString(2, uuid.toString());
            stmt.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Update player quit time
     */
    public void updateQuitTime(UUID uuid, Timestamp quitTime) {
        String query = "UPDATE players SET last_quit = ? WHERE uuid = ?";

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setTimestamp(1, quitTime);
            stmt.setString(2, uuid.toString());
            stmt.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}

