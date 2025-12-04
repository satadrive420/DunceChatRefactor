package gg.corn.DunceChat.repository;

import gg.corn.DunceChat.database.DatabaseManager;

import java.sql.*;
import java.util.*;
import java.util.logging.Logger;

/**
 * Repository for pending message data access
 */
public class PendingMessageRepository {

    private final DatabaseManager databaseManager;
    private static final Logger logger = Logger.getLogger("DunceChat");

    public PendingMessageRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    /**
     * Add a pending message for a player
     */
    public void addPendingMessage(UUID playerUuid, String messageKey) {
        String query = "INSERT INTO pending_messages (player_uuid, message_key) VALUES (?, ?)";

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setString(1, playerUuid.toString());
            stmt.setString(2, messageKey);

            stmt.executeUpdate();

        } catch (SQLException e) {
            logger.severe("[DunceChat] Failed to add pending message!");
            logger.severe("[DunceChat] SQL Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Get all pending messages for a player
     */
    public List<String> getPendingMessages(UUID playerUuid) {
        List<String> messages = new ArrayList<>();
        String query = "SELECT message_key FROM pending_messages WHERE player_uuid = ? ORDER BY created_at ASC";

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setString(1, playerUuid.toString());

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    messages.add(rs.getString("message_key"));
                }
            }

        } catch (SQLException e) {
            logger.severe("[DunceChat] Failed to get pending messages!");
            logger.severe("[DunceChat] SQL Error: " + e.getMessage());
            e.printStackTrace();
        }

        return messages;
    }

    /**
     * Delete all pending messages for a player
     */
    public void deletePendingMessages(UUID playerUuid) {
        String query = "DELETE FROM pending_messages WHERE player_uuid = ?";

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setString(1, playerUuid.toString());
            stmt.executeUpdate();

        } catch (SQLException e) {
            logger.severe("[DunceChat] Failed to delete pending messages!");
            logger.severe("[DunceChat] SQL Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

