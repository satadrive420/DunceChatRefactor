package gg.corn.DunceChat.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.Bukkit;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

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

    public DatabaseManager(String host, int port, String database, String username, String password) {
        this.host = host;
        this.port = port;
        this.database = database;
        this.username = username;
        this.password = password;
    }

    /**
     * Initialize the connection pool
     */
    public void initialize() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false&allowPublicKeyRetrieval=true");
        config.setUsername(username);
        config.setPassword(password);

        // Connection pool settings
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);

        // Performance settings
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");

        try {
            this.dataSource = new HikariDataSource(config);
            Bukkit.getLogger().info("[DunceChat] Database connection pool initialized successfully!");
            testConnection();
        } catch (Exception e) {
            Bukkit.getLogger().severe("[DunceChat] Failed to initialize database connection pool!");
            e.printStackTrace();
        }
    }

    /**
     * Test the database connection
     */
    private void testConnection() {
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("SELECT 1");
            Bukkit.getLogger().info("[DunceChat] Database connection test successful!");
        } catch (SQLException e) {
            Bukkit.getLogger().severe("[DunceChat] Database connection test failed!");
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
            Bukkit.getLogger().info("[DunceChat] Database connection pool closed.");
        }
    }

    /**
     * Check if the connection pool is active
     */
    public boolean isInitialized() {
        return dataSource != null && !dataSource.isClosed();
    }
}

