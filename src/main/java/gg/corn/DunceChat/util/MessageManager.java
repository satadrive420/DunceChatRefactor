package gg.corn.DunceChat.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Map;
import java.util.Properties;

/**
 * Handles loading and formatting of messages from messages.properties using MiniMessage
 */
public class MessageManager {

    private final Properties messages;
    private final Plugin plugin;
    private final MiniMessage miniMessage;
    private String baseColor;
    private String highlightColor;

    public MessageManager(Plugin plugin) {
        this.plugin = plugin;
        this.messages = new Properties();
        this.miniMessage = MiniMessage.miniMessage();
        loadColors();
        loadMessages();
    }

    /**
     * Load colors from config.yml
     */
    private void loadColors() {
        FileConfiguration config = plugin.getConfig();

        // Get color names from config - MiniMessage uses lowercase with underscores
        baseColor = config.getString("baseColor", "gray").toLowerCase().replace(" ", "_");
        highlightColor = config.getString("highlightColor", "gold").toLowerCase().replace(" ", "_");

        plugin.getLogger().info("Loaded colors - Base: " + baseColor + ", Highlight: " + highlightColor);
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
        loadColors();
        loadMessages();
    }

    /**
     * Get a raw message string (without MiniMessage parsing)
     */
    public String getRaw(String key) {
        return messages.getProperty(key, "<red>Missing message: " + key);
    }

    /**
     * Get a formatted message string with placeholders (without MiniMessage parsing)
     */
    public String getRaw(String key, Object... args) {
        String message = getRaw(key);
        return formatMessage(message, args);
    }

    /**
     * Get a Component message using MiniMessage
     */
    public Component get(String key) {
        String message = getRaw(key);
        return miniMessage.deserialize(message, getColorResolvers());
    }

    /**
     * Get a Component message with placeholders using MiniMessage
     */
    public Component get(String key, Object... args) {
        String message = getRaw(key, args);
        return miniMessage.deserialize(message, getColorResolvers());
    }

    /**
     * Get a Component message with Component placeholders
     * This allows passing Components (like from PlaceholderAPI) that preserve their colors
     *
     * @param key The message key
     * @param placeholderName The name of the placeholder (e.g., "player")
     * @param component The component to insert
     * @return The formatted message
     */
    public Component getWithComponent(String key, String placeholderName, Component component) {
        String message = getRaw(key);

        TagResolver resolver = TagResolver.resolver(
            getColorResolvers(),
            Placeholder.component(placeholderName, component)
        );

        return miniMessage.deserialize(message, resolver);
    }

    /**
     * Get a Component message with multiple Component placeholders
     *
     * @param key The message key
     * @param placeholders Map of placeholder names to components
     * @return The formatted message
     */
    public Component getWithComponents(String key, Map<String, Component> placeholders) {
        String message = getRaw(key);

        TagResolver.Builder resolverBuilder = TagResolver.builder();
        resolverBuilder.resolvers(getColorResolvers());

        for (Map.Entry<String, Component> entry : placeholders.entrySet()) {
            resolverBuilder.resolver(Placeholder.component(entry.getKey(), entry.getValue()));
        }

        return miniMessage.deserialize(message, resolverBuilder.build());
    }

    /**
     * Get tag resolvers for baseColor and highlightColor
     */
    private TagResolver getColorResolvers() {
        return TagResolver.resolver(
            TagResolver.resolver("base_color", (argumentQueue, context) ->
                net.kyori.adventure.text.minimessage.tag.Tag.styling(
                    net.kyori.adventure.text.format.NamedTextColor.NAMES.value(baseColor)
                )),
            TagResolver.resolver("highlight_color", (argumentQueue, context) ->
                net.kyori.adventure.text.minimessage.tag.Tag.styling(
                    net.kyori.adventure.text.format.NamedTextColor.NAMES.value(highlightColor)
                ))
        );
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

