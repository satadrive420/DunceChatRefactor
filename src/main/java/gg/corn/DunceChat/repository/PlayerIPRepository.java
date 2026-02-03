package gg.corn.DunceChat.repository;

import gg.corn.DunceChat.database.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * Repository for managing player IP address associations
 */
public class PlayerIPRepository {

    private final DatabaseManager databaseManager;

    public PlayerIPRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    /**
     * Log or update a player's IP address
     * Updates last_seen if the association already exists
     */
    public void logPlayerIP(UUID playerUuid, String ipAddress) {
        String sql = """
            INSERT INTO player_ip_log (player_uuid, ip_address, first_seen, last_seen)
            VALUES (?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            ON DUPLICATE KEY UPDATE last_seen = CURRENT_TIMESTAMP
        """;

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, playerUuid.toString());
            stmt.setString(2, ipAddress);
            stmt.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Failed to log player IP", e);
        }
    }

    /**
     * Delete all IP history for a player
     * This removes all player-to-IP associations, effectively unlinking them from alt detection
     * @return the number of records deleted
     */
    public int deletePlayerIPHistory(UUID playerUuid) {
        String sql = "DELETE FROM player_ip_log WHERE player_uuid = ?";

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, playerUuid.toString());
            return stmt.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete player IP history", e);
        }
    }

    /**
     * Get all player UUIDs that currently share an IP address with the given player
     */
    public Set<UUID> getPlayersWithCurrentIP(UUID playerUuid) {
        Set<UUID> players = new HashSet<>();

        String sql = """
            SELECT DISTINCT pil2.player_uuid
            FROM player_ip_log pil1
            JOIN player_ip_log pil2 ON pil1.ip_address = pil2.ip_address
            WHERE pil1.player_uuid = ?
            AND pil2.player_uuid != ?
        """;

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, playerUuid.toString());
            stmt.setString(2, playerUuid.toString());

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    players.add(UUID.fromString(rs.getString("player_uuid")));
                }
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed to get players with current IP", e);
        }

        return players;
    }

    /**
     * Get all player UUIDs that share ANY IP address with the given player (historical)
     */
    public Set<UUID> getPlayersWithHistoricalIP(UUID playerUuid) {
        Set<UUID> players = new HashSet<>();

        String sql = """
            SELECT DISTINCT pil2.player_uuid
            FROM player_ip_log pil1
            JOIN player_ip_log pil2 ON pil1.ip_address = pil2.ip_address
            WHERE pil1.player_uuid = ?
            AND pil2.player_uuid != ?
        """;

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, playerUuid.toString());
            stmt.setString(2, playerUuid.toString());

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    players.add(UUID.fromString(rs.getString("player_uuid")));
                }
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed to get players with historical IP", e);
        }

        return players;
    }

    /**
     * Get all UUIDs associated with a specific IP address
     */
    public Set<UUID> getPlayersByIP(String ipAddress) {
        Set<UUID> players = new HashSet<>();

        String sql = """
            SELECT player_uuid
            FROM player_ip_log
            WHERE ip_address = ?
        """;

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, ipAddress);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    players.add(UUID.fromString(rs.getString("player_uuid")));
                }
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed to get players by IP", e);
        }

        return players;
    }

    /**
     * Get all IP addresses associated with a player UUID
     */
    public List<String> getIPsByPlayer(UUID playerUuid) {
        List<String> ips = new ArrayList<>();

        String sql = """
            SELECT ip_address, first_seen, last_seen
            FROM player_ip_log
            WHERE player_uuid = ?
            ORDER BY last_seen DESC
        """;

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, playerUuid.toString());

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    ips.add(rs.getString("ip_address"));
                }
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed to get IPs by player", e);
        }

        return ips;
    }

    /**
     * Get the most recent IP address for a player
     */
    public Optional<String> getCurrentIP(UUID playerUuid) {
        String sql = """
            SELECT ip_address
            FROM player_ip_log
            WHERE player_uuid = ?
            ORDER BY last_seen DESC
            LIMIT 1
        """;

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, playerUuid.toString());

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(rs.getString("ip_address"));
                }
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed to get current IP", e);
        }

        return Optional.empty();
    }

    /**
     * Get detailed IP records for a player (includes timestamps)
     */
    public List<IPRecord> getDetailedIPsByPlayer(UUID playerUuid) {
        List<IPRecord> records = new ArrayList<>();

        String sql = """
            SELECT ip_address, first_seen, last_seen
            FROM player_ip_log
            WHERE player_uuid = ?
            ORDER BY last_seen DESC
        """;

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, playerUuid.toString());

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    records.add(new IPRecord(
                        rs.getString("ip_address"),
                        rs.getTimestamp("first_seen"),
                        rs.getTimestamp("last_seen")
                    ));
                }
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed to get detailed IPs by player", e);
        }

        return records;
    }

    /**
     * Comprehensive alt detection: Find all players connected through shared IPs
     * Uses recursive-style detection to find chains of alt accounts
     */
    public Map<UUID, Set<String>> findAllConnectedPlayers(UUID startPlayerUuid, int maxDepth) {
        Map<UUID, Set<String>> connectedPlayers = new HashMap<>();
        Set<UUID> processedPlayers = new HashSet<>();
        Set<String> processedIPs = new HashSet<>();
        Queue<UUID> playersToProcess = new LinkedList<>();

        playersToProcess.add(startPlayerUuid);
        int currentDepth = 0;

        while (!playersToProcess.isEmpty() && currentDepth < maxDepth) {
            int levelSize = playersToProcess.size();

            for (int i = 0; i < levelSize; i++) {
                UUID currentPlayer = playersToProcess.poll();
                if (currentPlayer == null || processedPlayers.contains(currentPlayer)) {
                    continue;
                }
                processedPlayers.add(currentPlayer);

                // Get all IPs for this player
                List<String> playerIPs = getIPsByPlayer(currentPlayer);

                for (String ip : playerIPs) {
                    if (processedIPs.contains(ip)) {
                        continue;
                    }
                    processedIPs.add(ip);

                    // Get all players who have used this IP
                    Set<UUID> playersOnIP = getPlayersByIP(ip);

                    for (UUID linkedPlayer : playersOnIP) {
                        if (!linkedPlayer.equals(startPlayerUuid)) {
                            connectedPlayers.computeIfAbsent(linkedPlayer, k -> new HashSet<>()).add(ip);

                            if (!processedPlayers.contains(linkedPlayer)) {
                                playersToProcess.add(linkedPlayer);
                            }
                        }
                    }
                }
            }
            currentDepth++;
        }

        return connectedPlayers;
    }

    /**
     * Get the last seen timestamp for a player across all their IPs
     */
    public Optional<java.sql.Timestamp> getLastSeenTimestamp(UUID playerUuid) {
        String sql = """
            SELECT MAX(last_seen) as last_seen
            FROM player_ip_log
            WHERE player_uuid = ?
        """;

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, playerUuid.toString());

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.ofNullable(rs.getTimestamp("last_seen"));
                }
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed to get last seen timestamp", e);
        }

        return Optional.empty();
    }

    /**
     * Check if two players share any IP addresses
     */
    public Set<String> getSharedIPs(UUID player1, UUID player2) {
        Set<String> sharedIPs = new HashSet<>();

        String sql = """
            SELECT pil1.ip_address
            FROM player_ip_log pil1
            JOIN player_ip_log pil2 ON pil1.ip_address = pil2.ip_address
            WHERE pil1.player_uuid = ? AND pil2.player_uuid = ?
        """;

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, player1.toString());
            stmt.setString(2, player2.toString());

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    sharedIPs.add(rs.getString("ip_address"));
                }
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed to get shared IPs", e);
        }

        return sharedIPs;
    }

    /**
     * Get IP count for a player
     */
    public int getIPCount(UUID playerUuid) {
        String sql = """
            SELECT COUNT(*) as count
            FROM player_ip_log
            WHERE player_uuid = ?
        """;

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, playerUuid.toString());

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("count");
                }
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed to get IP count", e);
        }

        return 0;
    }

    /**
     * Record for detailed IP information
     */
    public record IPRecord(String ipAddress, java.sql.Timestamp firstSeen, java.sql.Timestamp lastSeen) {}
}

