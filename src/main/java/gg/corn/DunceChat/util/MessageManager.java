package gg.corn.DunceChat.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Map;
import java.util.Properties;

/**
 * Handles loading and formatting of messages from messages.properties using Adventure API
 * Uses legacy color codes (&c, &a, etc.) for simplicity and compatibility
 */
public class MessageManager {

    private final Properties messages;
    private final Plugin plugin;
    private final LegacyComponentSerializer legacySerializer;
    private TextColor baseColor;
    private TextColor highlightColor;
    private String baseColorCode;
    private String highlightColorCode;

    public MessageManager(Plugin plugin) {
        this.plugin = plugin;
        this.messages = new Properties();
        // Use ampersand for color codes in messages.properties
        // Enable hex color support with &#RRGGBB format
        this.legacySerializer = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .build();
        loadColors();
        loadMessages();
    }

    /**
     * Load colors from config.yml
     */
    private void loadColors() {
        FileConfiguration config = plugin.getConfig();

        // Get color values from config - support both hex (#RRGGBB) and named colors
        String baseColorStr = config.getString("baseColor", "gray");
        String highlightColorStr = config.getString("highlightColor", "gold");

        baseColor = parseColor(baseColorStr, NamedTextColor.GRAY);
        highlightColor = parseColor(highlightColorStr, NamedTextColor.GOLD);

        // Generate color codes for string replacement
        baseColorCode = getColorCode(baseColor);
        highlightColorCode = getColorCode(highlightColor);

        plugin.getLogger().info("Loaded colors - Base: " + baseColorStr + ", Highlight: " + highlightColorStr);
    }

    /**
     * Parse a color string (hex or named) into a TextColor
     */
    private TextColor parseColor(String colorStr, TextColor defaultColor) {
        if (colorStr == null || colorStr.isEmpty()) {
            return defaultColor;
        }

        // Try hex format
        if (colorStr.startsWith("#")) {
            try {
                return TextColor.fromHexString(colorStr);
            } catch (Exception e) {
                return defaultColor;
            }
        }

        // Try named color
        NamedTextColor named = NamedTextColor.NAMES.value(colorStr.toLowerCase().replace(" ", "_"));
        return named != null ? named : defaultColor;
    }

    /**
     * Get the legacy color code for a TextColor
     * For hex colors, returns the &#RRGGBB format
     */
    private String getColorCode(TextColor color) {
        if (color instanceof NamedTextColor named) {
            return switch (named.toString()) {
                case "black" -> "&0";
                case "dark_blue" -> "&1";
                case "dark_green" -> "&2";
                case "dark_aqua" -> "&3";
                case "dark_red" -> "&4";
                case "dark_purple" -> "&5";
                case "gold" -> "&6";
                case "gray" -> "&7";
                case "dark_gray" -> "&8";
                case "blue" -> "&9";
                case "green" -> "&a";
                case "aqua" -> "&b";
                case "red" -> "&c";
                case "light_purple" -> "&d";
                case "yellow" -> "&e";
                case "white" -> "&f";
                default -> "&7";
            };
        }
        // For hex colors, use &#RRGGBB format (supported by LegacyComponentSerializer)
        return "&#" + color.asHexString().substring(1); // Remove the # from asHexString() result
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
     * Get a raw message string (without parsing)
     */
    public String getRaw(String key) {
        String message = messages.getProperty(key, "&cMissing message: " + key);
        return applyColorPlaceholders(message);
    }

    /**
     * Get a formatted message string with placeholders (without parsing)
     */
    public String getRaw(String key, Object... args) {
        String message = getRaw(key);
        return formatMessage(message, args);
    }

    /**
     * Apply {base_color} and {highlight_color} placeholders
     */
    private String applyColorPlaceholders(String message) {
        return message
            .replace("{base_color}", baseColorCode)
            .replace("{highlight_color}", highlightColorCode);
    }

    /**
     * Get a Component message
     */
    public Component get(String key) {
        String message = getRaw(key);
        return legacySerializer.deserialize(message);
    }

    /**
     * Get a Component message with placeholders
     */
    public Component get(String key, Object... args) {
        String message = getRaw(key, args);
        return legacySerializer.deserialize(message);
    }

    /**
     * Get a Component message with a Component placeholder
     * The component will be inserted at {placeholder_name} in the message
     */
    public Component getWithComponent(String key, String placeholderName, Component component) {
        String message = getRaw(key);
        String placeholder = "<" + placeholderName + ">";

        // Split message at the placeholder
        int index = message.indexOf(placeholder);
        if (index == -1) {
            // Placeholder not found, just return the message
            return legacySerializer.deserialize(message);
        }

        String before = message.substring(0, index);
        String after = message.substring(index + placeholder.length());

        return legacySerializer.deserialize(before)
            .append(component)
            .append(legacySerializer.deserialize(after));
    }

    /**
     * Get a Component message with multiple Component placeholders
     * Components will be inserted at <placeholder_name> in the message
     */
    public Component getWithComponents(String key, Map<String, Component> placeholders) {
        String message = getRaw(key);

        // Build the component by splitting on placeholders and inserting components
        Component result = Component.empty();
        String remaining = message;

        while (!remaining.isEmpty()) {
            // Find the next placeholder
            int earliestIndex = -1;
            String earliestPlaceholder = null;
            Component earliestComponent = null;

            for (Map.Entry<String, Component> entry : placeholders.entrySet()) {
                String placeholder = "<" + entry.getKey() + ">";
                int index = remaining.indexOf(placeholder);
                if (index != -1 && (earliestIndex == -1 || index < earliestIndex)) {
                    earliestIndex = index;
                    earliestPlaceholder = placeholder;
                    earliestComponent = entry.getValue();
                }
            }

            if (earliestIndex == -1) {
                // No more placeholders, append the rest
                result = result.append(legacySerializer.deserialize(remaining));
                break;
            }

            // Append text before placeholder
            if (earliestIndex > 0) {
                result = result.append(legacySerializer.deserialize(remaining.substring(0, earliestIndex)));
            }

            // Append the component
            result = result.append(earliestComponent);

            // Continue with the rest
            remaining = remaining.substring(earliestIndex + earliestPlaceholder.length());
        }

        return result;
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
     * Format a message with placeholders {0}, {1}, etc.
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

    /**
     * Get the base color
     */
    public TextColor getBaseColor() {
        return baseColor;
    }

    /**
     * Get the highlight color
     */
    public TextColor getHighlightColor() {
        return highlightColor;
    }
}
