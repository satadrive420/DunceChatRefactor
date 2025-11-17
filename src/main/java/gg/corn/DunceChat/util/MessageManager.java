package gg.corn.DunceChat.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Properties;

/**
 * Handles loading and formatting of messages from messages.properties
 */
public class MessageManager {

    private final Properties messages;
    private final Plugin plugin;

    public MessageManager(Plugin plugin) {
        this.plugin = plugin;
        this.messages = new Properties();
        loadMessages();
    }

    /**
     * Load messages from messages.properties
     */
    private void loadMessages() {
        File messagesFile = new File(plugin.getDataFolder(), "messages.properties");

        // Copy default if not exists
        if (!messagesFile.exists()) {
            try (InputStream in = plugin.getResource("messages.properties")) {
                if (in != null) {
                    Files.copy(in, messagesFile.toPath());
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to create messages.properties file!");
                e.printStackTrace();
            }
        }

        // Load properties
        try (InputStream in = Files.newInputStream(messagesFile.toPath())) {
            messages.load(in);
            plugin.getLogger().info("Loaded " + messages.size() + " messages from messages.properties");
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to load messages.properties!");
            e.printStackTrace();
        }
    }

    /**
     * Reload messages from file
     */
    public void reload() {
        messages.clear();
        loadMessages();
    }

    /**
     * Get a raw message string
     */
    public String getRaw(String key) {
        return messages.getProperty(key, "§cMissing message: " + key);
    }

    /**
     * Get a formatted message string with placeholders
     */
    public String getRaw(String key, Object... args) {
        String message = getRaw(key);
        return formatMessage(message, args);
    }

    /**
     * Get a Component message
     */
    public Component get(String key) {
        String message = getRaw(key);
        return LegacyComponentSerializer.legacySection().deserialize(message);
    }

    /**
     * Get a Component message with placeholders
     */
    public Component get(String key, Object... args) {
        String message = getRaw(key, args);
        return LegacyComponentSerializer.legacySection().deserialize(message);
    }

    /**
     * Get a prefixed message
     */
    public Component getPrefixed(String key) {
        Component prefix = get("prefix");
        Component message = get(key);
        return prefix.append(Component.space()).append(message);
    }

    /**
     * Get a prefixed message with placeholders
     */
    public Component getPrefixed(String key, Object... args) {
        Component prefix = get("prefix");
        Component message = get(key, args);
        return prefix.append(Component.space()).append(message);
    }

    /**
     * Format a message with placeholders
     */
    private String formatMessage(String message, Object... args) {
        if (args == null || args.length == 0) {
            return message;
        }

        for (int i = 0; i < args.length; i++) {
            message = message.replace("{" + i + "}", String.valueOf(args[i]));
        }

        return message;
    }

    /**
     * Check if a message key exists
     */
    public boolean hasMessage(String key) {
        return messages.containsKey(key);
    }
}

