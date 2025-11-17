package gg.corn.DunceChat;

import gg.corn.DunceChat.command.*;
import gg.corn.DunceChat.database.DatabaseManager;
import gg.corn.DunceChat.database.SchemaManager;
import gg.corn.DunceChat.gui.DunceGUIBuilder;
import gg.corn.DunceChat.listener.ChatListener;
import gg.corn.DunceChat.listener.GUIListener;
import gg.corn.DunceChat.listener.GreentextListener;
import gg.corn.DunceChat.repository.DunceRepository;
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
        getLogger().info("Starting DunceChat with clean architecture...");

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

        getLogger().info("DunceChat enabled successfully with clean architecture!");
    }

    @Override
    public void onDisable() {
        if (databaseManager != null) {
            databaseManager.close();
        }
        getLogger().info("DunceChat disabled.");
    }

    /**
     * Initialize database connection and schema
     */
    private void initializeDatabase() {
        String host = getConfig().getString("mysql.host");
        int port = getConfig().getInt("mysql.port");
        String database = getConfig().getString("mysql.database");
        String username = getConfig().getString("mysql.username");
        String password = getConfig().getString("mysql.password");

        databaseManager = new DatabaseManager(host, port, database, username, password);
        databaseManager.initialize();

        schemaManager = new SchemaManager(databaseManager);
        schemaManager.initializeSchema();

        // Auto-migrate if enabled
        if (getConfig().getBoolean("auto-migrate", true)) {
            getLogger().info("Auto-migration enabled, checking for old schema...");
            schemaManager.migrateFromOldSchema();
        }
    }

    /**
     * Initialize all repositories
     */
    private void initializeRepositories() {
        playerRepository = new PlayerRepository(databaseManager);
        dunceRepository = new DunceRepository(databaseManager);

        boolean defaultVisibility = getConfig().getBoolean("visible-by-default", false);
        preferencesRepository = new PreferencesRepository(databaseManager, defaultVisibility);

        getLogger().info("Repositories initialized.");
    }

    /**
     * Initialize all services
     */
    private void initializeServices() {
        playerService = new PlayerService(playerRepository);
        preferencesService = new PreferencesService(preferencesRepository);
        dunceService = new DunceService(dunceRepository, playerService, preferencesService, messageManager);

        getLogger().info("Services initialized.");
    }

    /**
     * Register all commands with their handlers
     */
    private void registerCommands() {
        // Dunce/Undunce commands
        DunceCommandNew dunceCommand = new DunceCommandNew(dunceService, playerService, messageManager);
        Objects.requireNonNull(getCommand("dunce")).setExecutor(dunceCommand);
        Objects.requireNonNull(getCommand("dunce")).setTabCompleter(dunceCommand);
        Objects.requireNonNull(getCommand("undunce")).setExecutor(dunceCommand);
        Objects.requireNonNull(getCommand("undunce")).setTabCompleter(dunceCommand);

        // Toggle commands
        ToggleCommandNew toggleCommand = new ToggleCommandNew(preferencesService, messageManager);
        Objects.requireNonNull(getCommand("dcon")).setExecutor(toggleCommand);
        Objects.requireNonNull(getCommand("dcoff")).setExecutor(toggleCommand);

        // GUI command
        Objects.requireNonNull(getCommand("duncemenu")).setExecutor(new MenuCommand(guiBuilder));

        // Utility commands
        Objects.requireNonNull(getCommand("clearchat")).setExecutor(new ClearChatCommandNew(messageManager));
        Objects.requireNonNull(getCommand("duncereload")).setExecutor(new ReloadCommandNew(this, messageManager));
        Objects.requireNonNull(getCommand("duncelookup")).setExecutor(new LookupCommandNew(dunceService, playerService, messageManager));
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
            new GUIListener(dunceService, preferencesService, this),
            this);

        getLogger().info("Event listeners registered.");
    }

    /**
     * Start the scheduler that checks for expired dunces
     */
    private void startExpiryChecker() {
        getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            dunceService.processExpiredDunces();
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

