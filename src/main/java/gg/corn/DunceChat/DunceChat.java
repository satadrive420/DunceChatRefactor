package gg.corn.DunceChat;

import gg.corn.DunceChat.command.*;
import gg.corn.DunceChat.database.DatabaseManager;
import gg.corn.DunceChat.database.SchemaManager;
import gg.corn.DunceChat.gui.DunceGUIBuilder;
import gg.corn.DunceChat.listener.ChatListener;
import gg.corn.DunceChat.listener.GUIListener;
import gg.corn.DunceChat.listener.GreentextListener;
import gg.corn.DunceChat.repository.DunceRepository;
import gg.corn.DunceChat.repository.PendingMessageRepository;
import gg.corn.DunceChat.repository.PlayerRepository;
import gg.corn.DunceChat.repository.PreferencesRepository;
import gg.corn.DunceChat.service.DunceService;
import gg.corn.DunceChat.service.PlayerService;
import gg.corn.DunceChat.service.PreferencesService;
import gg.corn.DunceChat.util.MessageManager;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.List;
import java.util.Objects;

/**
 * DunceChat - Refactored with clean architecture
 * Main plugin class using dependency injection and service layer
 */
public class DunceChat extends JavaPlugin {

    // Database
    private DatabaseManager databaseManager;
    private SchemaManager schemaManager;

    // Repositories
    private PlayerRepository playerRepository;
    private DunceRepository dunceRepository;
    private PendingMessageRepository pendingMessageRepository;
    private PreferencesRepository preferencesRepository;

    // Services
    private PlayerService playerService;
    private DunceService dunceService;
    private PreferencesService preferencesService;

    // Utilities
    private MessageManager messageManager;
    private DunceGUIBuilder guiBuilder;

    // Config
    private FileConfiguration wordsConfig;

    @Override
    public void onEnable() {
        getLogger().info("DunceChat v" + getDescription().getVersion() + " is loading...");

        // Ensure data folder exists
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        // Load configs
        loadConfig();
        loadWordsConfig();

        // Initialize message manager
        messageManager = new MessageManager(this);

        // Initialize database
        initializeDatabase();

        // Initialize repositories
        initializeRepositories();

        // Initialize services
        initializeServices();

        // Initialize GUI builder
        guiBuilder = new DunceGUIBuilder(dunceService, playerService, preferencesService, messageManager, this);

        // Register commands
        registerCommands();

        // Register listeners
        registerListeners();

        // Start expiry checker
        startExpiryChecker();

        getLogger().info("DunceChat v" + getDescription().getVersion() + " loaded successfully!");
    }

    @Override
    public void onDisable() {
        if (databaseManager != null) {
            databaseManager.close();
        }
        getLogger().info("DunceChat v" + getDescription().getVersion() + " disabled.");
    }

