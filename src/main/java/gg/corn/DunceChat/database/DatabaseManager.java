package gg.corn.DunceChat.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Logger;

/**
 * Manages database connections using HikariCP connection pooling
 */
public class DatabaseManager {

    private HikariDataSource dataSource;
    private final String host;
    private final int port;
    private final String database;
    private final String username;
    private final String password;
    private final DatabaseType databaseType;
    private final String h2FilePath;
    private static final Logger logger = Logger.getLogger("DunceChat");

    public enum DatabaseType {
        MYSQL, H2
    }

    // Constructor for MySQL
    public DatabaseManager(String host, int port, String database, String username, String password) {
        this.databaseType = DatabaseType.MYSQL;
        this.host = host;
        this.port = port;
        this.database = database;
        this.username = username;
        this.password = password;
        this.h2FilePath = null;
    }

    // Constructor for H2
    public DatabaseManager(String h2FilePath) {
        this.databaseType = DatabaseType.H2;
        this.h2FilePath = h2FilePath;
        this.host = null;
        this.port = 0;
        this.database = null;
        this.username = null;
        this.password = null;
    }

    /**
     * Initialize the connection pool
     */
    public void initialize() {
        // For MySQL, ensure database exists before creating the connection pool
        if (databaseType == DatabaseType.MYSQL) {
            ensureDatabaseExists();
        }

        HikariConfig config = new HikariConfig();

        // Configure based on database type
        if (databaseType == DatabaseType.MYSQL) {
            config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false&allowPublicKeyRetrieval=true");
            config.setUsername(username);
            config.setPassword(password);
            config.setDriverClassName("com.mysql.cj.jdbc.Driver");

            // MySQL-specific performance settings
            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
            config.addDataSourceProperty("useServerPrepStmts", "true");
        } else if (databaseType == DatabaseType.H2) {
            config.setJdbcUrl("jdbc:h2:" + h2FilePath + ";MODE=MySQL");
            config.setDriverClassName("org.h2.Driver");
            logger.info("[DunceChat] Using H2 database at: " + h2FilePath);
        }

        // Connection pool settings
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);

        try {
            this.dataSource = new HikariDataSource(config);
            logger.info("[DunceChat] Database connection pool initialized successfully! (Type: " + databaseType + ")");
            testConnection();
        } catch (Exception e) {
            logger.severe("[DunceChat] Failed to initialize database connection pool!");
            logger.severe("[DunceChat] Database Type: " + databaseType);
            if (databaseType == DatabaseType.H2) {
                logger.severe("[DunceChat] H2 File Path: " + h2FilePath);
                logger.severe("[DunceChat] JDBC URL: jdbc:h2:" + h2FilePath + ";MODE=MySQL");
            } else {
                logger.severe("[DunceChat] MySQL Host: " + host + ":" + port);
                logger.severe("[DunceChat] MySQL Database: " + database);
            }
            e.printStackTrace();
            throw new RuntimeException("Failed to initialize database", e);
        }
    }

    /**
     * Ensure MySQL database exists, create if it doesn't
     */
    private void ensureDatabaseExists() {
        String urlWithoutDb = "jdbc:mysql://" + host + ":" + port + "?useSSL=false&allowPublicKeyRetrieval=true";

        try (Connection conn = java.sql.DriverManager.getConnection(urlWithoutDb, username, password);
             Statement stmt = conn.createStatement()) {

            // Check if database exists
            var rs = stmt.executeQuery("SELECT SCHEMA_NAME FROM INFORMATION_SCHEMA.SCHEMATA WHERE SCHEMA_NAME = '" + database + "'");

            if (!rs.next()) {
                // Database doesn't exist, create it
                logger.info("[DunceChat] Database '" + database + "' does not exist. Creating it...");
                stmt.executeUpdate("CREATE DATABASE `" + database + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
                logger.info("[DunceChat] Database '" + database + "' created successfully!");
            } else {
                logger.info("[DunceChat] Database '" + database + "' already exists.");
            }

        } catch (SQLException e) {
            logger.warning("[DunceChat] Could not automatically create database '" + database + "'");
            logger.warning("[DunceChat] Error: " + e.getMessage());
            logger.warning("[DunceChat] The user may not have CREATE DATABASE permission.");
            logger.warning("[DunceChat] Please create the database manually:");
            logger.warning("[DunceChat]   CREATE DATABASE `" + database + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;");
            // Don't throw exception here, let the normal connection attempt fail with full error
        }
    }

    /**
     * Test the database connection
     */
    private void testConnection() {
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("SELECT 1");
            logger.info("[DunceChat] Database connection test successful!");
        } catch (SQLException e) {
            logger.severe("[DunceChat] Database connection test failed!");
            e.printStackTrace();
        }
    }

    /**
     * Get a connection from the pool
     */
    public Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            throw new SQLException("Database connection pool is not initialized!");
        }
        return dataSource.getConnection();
    }

    /**
     * Close the connection pool
     */
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            logger.info("[DunceChat] Database connection pool closed.");
        }
    }

    /**
     * Check if the connection pool is active
     */
    public boolean isInitialized() {
        return dataSource != null && !dataSource.isClosed();
    }

    /**
     * Get the database type (MySQL or H2)
     */
    public DatabaseType getDatabaseType() {
        return databaseType;
    }
}

