package gg.corn.DunceChat.database;

import org.bukkit.Bukkit;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Handles database schema initialization and migrations
 */
public class SchemaManager {

    private final DatabaseManager databaseManager;
    private static final int CURRENT_SCHEMA_VERSION = 1;

    public SchemaManager(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    /**
     * Initialize the new schema
     */
    public void initializeSchema() {
        try (Connection conn = databaseManager.getConnection();
             Statement stmt = conn.createStatement()) {

            // Schema version tracking table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS schema_version (
                    version INT PRIMARY KEY,
                    applied_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """);

            // Players table - central player information
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS players (
                    uuid VARCHAR(36) PRIMARY KEY,
                    username VARCHAR(16) NOT NULL,
                    first_join TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    last_join TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    last_quit TIMESTAMP NULL,
                    INDEX idx_username (username)
                )
            """);

            // Dunce records table - tracks all dunce actions
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS dunce_records (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    player_uuid VARCHAR(36) NOT NULL,
                    is_dunced BOOLEAN NOT NULL DEFAULT FALSE,
                    reason TEXT,
                    staff_uuid VARCHAR(36),
                    dunced_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    expires_at TIMESTAMP NULL,
                    undunced_at TIMESTAMP NULL,
                    INDEX idx_player (player_uuid),
                    INDEX idx_active (player_uuid, is_dunced),
                    INDEX idx_expiry (expires_at),
                    FOREIGN KEY (player_uuid) REFERENCES players(uuid) ON DELETE CASCADE
                )
            """);

            // Player preferences table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS player_preferences (
                    player_uuid VARCHAR(36) PRIMARY KEY,
                    dunce_chat_visible BOOLEAN DEFAULT FALSE,
                    in_dunce_chat BOOLEAN DEFAULT FALSE,
                    FOREIGN KEY (player_uuid) REFERENCES players(uuid) ON DELETE CASCADE
                )
            """);

            Bukkit.getLogger().info("[DunceChat] Database schema initialized successfully!");
            updateSchemaVersion(CURRENT_SCHEMA_VERSION);

        } catch (SQLException e) {
            Bukkit.getLogger().severe("[DunceChat] Failed to initialize database schema!");
            e.printStackTrace();
        }
    }

    /**
     * Migrate from old schema to new schema
     */
    public boolean migrateFromOldSchema() {
        Bukkit.getLogger().info("[DunceChat] Starting migration from old schema...");

        try (Connection conn = databaseManager.getConnection();
             Statement stmt = conn.createStatement()) {

            // Check if old tables exist
            if (!tableExists(conn, "users") && !tableExists(conn, "dunced_players")) {
                Bukkit.getLogger().info("[DunceChat] No old schema detected, skipping migration.");
                return true;
            }

            // Migrate users to players table
            if (tableExists(conn, "users")) {
                stmt.execute("""
                    INSERT INTO players (uuid, username, first_join, last_join, last_quit)
                    SELECT uuid, display_name, 
                           COALESCE(last_login, CURRENT_TIMESTAMP),
                           COALESCE(last_login, CURRENT_TIMESTAMP),
                           last_logout
                    FROM users
                    ON DUPLICATE KEY UPDATE
                        username = VALUES(username),
                        last_join = COALESCE(VALUES(last_join), last_join),
                        last_quit = VALUES(last_quit)
                """);
                Bukkit.getLogger().info("[DunceChat] Migrated user data to new schema.");
            }

            // Migrate dunce records
            if (tableExists(conn, "dunced_players")) {
                stmt.execute("""
                    INSERT INTO dunce_records (player_uuid, is_dunced, reason, staff_uuid, dunced_at, expires_at)
                    SELECT uuid, dunced, reason, staff_uuid, 
                           COALESCE(date, CURRENT_TIMESTAMP),
                           expiry
                    FROM dunced_players
                    WHERE dunced = TRUE
                """);
                Bukkit.getLogger().info("[DunceChat] Migrated dunce records to new schema.");
            }

            // Migrate preferences
            if (tableExists(conn, "dunce_visible")) {
                stmt.execute("""
                    INSERT INTO player_preferences (player_uuid, dunce_chat_visible)
                    SELECT uuid, visible FROM dunce_visible
                    ON DUPLICATE KEY UPDATE dunce_chat_visible = VALUES(dunce_chat_visible)
                """);
            }

            if (tableExists(conn, "dunce_chat")) {
                stmt.execute("""
                    INSERT INTO player_preferences (player_uuid, in_dunce_chat)
                    SELECT uuid, in_chat FROM dunce_chat
                    ON DUPLICATE KEY UPDATE in_dunce_chat = VALUES(in_dunce_chat)
                """);
            }

            Bukkit.getLogger().info("[DunceChat] Migration completed successfully!");

            // Optionally backup and drop old tables
            backupOldTables(conn);

            return true;

        } catch (SQLException e) {
            Bukkit.getLogger().severe("[DunceChat] Migration failed!");
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Backup old tables by renaming them
     */
    private void backupOldTables(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            String timestamp = String.valueOf(System.currentTimeMillis());

            if (tableExists(conn, "users")) {
                stmt.execute("RENAME TABLE users TO users_backup_" + timestamp);
            }
            if (tableExists(conn, "dunced_players")) {
                stmt.execute("RENAME TABLE dunced_players TO dunced_players_backup_" + timestamp);
            }
            if (tableExists(conn, "dunce_visible")) {
                stmt.execute("RENAME TABLE dunce_visible TO dunce_visible_backup_" + timestamp);
            }
            if (tableExists(conn, "dunce_chat")) {
                stmt.execute("RENAME TABLE dunce_chat TO dunce_chat_backup_" + timestamp);
            }

            Bukkit.getLogger().info("[DunceChat] Old tables backed up with suffix: _backup_" + timestamp);
        }
    }

    /**
     * Check if a table exists
     */
    private boolean tableExists(Connection conn, String tableName) throws SQLException {
        var meta = conn.getMetaData();
        try (var rs = meta.getTables(null, null, tableName, new String[]{"TABLE"})) {
            return rs.next();
        }
    }

    /**
     * Update schema version
     */
    private void updateSchemaVersion(int version) {
        try (Connection conn = databaseManager.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("INSERT INTO schema_version (version) VALUES (" + version + ") " +
                        "ON DUPLICATE KEY UPDATE version = " + version);
        } catch (SQLException e) {
            Bukkit.getLogger().warning("[DunceChat] Failed to update schema version.");
            e.printStackTrace();
        }
    }

    /**
     * Get current schema version
     */
    public int getCurrentSchemaVersion() {
        try (Connection conn = databaseManager.getConnection();
             Statement stmt = conn.createStatement();
             var rs = stmt.executeQuery("SELECT MAX(version) as version FROM schema_version")) {
            if (rs.next()) {
                return rs.getInt("version");
            }
        } catch (SQLException e) {
            // Table might not exist yet
            return 0;
        }
        return 0;
    }
}

