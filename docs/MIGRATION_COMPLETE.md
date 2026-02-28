# DunceChat Refactoring - Migration Guide

## ✅ What We've Done

### 1. **Created Clean Architecture** (Already Done)
- Database layer with connection pooling
- Model layer with clean POJOs
- Repository layer for data access
- Service layer for business logic
- Command layer using dependency injection

### 2. **Implemented Message System** (NEW!)
- All messages now in `messages.properties`
- Easy to customize without code changes
- Support for placeholders
- Multi-language ready (future)

### 3. **Refactored All Components** (NEW!)

#### New Command Classes:
- `DunceCommandNew.java` - Clean dunce/undunce handling
- `ToggleCommandNew.java` - Chat visibility toggle
- `LookupCommandNew.java` - Dunce lookup
- `ClearChatCommandNew.java` - Clear chat
- `ReloadCommandNew.java` - Reload config + messages
- `MenuCommand.java` - Open GUI
- `MigrateCommand.java` - Database migration

#### New Event Listeners:
- `ChatListener.java` - All chat events in one place
- `GreentextListener.java` - Greentext formatting
- `GUIListener.java` - GUI click handling

#### New GUI System:
- `DunceGUIBuilder.java` - Clean GUI construction
- Uses MessageManager for all text
- No more hardcoded strings

### 4. **New Utility Classes**:
- `MessageManager.java` - Message loading and formatting

## 📝 Files Created

### Core Architecture (Previous):
```
database/
├── DatabaseManager.java
└── SchemaManager.java

model/
├── Player.java
├── DunceRecord.java
└── PlayerPreferences.java

repository/
├── PlayerRepository.java
├── DunceRepository.java
└── PreferencesRepository.java

service/
├── PlayerService.java
├── DunceService.java
└── PreferencesService.java
```

### New Refactored Components:
```
command/
├── DunceCommandNew.java      (replaces DunceCommand.java)
├── ToggleCommandNew.java     (replaces DunceToggle.java)
├── LookupCommandNew.java     (replaces DunceLookupCommand.java)
├── ClearChatCommandNew.java  (replaces ClearChat.java)
├── ReloadCommandNew.java     (replaces ReloadCommand.java)
├── MenuCommand.java          (replaces DunceGUI.java command part)
└── MigrateCommand.java       (new)

listener/
├── ChatListener.java         (replaces Events.java)
├── GreentextListener.java    (replaces Greentexter.java)
└── GUIListener.java          (GUI click handling)

gui/
└── DunceGUIBuilder.java      (replaces DunceGUI.java GUI part)

util/
└── MessageManager.java       (new)

resources/
└── messages.properties       (new)
```

## 🔄 Migration Steps

### Step 1: Update Main Plugin Class (DunceChat.java)

Replace the entire content with the new initialization:

```java
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
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.List;
import java.util.Objects;

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
        
        getLogger().info("DunceChat enabled with clean architecture!");
    }

    @Override
    public void onDisable() {
        if (databaseManager != null) {
            databaseManager.close();
        }
        getLogger().info("DunceChat disabled.");
    }

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

    private void initializeRepositories() {
        playerRepository = new PlayerRepository(databaseManager);
        dunceRepository = new DunceRepository(databaseManager);
        
        boolean defaultVisibility = getConfig().getBoolean("visible-by-default");
        preferencesRepository = new PreferencesRepository(databaseManager, defaultVisibility);
    }

    private void initializeServices() {
        playerService = new PlayerService(playerRepository);
        preferencesService = new PreferencesService(preferencesRepository);
        dunceService = new DunceService(dunceRepository, playerService, preferencesService, messageManager);
    }

    private void registerCommands() {
        Objects.requireNonNull(getCommand("dunce")).setExecutor(
            new DunceCommandNew(dunceService, playerService, messageManager));
        Objects.requireNonNull(getCommand("undunce")).setExecutor(
            new DunceCommandNew(dunceService, playerService, messageManager));
        Objects.requireNonNull(getCommand("dcon")).setExecutor(
            new ToggleCommandNew(preferencesService, messageManager));
        Objects.requireNonNull(getCommand("dcoff")).setExecutor(
            new ToggleCommandNew(preferencesService, messageManager));
        Objects.requireNonNull(getCommand("duncemenu")).setExecutor(
            new MenuCommand(guiBuilder));
        Objects.requireNonNull(getCommand("clearchat")).setExecutor(
            new ClearChatCommandNew(messageManager));
        Objects.requireNonNull(getCommand("duncereload")).setExecutor(
            new ReloadCommandNew(this, messageManager));
        Objects.requireNonNull(getCommand("duncelookup")).setExecutor(
            new LookupCommandNew(dunceService, playerService, messageManager));
        Objects.requireNonNull(getCommand("duncemigrate")).setExecutor(
            new MigrateCommand(schemaManager, messageManager));
    }

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
    }

    private void startExpiryChecker() {
        getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            dunceService.processExpiredDunces();
        }, 0L, 20L); // Run every second
    }

    private void loadConfig() {
        getConfig().options().copyDefaults(true);
        saveConfig();
    }

    private void loadWordsConfig() {
        File wordsFile = new File(getDataFolder(), "words.yml");
        if (!wordsFile.exists()) {
            try (InputStream in = getResource("words.yml")) {
                if (in != null) {
                    Files.copy(in, wordsFile.toPath());
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        wordsConfig = YamlConfiguration.loadConfiguration(wordsFile);
    }

    public FileConfiguration getWordsConfig() {
        return wordsConfig;
    }
}
```