    /**
     * Initialize database connection and schema
     */
    private void initializeDatabase() {
        try {
            getLogger().info("=== Database Initialization Starting ===");

            String databaseType = getConfig().getString("database.type", "h2").toLowerCase();
            getLogger().info("Database type from config: " + databaseType);

            if (databaseType.equals("mysql")) {
                String host = getConfig().getString("database.mysql.host");
                int port = getConfig().getInt("database.mysql.port");
                String database = getConfig().getString("database.mysql.database");
                String username = getConfig().getString("database.mysql.username");
                String password = getConfig().getString("database.mysql.password");

                getLogger().info("MySQL Configuration: " + host + ":" + port + "/" + database);
                databaseManager = new DatabaseManager(host, port, database, username, password);
            } else {
                // Default to H2
                String h2FileConfig = getConfig().getString("database.h2.file", "database");
                // If path is not absolute, make it relative to plugin folder
                String h2FilePath;
                if (new File(h2FileConfig).isAbsolute()) {
                    h2FilePath = h2FileConfig;
                } else {
                    h2FilePath = getDataFolder().getAbsolutePath() + File.separator + h2FileConfig;
                }
                getLogger().info("H2 file path: " + h2FilePath);
                databaseManager = new DatabaseManager(h2FilePath);
            }

            getLogger().info("Initializing database connection pool...");
            databaseManager.initialize();
            getLogger().info("Database connection pool initialized!");

            getLogger().info("Initializing database schema...");
            schemaManager = new SchemaManager(databaseManager);
            schemaManager.initializeSchema();
            schemaManager.applySchemaUpgrades();
            getLogger().info("Database schema initialized!");

            // Auto-migrate if enabled and old tables exist
            if (getConfig().getBoolean("auto-migrate", true)) {
                if (schemaManager.needsMigration()) {
                    getLogger().info("Old schema detected, starting migration...");
                    schemaManager.migrateFromOldSchema();
                } else {
                    getLogger().info("No old schema detected, skipping migration.");
                }
            }

            getLogger().info("=== Database Initialization Complete ===");
        } catch (Exception e) {
            getLogger().severe("=== Database Initialization FAILED ===");
            getLogger().severe("Failed to initialize database! Plugin may not function correctly.");
            getLogger().severe("Error: " + e.getClass().getName() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Initialize all repositories
     */
    private void initializeRepositories() {
        playerRepository = new PlayerRepository(databaseManager);
        dunceRepository = new DunceRepository(databaseManager);
        pendingMessageRepository = new PendingMessageRepository(databaseManager);

        boolean defaultVisibility = getConfig().getBoolean("visible-by-default", false);
        preferencesRepository = new PreferencesRepository(databaseManager, defaultVisibility);

        getLogger().info("Repositories initialized.");
    }

    /**
     * Initialize all services
     */
    private void initializeServices() {
        playerService = new PlayerService(playerRepository, getConfig());
        preferencesService = new PreferencesService(preferencesRepository);
        dunceService = new DunceService(dunceRepository, pendingMessageRepository, playerService, preferencesService, messageManager);

        getLogger().info("Services initialized.");
    }

    /**
     * Register all commands with their handlers
     */
    private void registerCommands() {
        // Dunce/Undunce commands
        DunceCommand dunceCommand = new DunceCommand(dunceService, playerService, messageManager);
        Objects.requireNonNull(getCommand("dunce")).setExecutor(dunceCommand);
        Objects.requireNonNull(getCommand("dunce")).setTabCompleter(dunceCommand);
        Objects.requireNonNull(getCommand("undunce")).setExecutor(dunceCommand);
        Objects.requireNonNull(getCommand("undunce")).setTabCompleter(dunceCommand);

        // Dunce Chat message command
        DunceChatCommand dunceChatCommand = new DunceChatCommand(dunceService, preferencesService, messageManager);
        Objects.requireNonNull(getCommand("duncechat")).setExecutor(dunceChatCommand);

        // Toggle commands
        ToggleCommand toggleCommand = new ToggleCommand(dunceService, preferencesService, messageManager);
        Objects.requireNonNull(getCommand("dcon")).setExecutor(toggleCommand);
        Objects.requireNonNull(getCommand("dcoff")).setExecutor(toggleCommand);

        // GUI command
        Objects.requireNonNull(getCommand("duncemenu")).setExecutor(new MenuCommand(guiBuilder));

        // Utility commands
        Objects.requireNonNull(getCommand("clearchat")).setExecutor(new ClearChatCommand(messageManager));
        Objects.requireNonNull(getCommand("duncereload")).setExecutor(new ReloadCommand(this, messageManager));
        Objects.requireNonNull(getCommand("duncelookup")).setExecutor(new LookupCommand(dunceService, playerService, messageManager));
        Objects.requireNonNull(getCommand("duncemigrate")).setExecutor(new MigrateCommand(schemaManager, messageManager));

        getLogger().info("Commands registered.");
    }

    /**
     * Register all event listeners
     */
    private void registerListeners() {
        List<String> disallowedWords = wordsConfig.getStringList("disallowed-words");

        getServer().getPluginManager().registerEvents(
            new ChatListener(dunceService, playerService, preferencesService, messageManager, getConfig(), disallowedWords),
            this);
        getServer().getPluginManager().registerEvents(
            new GreentextListener(this),
            this);
        getServer().getPluginManager().registerEvents(
            new GUIListener(dunceService, preferencesService, messageManager, this),
            this);

        getLogger().info("Event listeners registered.");
    }

    /**
     * Start the scheduler that checks for expired dunces
     */
    private void startExpiryChecker() {
        if (databaseManager == null || !databaseManager.isInitialized()) {
            getLogger().warning("Cannot start expiry checker - database not initialized!");
            return;
        }

        getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            try {
                dunceService.processExpiredDunces();
            } catch (Exception e) {
                getLogger().severe("Error processing expired dunces: " + e.getMessage());
                e.printStackTrace();
            }
        }, 0L, 20L); // Run every second

        getLogger().info("Expiry checker started.");
    }

    /**
     * Load plugin configuration
     */
    private void loadConfig() {
        getConfig().options().copyDefaults(true);
        saveConfig();
    }

    /**
     * Load words configuration
     */
    private void loadWordsConfig() {
        File wordsFile = new File(getDataFolder(), "words.yml");
        if (!wordsFile.exists()) {
            try (InputStream in = getResource("words.yml")) {
                if (in != null) {
                    Files.copy(in, wordsFile.toPath());
                }
            } catch (Exception e) {
                getLogger().warning("Failed to create words.yml");
                e.printStackTrace();
            }
        }
        wordsConfig = YamlConfiguration.loadConfiguration(wordsFile);
    }

    /**
     * Get words configuration
     */
    public FileConfiguration getWordsConfig() {
        return wordsConfig;
    }
}

