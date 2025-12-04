package gg.corn.DunceChat.database;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Logger;

/**
 * Handles database schema initialization and migrations
 */
public class SchemaManager {

    private final DatabaseManager databaseManager;
    private static final int CURRENT_SCHEMA_VERSION = 3;
    private static final Logger logger = Logger.getLogger("DunceChat");

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
                    trigger_message TEXT,
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

            // Pending messages table - stores messages for offline players
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS pending_messages (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    player_uuid VARCHAR(36) NOT NULL,
                    message_key VARCHAR(255) NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    INDEX idx_player (player_uuid),
                    FOREIGN KEY (player_uuid) REFERENCES players(uuid) ON DELETE CASCADE
                )
            """);

            logger.info("[DunceChat] Database schema initialized successfully!");
            updateSchemaVersion(CURRENT_SCHEMA_VERSION);

        } catch (SQLException e) {
            logger.severe("[DunceChat] Failed to initialize database schema!");
            e.printStackTrace();
        }
    }

    /**
     * Apply schema upgrades if needed
     */
    public void applySchemaUpgrades() {
        int currentVersion = getCurrentSchemaVersion();

        logger.info("[DunceChat] Current schema version: " + currentVersion);

        if (currentVersion < CURRENT_SCHEMA_VERSION) {
            logger.info("[DunceChat] Upgrading schema from version " + currentVersion + " to " + CURRENT_SCHEMA_VERSION);

            boolean upgradeSuccess = false;

            if (currentVersion < 2) {
                upgradeSuccess = upgradeToVersion2();
            }

            if (currentVersion < 3 && (currentVersion >= 2 || upgradeSuccess)) {
                upgradeSuccess = upgradeToVersion3();
            }

            if (upgradeSuccess) {
                updateSchemaVersion(CURRENT_SCHEMA_VERSION);
                logger.info("[DunceChat] Schema upgrade complete!");
            } else {
                logger.severe("[DunceChat] Schema upgrade FAILED! Database may be in inconsistent state.");
                logger.severe("[DunceChat] Please check the error above and fix manually if needed.");
            }
        } else {
            logger.info("[DunceChat] Schema is up to date (version " + currentVersion + ")");
        }
    }

    /**
     * Upgrade schema to version 2: Add trigger_message column
     * @return true if upgrade succeeded, false otherwise
     */
    private boolean upgradeToVersion2() {
        logger.info("[DunceChat] Applying schema upgrade to version 2...");

        try (Connection conn = databaseManager.getConnection();
             Statement stmt = conn.createStatement()) {

            logger.info("[DunceChat] Checking if trigger_message column exists in dunce_records table...");

            // Add trigger_message column if it doesn't exist
            if (!columnExists(conn, "dunce_records", "trigger_message")) {
                logger.info("[DunceChat] Column does not exist. Adding trigger_message column to dunce_records table...");

                String alterSQL = "ALTER TABLE dunce_records ADD COLUMN trigger_message TEXT AFTER undunced_at";
                logger.info("[DunceChat] Executing SQL: " + alterSQL);

                stmt.execute(alterSQL);
                logger.info("[DunceChat] Successfully added trigger_message column to dunce_records table.");
            } else {
                logger.info("[DunceChat] trigger_message column already exists, skipping.");
            }

            return true;

        } catch (SQLException e) {
            logger.severe("[DunceChat] Failed to upgrade schema to version 2!");
            logger.severe("[DunceChat] Error: " + e.getMessage());
            logger.severe("[DunceChat] SQLState: " + e.getSQLState());
            logger.severe("[DunceChat] Error Code: " + e.getErrorCode());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Upgrade schema to version 3: Add pending_messages table
     * @return true if upgrade succeeded, false otherwise
     */
    private boolean upgradeToVersion3() {
        logger.info("[DunceChat] Applying schema upgrade to version 3...");

        try (Connection conn = databaseManager.getConnection();
             Statement stmt = conn.createStatement()) {

            logger.info("[DunceChat] Checking if pending_messages table exists...");

            // Add pending_messages table if it doesn't exist
            if (!tableExists(conn, "pending_messages")) {
                logger.info("[DunceChat] Table does not exist. Creating pending_messages table...");

                String createSQL = """
                    CREATE TABLE pending_messages (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        player_uuid VARCHAR(36) NOT NULL,
                        message_key VARCHAR(255) NOT NULL,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        INDEX idx_player (player_uuid),
                        FOREIGN KEY (player_uuid) REFERENCES players(uuid) ON DELETE CASCADE
                    )
                """;
                logger.info("[DunceChat] Executing SQL: CREATE TABLE pending_messages...");

                stmt.execute(createSQL);
                logger.info("[DunceChat] Successfully created pending_messages table.");
            } else {
                logger.info("[DunceChat] pending_messages table already exists, skipping.");
            }

            return true;

        } catch (SQLException e) {
            logger.severe("[DunceChat] Failed to upgrade schema to version 3!");
            logger.severe("[DunceChat] Error: " + e.getMessage());
            logger.severe("[DunceChat] SQLState: " + e.getSQLState());
            logger.severe("[DunceChat] Error Code: " + e.getErrorCode());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Migrate from old schema to new schema
     */
    public boolean migrateFromOldSchema() {
        logger.info("[DunceChat] Starting migration from old schema...");

        try (Connection conn = databaseManager.getConnection();
             Statement stmt = conn.createStatement()) {

            // Check if old tables exist
            if (!tableExists(conn, "users") && !tableExists(conn, "dunced_players")) {
                logger.info("[DunceChat] No old schema detected, skipping migration.");
                return true;
            }

            // Migrate users to players table
            if (tableExists(conn, "users")) {
                try {
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
                    logger.info("[DunceChat] Migrated user data to new schema.");
                } catch (SQLException e) {
                    logger.warning("[DunceChat] Failed to migrate users table: " + e.getMessage());
                    // Continue with other migrations
                }
            }

            // Migrate dunce records
            if (tableExists(conn, "dunced_players")) {
                try {
                    stmt.execute("""
                        INSERT INTO dunce_records (player_uuid, is_dunced, reason, staff_uuid, dunced_at, expires_at)
                        SELECT uuid, dunced, reason, staff_uuid, 
                               COALESCE(date, CURRENT_TIMESTAMP),
                               expiry
                        FROM dunced_players
                        WHERE dunced = TRUE
                    """);
                    logger.info("[DunceChat] Migrated dunce records to new schema.");
                } catch (SQLException e) {
                    logger.warning("[DunceChat] Failed to migrate dunced_players table: " + e.getMessage());
                    // Continue with other migrations
                }
            }

            // Migrate preferences
            if (tableExists(conn, "dunce_visible")) {
                try {
                    stmt.execute("""
                        INSERT INTO player_preferences (player_uuid, dunce_chat_visible)
                        SELECT uuid, visible FROM dunce_visible
                        ON DUPLICATE KEY UPDATE dunce_chat_visible = VALUES(dunce_chat_visible)
                    """);
                    logger.info("[DunceChat] Migrated dunce_visible preferences.");
                } catch (SQLException e) {
                    logger.warning("[DunceChat] Failed to migrate dunce_visible table: " + e.getMessage());
                    // Continue with other migrations
                }
            }

            if (tableExists(conn, "dunce_chat")) {
                try {
                    stmt.execute("""
                        INSERT INTO player_preferences (player_uuid, in_dunce_chat)
                        SELECT uuid, in_chat FROM dunce_chat
                        ON DUPLICATE KEY UPDATE in_dunce_chat = VALUES(in_dunce_chat)
                    """);
                    logger.info("[DunceChat] Migrated dunce_chat preferences.");
                } catch (SQLException e) {
                    logger.warning("[DunceChat] Failed to migrate dunce_chat table: " + e.getMessage());
                    // Continue
                }
            }

            logger.info("[DunceChat] Migration completed successfully!");

            // Optionally backup and drop old tables
            try {
                backupOldTables(conn);
            } catch (SQLException e) {
                logger.warning("[DunceChat] Failed to backup old tables: " + e.getMessage());
                // This is not critical, continue
            }

            return true;

        } catch (SQLException e) {
            logger.warning("[DunceChat] Migration check failed: " + e.getMessage());
            logger.info("[DunceChat] This is normal for fresh installations. Skipping migration.");
            return true; // Return true so initialization continues
        } catch (Exception e) {
            logger.severe("[DunceChat] Unexpected error during migration!");
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

            logger.info("[DunceChat] Old tables backed up with suffix: _backup_" + timestamp);
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
     * Check if a column exists in a table
     */
    private boolean columnExists(Connection conn, String tableName, String columnName) throws SQLException {
        var meta = conn.getMetaData();

        // Try with exact case first
        try (var rs = meta.getColumns(null, null, tableName, columnName)) {
            if (rs.next()) {
                logger.info("[DunceChat] Column check: " + columnName + " exists in " + tableName);
                return true;
            }
        }

        // Try with uppercase table name (MySQL default)
        try (var rs = meta.getColumns(null, null, tableName.toUpperCase(), columnName)) {
            if (rs.next()) {
                logger.info("[DunceChat] Column check: " + columnName + " exists in " + tableName.toUpperCase());
                return true;
            }
        }

        // Try with lowercase table name
        try (var rs = meta.getColumns(null, null, tableName.toLowerCase(), columnName)) {
            if (rs.next()) {
                logger.info("[DunceChat] Column check: " + columnName + " exists in " + tableName.toLowerCase());
                return true;
            }
        }

        // Fallback: Use direct SQL query
        try (Statement stmt = conn.createStatement()) {
            String query = "SELECT " + columnName + " FROM " + tableName + " LIMIT 0";
            stmt.executeQuery(query);
            logger.info("[DunceChat] Column check (SQL): " + columnName + " exists in " + tableName);
            return true;
        } catch (SQLException e) {
            // Column doesn't exist
            logger.info("[DunceChat] Column check: " + columnName + " does NOT exist in " + tableName);
            return false;
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
            logger.info("[DunceChat] Updated schema version to " + version);
        } catch (SQLException e) {
            logger.severe("[DunceChat] Failed to update schema version!");
            logger.severe("[DunceChat] SQL Error: " + e.getMessage());
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
                int version = rs.getInt("version");
                logger.info("[DunceChat] Read schema version from database: " + version);
                return version;
            }
            logger.info("[DunceChat] No schema version found in database, defaulting to 0");
            return 0;
        } catch (SQLException e) {
            // Table might not exist yet
            logger.info("[DunceChat] Could not read schema version (table may not exist), defaulting to 0");
            return 0;
        }
    }
}