### Step 2: Remove Old Files

After testing that everything works, delete these old files:
- `DunceCommand.java` (old)
- `DunceToggle.java` (old)
- `DunceLookupCommand.java` (old)
- `ClearChat.java` (old)
- `ReloadCommand.java` (old)
- `Events.java` (old)
- `Greentexter.java` (old)
- `DunceGUI.java` (old)
- `MySQLHandler.java` (old)
- `UserData.java` (old)
- `NBTHandler.java` (old - replaced by Bukkit's PersistentDataContainer)

## 🎯 Key Improvements

### Before vs After

#### Messages
**Before:**
```java
sender.sendMessage(ChatColor.RED + "Player not found!");
```

**After:**
```java
sender.sendMessage(messageManager.get("player_not_found", playerName));
```

#### Commands
**Before:**
```java
public class DunceCommand {
    public static void tryDunce(...) {
        UUID uuid = MySQLHandler.getUUIDByName(name);
        DunceChat.setDunced(uuid, ...);
    }
}
```

**After:**
```java
public class DunceCommandNew {
    private final DunceService dunceService;
    
    public DunceCommandNew(DunceService dunceService, ...) {
        this.dunceService = dunceService;
    }
    
    public void handleDunce(...) {
        Optional<UUID> uuid = playerService.getUuidByName(name);
        dunceService.duncePlayer(uuid.get(), ...);
    }
}
```

#### Event Listeners
**Before:**
```java
public class Events {
    public void onChat(AsyncPlayerChatEvent event) {
        if (DunceChat.isDunced(player)) {
            event.getRecipients().retainAll(DunceChat.getPlayersDunceChatVisible());
        }
    }
}
```

**After:**
```java
public class ChatListener {
    private final DunceService dunceService;
    private final PreferencesService preferencesService;
    
    public void onChat(AsyncPlayerChatEvent event) {
        if (dunceService.isDunced(playerUuid)) {
            Set<Player> recipients = preferencesService.getPlayersWithDunceChatVisible();
            event.getRecipients().retainAll(recipients);
        }
    }
}
```

## ✅ Testing Checklist

- [ ] Plugin starts without errors
- [ ] Database migrates successfully
- [ ] All commands work:
  - [ ] `/dunce <player>` - Dunces player
  - [ ] `/dunce <player> 1h` - Dunces with expiry
  - [ ] `/dunce <player> 1h reason` - Dunces with reason
  - [ ] `/undunce <player>` - Undunces player
  - [ ] `/dcon` - Shows dunce chat
  - [ ] `/dcoff` - Hides dunce chat
  - [ ] `/duncemenu` - Opens GUI
  - [ ] `/duncelookup <player>` - Shows dunce info
  - [ ] `/clearchat` - Clears chat
  - [ ] `/duncereload` - Reloads config and messages
  - [ ] `/duncemigrate` - Runs migration
- [ ] Chat features work:
  - [ ] Dunced players only visible to those with visibility on
  - [ ] Auto-dunce on bad words works
  - [ ] Greentext works
  - [ ] Dunce star works (if enabled)
- [ ] GUI works:
  - [ ] Opens correctly
  - [ ] Shows dunce info when dunced
  - [ ] Toggles work
  - [ ] Items update when clicked
- [ ] Expiry checker works:
  - [ ] Players are auto-undunced when time expires

## 🎉 Benefits Achieved

1. **Clean Code**: No more spaghetti!
2. **Maintainable**: Easy to understand and modify
3. **Testable**: Can write unit tests
4. **Configurable**: All messages in properties file
5. **Fast**: Connection pooling and optimized queries
6. **Professional**: Industry-standard patterns

## 📚 Documentation

All documentation is in place:
- `REFACTORING.md` - Technical details
- `REFACTORING_SUMMARY.md` - Overview
- `ARCHITECTURE_DIAGRAM.md` - Visual guides
- `IMPLEMENTATION_GUIDE.md` - Integration steps
- This file - Migration guide

---

**You're now ready to deploy the refactored DunceChat! 🚀**

