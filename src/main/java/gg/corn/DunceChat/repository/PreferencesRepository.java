package gg.corn.DunceChat.repository;

import gg.corn.DunceChat.database.DatabaseManager;
import gg.corn.DunceChat.model.PlayerPreferences;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for player preferences data access
 */
public class PreferencesRepository {

    private final DatabaseManager databaseManager;
    private final boolean defaultVisibility;

    public PreferencesRepository(DatabaseManager databaseManager, boolean defaultVisibility) {
        this.databaseManager = databaseManager;
        this.defaultVisibility = defaultVisibility;
    }

    /**
     * Get player preferences
     */
    public PlayerPreferences getPreferences(UUID playerUuid) {
        String query = "SELECT * FROM player_preferences WHERE player_uuid = ?";

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setString(1, playerUuid.toString());
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return new PlayerPreferences(
                    playerUuid,
                    rs.getBoolean("dunce_chat_visible"),
                    rs.getBoolean("in_dunce_chat")
                );
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        // Return default preferences if not found
        return new PlayerPreferences(playerUuid, defaultVisibility, false);
    }

    /**
     * Set dunce chat visibility
     */
    public void setDunceChatVisible(UUID playerUuid, boolean visible) {
        String query = """
            INSERT INTO player_preferences (player_uuid, dunce_chat_visible)
            VALUES (?, ?)
            ON DUPLICATE KEY UPDATE dunce_chat_visible = VALUES(dunce_chat_visible)
        """;

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setString(1, playerUuid.toString());
            stmt.setBoolean(2, visible);
            stmt.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Set in dunce chat status
     */
    public void setInDunceChat(UUID playerUuid, boolean inDunceChat) {
        String query = """
            INSERT INTO player_preferences (player_uuid, in_dunce_chat)
            VALUES (?, ?)
            ON DUPLICATE KEY UPDATE in_dunce_chat = VALUES(in_dunce_chat)
        """;

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setString(1, playerUuid.toString());
            stmt.setBoolean(2, inDunceChat);
            stmt.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Save complete preferences
     */
    public void save(PlayerPreferences preferences) {
        String query = """
            INSERT INTO player_preferences (player_uuid, dunce_chat_visible, in_dunce_chat)
            VALUES (?, ?, ?)
            ON DUPLICATE KEY UPDATE
                dunce_chat_visible = VALUES(dunce_chat_visible),
                in_dunce_chat = VALUES(in_dunce_chat)
        """;

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setString(1, preferences.getPlayerUuid().toString());
            stmt.setBoolean(2, preferences.isDunceChatVisible());
            stmt.setBoolean(3, preferences.isInDunceChat());
            stmt.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}

