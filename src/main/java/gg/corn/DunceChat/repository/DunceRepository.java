package gg.corn.DunceChat.repository;

import gg.corn.DunceChat.database.DatabaseManager;
import gg.corn.DunceChat.model.DunceRecord;

import java.sql.*;
import java.util.*;

/**
 * Repository for dunce record data access
 */
public class DunceRepository {

    private final DatabaseManager databaseManager;

    public DunceRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    /**
     * Get the active dunce record for a player
     */
    public Optional<DunceRecord> getActiveDunceRecord(UUID playerUuid) {
        String query = "SELECT * FROM dunce_records WHERE player_uuid = ? AND is_dunced = TRUE ORDER BY id DESC LIMIT 1";

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setString(1, playerUuid.toString());
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return Optional.of(mapResultSetToDunceRecord(rs));
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return Optional.empty();
    }

    /**
     * Get all active dunced players
     */
    public Set<UUID> getAllActiveDuncedPlayers() {
        Set<UUID> duncedPlayers = new HashSet<>();
        String query = "SELECT DISTINCT player_uuid FROM dunce_records WHERE is_dunced = TRUE";

        try (Connection conn = databaseManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {

            while (rs.next()) {
                duncedPlayers.add(UUID.fromString(rs.getString("player_uuid")));
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return duncedPlayers;
    }

    /**
     * Get all expired dunce records
     */
    public List<DunceRecord> getExpiredDunceRecords() {
        List<DunceRecord> expiredRecords = new ArrayList<>();
        String query = "SELECT * FROM dunce_records WHERE is_dunced = TRUE AND expires_at IS NOT NULL AND expires_at < NOW()";

        try (Connection conn = databaseManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {

            while (rs.next()) {
                expiredRecords.add(mapResultSetToDunceRecord(rs));
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return expiredRecords;
    }

    /**
     * Create a new dunce record
     */
    public DunceRecord create(DunceRecord record) {
        String query = """
            INSERT INTO dunce_records (player_uuid, is_dunced, reason, staff_uuid, dunced_at, expires_at)
            VALUES (?, ?, ?, ?, ?, ?)
        """;

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setString(1, record.getPlayerUuid().toString());
            stmt.setBoolean(2, record.isDunced());
            stmt.setString(3, record.getReason());
            stmt.setString(4, record.getStaffUuid() != null ? record.getStaffUuid().toString() : null);
            stmt.setTimestamp(5, record.getDuncedAt());
            stmt.setTimestamp(6, record.getExpiresAt());

            stmt.executeUpdate();

            ResultSet generatedKeys = stmt.getGeneratedKeys();
            if (generatedKeys.next()) {
                record.setId(generatedKeys.getInt(1));
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return record;
    }

    /**
     * Update a dunce record
     */
    public void update(DunceRecord record) {
        String query = """
            UPDATE dunce_records
            SET is_dunced = ?, reason = ?, staff_uuid = ?, expires_at = ?, undunced_at = ?
            WHERE id = ?
        """;

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setBoolean(1, record.isDunced());
            stmt.setString(2, record.getReason());
            stmt.setString(3, record.getStaffUuid() != null ? record.getStaffUuid().toString() : null);
            stmt.setTimestamp(4, record.getExpiresAt());
            stmt.setTimestamp(5, record.getUnduncedAt());
            stmt.setInt(6, record.getId());

            stmt.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Undunce a player by marking the active record as inactive
     */
    public void undunce(UUID playerUuid) {
        String query = """
            UPDATE dunce_records
            SET is_dunced = FALSE, undunced_at = NOW()
            WHERE player_uuid = ? AND is_dunced = TRUE
        """;

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setString(1, playerUuid.toString());
            stmt.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Get dunce history for a player
     */
    public List<DunceRecord> getDunceHistory(UUID playerUuid) {
        List<DunceRecord> history = new ArrayList<>();
        String query = "SELECT * FROM dunce_records WHERE player_uuid = ? ORDER BY dunced_at DESC";

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setString(1, playerUuid.toString());
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                history.add(mapResultSetToDunceRecord(rs));
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return history;
    }

    /**
     * Map ResultSet to DunceRecord
     */
    private DunceRecord mapResultSetToDunceRecord(ResultSet rs) throws SQLException {
        UUID playerUuid = UUID.fromString(rs.getString("player_uuid"));
        String staffUuidStr = rs.getString("staff_uuid");
        UUID staffUuid = staffUuidStr != null ? UUID.fromString(staffUuidStr) : null;

        return new DunceRecord(
            rs.getInt("id"),
            playerUuid,
            rs.getBoolean("is_dunced"),
            rs.getString("reason"),
            staffUuid,
            rs.getTimestamp("dunced_at"),
            rs.getTimestamp("expires_at"),
            rs.getTimestamp("undunced_at")
        );
    }
}

