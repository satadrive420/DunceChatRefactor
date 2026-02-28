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
     * Initialize the new schema (fresh install only)
     * Creates all tables with version 3 structure directly
     */
    public void initializeSchema() {
        try (Connection conn = databaseManager.getConnection();
             Statement stmt = conn.createStatement()) {

            // First, ensure schema_version table exists
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS schema_version (
                    version INT PRIMARY KEY,
                    applied_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """);

            // Check if this is a fresh install
            int currentVersion = getCurrentSchemaVersion();
            if (currentVersion > 0) {
                // Schema already initialized, nothing to do
                return;
            }

            // Fresh install - create all tables with version 3 structure
            logger.info("[DunceChat] No existing tables detected. Creating tables...");

            // Players table - central player information
            stmt.execute("""
                CREATE TABLE players (
                    uuid VARCHAR(36) PRIMARY KEY,
                    username VARCHAR(16) NOT NULL,
                    first_join TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    last_join TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    last_quit TIMESTAMP NULL,
                    INDEX idx_username (username)
                )
            """);

            // Dunce records table - tracks all dunce actions (with trigger_message column)
            stmt.execute("""
                CREATE TABLE dunce_records (
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
                CREATE TABLE player_preferences (
                    player_uuid VARCHAR(36) PRIMARY KEY,
                    dunce_chat_visible BOOLEAN DEFAULT FALSE,
                    in_dunce_chat BOOLEAN DEFAULT FALSE,
                    FOREIGN KEY (player_uuid) REFERENCES players(uuid) ON DELETE CASCADE
                )
            """);

            // Pending messages table
            stmt.execute("""
                CREATE TABLE pending_messages (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    player_uuid VARCHAR(36) NOT NULL,
                    message_key VARCHAR(255) NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    INDEX idx_player (player_uuid),
                    FOREIGN KEY (player_uuid) REFERENCES players(uuid) ON DELETE CASCADE
                )
            """);

            // Player IP log table - silently tracks IP associations
            stmt.execute("""
                CREATE TABLE player_ip_log (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    player_uuid VARCHAR(36) NOT NULL,
                    ip_address VARCHAR(45) NOT NULL,
                    first_seen TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    last_seen TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    INDEX idx_player (player_uuid),
                    INDEX idx_ip (ip_address),
                    INDEX idx_last_seen (last_seen),
                    UNIQUE KEY unique_player_ip (player_uuid, ip_address),
                    FOREIGN KEY (player_uuid) REFERENCES players(uuid) ON DELETE CASCADE
                )
            """);

            // Set schema version to 3
            updateSchemaVersion(CURRENT_SCHEMA_VERSION);
            logger.info("[DunceChat] Database schema v3 initialized successfully!");

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

        if (currentVersion < CURRENT_SCHEMA_VERSION) {
            logger.info("[DunceChat] Upgrading schema from version " + currentVersion + " to " + CURRENT_SCHEMA_VERSION);

            // Note: We skip version 2 as it only existed in debug environments
            // The migration from unversioned schema goes directly to version 3
            boolean upgradeSuccess = upgradeToVersion3();

            if (upgradeSuccess) {
                updateSchemaVersion(CURRENT_SCHEMA_VERSION);
                logger.info("[DunceChat] Schema upgrade complete!");
            } else {
                logger.severe("[DunceChat] Schema upgrade FAILED! Database may be in inconsistent state.");
                logger.severe("[DunceChat] Please check the error above and fix manually if needed.");
            }
        }
        // No need to log when schema is up to date - it's the normal case
    }

    /**
     * Upgrade schema to version 3: Ensure trigger_message column and pending_messages table exist
     * This upgrades directly from unversioned schema to version 3, skipping version 2
     * @return true if upgrade succeeded, false otherwise
     */
    private boolean upgradeToVersion3() {
        logger.info("[DunceChat] Applying schema upgrade to version 3...");

        try (Connection conn = databaseManager.getConnection();
             Statement stmt = conn.createStatement()) {

            // Ensure trigger_message column exists in dunce_records table
            logger.info("[DunceChat] Checking if trigger_message column exists in dunce_records table...");
            if (!columnExists(conn, "dunce_records", "trigger_message")) {
                logger.info("[DunceChat] Column does not exist. Adding trigger_message column to dunce_records table...");

                String alterSQL = "ALTER TABLE dunce_records ADD COLUMN trigger_message TEXT AFTER undunced_at";
                logger.info("[DunceChat] Executing SQL: " + alterSQL);

                stmt.execute(alterSQL);
                logger.info("[DunceChat] Successfully added trigger_message column to dunce_records table.");
            } else {
                logger.info("[DunceChat] trigger_message column already exists, skipping.");
            }

            // Ensure pending_messages table exists
            logger.info("[DunceChat] Checking if pending_messages table exists...");
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

            // Ensure player_ip_log table exists
            logger.info("[DunceChat] Checking if player_ip_log table exists...");
            if (!tableExists(conn, "player_ip_log")) {
                logger.info("[DunceChat] Table does not exist. Creating player_ip_log table...");

                String createIPLogSQL = """
                    CREATE TABLE player_ip_log (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        player_uuid VARCHAR(36) NOT NULL,
                        ip_address VARCHAR(45) NOT NULL,
                        first_seen TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        last_seen TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        INDEX idx_player (player_uuid),
                        INDEX idx_ip (ip_address),
                        INDEX idx_last_seen (last_seen),
                        UNIQUE KEY unique_player_ip (player_uuid, ip_address),
                        FOREIGN KEY (player_uuid) REFERENCES players(uuid) ON DELETE CASCADE
                    )
                """;
                logger.info("[DunceChat] Executing SQL: CREATE TABLE player_ip_log...");

                stmt.execute(createIPLogSQL);
                logger.info("[DunceChat] Successfully created player_ip_log table.");
            } else {
                logger.info("[DunceChat] player_ip_log table already exists, skipping.");
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
     * Check if old schema tables exist that need migration
     * H2 databases never need migration as they are only used for fresh installs
     */
    public boolean needsMigration() {
        // H2 databases are always fresh installs - no migration needed
        if (databaseManager.getDatabaseType() == DatabaseManager.DatabaseType.H2) {
            return false;
        }

        // Check for old MySQL schema tables
        try (Connection conn = databaseManager.getConnection()) {
            return tableExists(conn, "users") ||
                   tableExists(conn, "dunced_players") ||
                   tableExists(conn, "dunce_visible") ||
                   tableExists(conn, "dunce_chat");
        } catch (SQLException e) {
            logger.info("[DunceChat] Could not check for old schema tables: " + e.getMessage());
            return false;
        }
    }

    /**
     * Migrate from old schema to new schema
     * Only supported for MySQL databases - H2 is always a fresh install
     */
    public boolean migrateFromOldSchema() {
        // H2 databases don't support migration (they're always fresh installs)
        if (databaseManager.getDatabaseType() == DatabaseManager.DatabaseType.H2) {
            logger.info("[DunceChat] Migration is only supported for MySQL databases. H2 databases are always fresh installs.");
            return true; // Return true to allow initialization to continue
        }

        logger.info("[DunceChat] Starting migration from old schema...");

        try (Connection conn = databaseManager.getConnection();
             Statement stmt = conn.createStatement()) {

            boolean isH2 = databaseManager.getDatabaseType() == DatabaseManager.DatabaseType.H2;

            // Migrate users to players table
            if (tableExists(conn, "users")) {
                try {
                    String sql;
                    if (isH2) {
                        // H2: Use MERGE syntax
                        sql = """
                            MERGE INTO players (uuid, username, first_join, last_join, last_quit)
                            KEY(uuid)
                            SELECT uuid, display_name, 
                                   COALESCE(last_login, CURRENT_TIMESTAMP),
                                   COALESCE(last_login, CURRENT_TIMESTAMP),
                                   last_logout
                            FROM users
                        """;
                    } else {
                        // MySQL: Use INSERT ON DUPLICATE KEY UPDATE
                        sql = """
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
                        """;
                    }
                    stmt.execute(sql);
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
                    String sql;
                    if (isH2) {
                        // H2: Use MERGE syntax
                        sql = """
                            MERGE INTO player_preferences (player_uuid, dunce_chat_visible)
                            KEY(player_uuid)
                            SELECT uuid, visible FROM dunce_visible
                        """;
                    } else {
                        // MySQL: Use INSERT ON DUPLICATE KEY UPDATE
                        sql = """
                            INSERT INTO player_preferences (player_uuid, dunce_chat_visible)
                            SELECT uuid, visible FROM dunce_visible
                            ON DUPLICATE KEY UPDATE dunce_chat_visible = VALUES(dunce_chat_visible)
                        """;
                    }
                    stmt.execute(sql);
                    logger.info("[DunceChat] Migrated dunce_visible preferences.");
                } catch (SQLException e) {
                    logger.warning("[DunceChat] Failed to migrate dunce_visible table: " + e.getMessage());
                    // Continue with other migrations
                }
            }

            if (tableExists(conn, "dunce_chat")) {
                try {
                    String sql;
                    if (isH2) {
                        // H2: Use MERGE syntax
                        sql = """
                            MERGE INTO player_preferences (player_uuid, in_dunce_chat)
                            KEY(player_uuid)
                            SELECT uuid, in_chat FROM dunce_chat
                        """;
                    } else {
                        // MySQL: Use INSERT ON DUPLICATE KEY UPDATE
                        sql = """
                            INSERT INTO player_preferences (player_uuid, in_dunce_chat)
                            SELECT uuid, in_chat FROM dunce_chat
                            ON DUPLICATE KEY UPDATE in_dunce_chat = VALUES(in_dunce_chat)
                        """;
                    }
                    stmt.execute(sql);
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
     * Backup old tables using database-specific syntax
     * MySQL: RENAME TABLE
     * H2: CREATE TABLE AS SELECT (though this should never run for H2)
     */
    private void backupOldTables(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            String timestamp = String.valueOf(System.currentTimeMillis());
            boolean isH2 = databaseManager.getDatabaseType() == DatabaseManager.DatabaseType.H2;

            if (tableExists(conn, "users")) {
                backupTable(stmt, "users", timestamp, isH2);
            }
            if (tableExists(conn, "dunced_players")) {
                backupTable(stmt, "dunced_players", timestamp, isH2);
            }
            if (tableExists(conn, "dunce_visible")) {
                backupTable(stmt, "dunce_visible", timestamp, isH2);
            }
            if (tableExists(conn, "dunce_chat")) {
                backupTable(stmt, "dunce_chat", timestamp, isH2);
            }

            logger.info("[DunceChat] Old tables backed up with suffix: _backup_" + timestamp);
        }
    }

    /**
     * Backup a single table using database-specific syntax
     */
    private void backupTable(Statement stmt, String tableName, String timestamp, boolean isH2) throws SQLException {
        String backupName = tableName + "_backup_" + timestamp;

        if (isH2) {
            // H2: Create backup table then drop original
            stmt.execute("CREATE TABLE " + backupName + " AS SELECT * FROM " + tableName);
            stmt.execute("DROP TABLE " + tableName);
        } else {
            // MySQL: Simple rename
            stmt.execute("RENAME TABLE " + tableName + " TO " + backupName);
        }
    }

    /**
     * Check if a table exists in the current database
     */
    private boolean tableExists(Connection conn, String tableName) throws SQLException {
        var meta = conn.getMetaData();
        String catalog = conn.getCatalog(); // Get current database name

        // Try with exact case
        try (var rs = meta.getTables(catalog, null, tableName, new String[]{"TABLE"})) {
            if (rs.next()) {
                return true;
            }
        }

        // Try with uppercase (MySQL sometimes stores as uppercase)
        try (var rs = meta.getTables(catalog, null, tableName.toUpperCase(), new String[]{"TABLE"})) {
            if (rs.next()) {
                return true;
            }
        }

        // Try with lowercase
        try (var rs = meta.getTables(catalog, null, tableName.toLowerCase(), new String[]{"TABLE"})) {
            if (rs.next()) {
                return true;
            }
        }

        return false;
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
     * Update schema version using database-specific SQL
     */
    private void updateSchemaVersion(int version) {
        try (Connection conn = databaseManager.getConnection();
             Statement stmt = conn.createStatement()) {

            String sql;
            if (databaseManager.getDatabaseType() == DatabaseManager.DatabaseType.H2) {
                // H2 uses MERGE syntax
                sql = "MERGE INTO schema_version (version, applied_at) " +
                      "KEY(version) VALUES (" + version + ", CURRENT_TIMESTAMP)";
            } else {
                // MySQL uses INSERT ON DUPLICATE KEY UPDATE
                sql = "INSERT INTO schema_version (version) VALUES (" + version + ") " +
                      "ON DUPLICATE KEY UPDATE version = " + version;
            }

            stmt.execute(sql);
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
                return rs.getInt("version");
            }
            return 0;
        } catch (SQLException e) {
            // Table might not exist yet
            return 0;
        }
    }
}

