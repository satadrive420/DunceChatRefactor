package gg.corn.DunceChat.service;

import gg.corn.DunceChat.model.Player;
import gg.corn.DunceChat.repository.PlayerRepository;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;

import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for player-related operations
 */
public class PlayerService {

    private final PlayerRepository playerRepository;
    private final FileConfiguration config;

    public PlayerService(PlayerRepository playerRepository, FileConfiguration config) {
        this.playerRepository = playerRepository;
        this.config = config;
    }

    /**
     * Get a player by UUID
     */
    public Optional<Player> getPlayer(UUID uuid) {
        return playerRepository.findByUuid(uuid);
    }

    /**
     * Get a player by username
     */
    public Optional<Player> getPlayerByUsername(String username) {
        return playerRepository.findByUsername(username);
    }

    /**
     * Update or create player on join
     */
    public void handlePlayerJoin(org.bukkit.entity.Player bukkitPlayer) {
        UUID uuid = bukkitPlayer.getUniqueId();
        String username = bukkitPlayer.getName();
        Timestamp now = new Timestamp(System.currentTimeMillis());

        Optional<Player> existingPlayer = playerRepository.findByUuid(uuid);

        if (existingPlayer.isPresent()) {
            Player player = existingPlayer.get();
            player.setUsername(username);
            player.setLastJoin(now);
            playerRepository.save(player);
        } else {
            Player player = new Player(uuid, username);
            player.setFirstJoin(now);
            player.setLastJoin(now);
            playerRepository.save(player);
        }
    }

    /**
     * Update player quit time
     */
    public void handlePlayerQuit(org.bukkit.entity.Player bukkitPlayer) {
        Timestamp now = new Timestamp(System.currentTimeMillis());
        playerRepository.updateQuitTime(bukkitPlayer.getUniqueId(), now);
    }

    /**
     * Get UUID by player name
     */
    public Optional<UUID> getUuidByName(String username) {
        return playerRepository.findByUsername(username)
                .map(Player::getUuid);
    }

    /**
     * Get username by UUID
     */
    public Optional<String> getNameByUuid(UUID uuid) {
        return playerRepository.findByUuid(uuid)
                .map(Player::getUsername);
    }

    /**
     * Get display name for a player, using PlaceholderAPI if configured
     * Returns a Component to preserve colors from PlaceholderAPI
     */
    public Component getDisplayNameComponent(org.bukkit.entity.Player player) {
        String papiDisplayName = config.getString("display-name-placeholder");
        // Check if PlaceholderAPI is present AND the placeholder is configured (not null/empty)
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null
            && papiDisplayName != null
            && !papiDisplayName.trim().isEmpty()) {
            String resolved = PlaceholderAPI.setPlaceholders(player, papiDisplayName);
            // PlaceholderAPI can return either § (section) or & (ampersand) color codes
            // Try ampersand first (most common), then section
            Component legacyComponent;
            if (resolved.contains("&")) {
                legacyComponent = LegacyComponentSerializer.legacyAmpersand().deserialize(resolved);
            } else {
                legacyComponent = LegacyComponentSerializer.legacySection().deserialize(resolved);
            }
            return legacyComponent;
        }
        return Component.text(player.getName());
    }

    /**
     * Get display name as plain string (without colors)
     */
    public String getDisplayName(org.bukkit.entity.Player player) {
        return PlainTextComponentSerializer.plainText().serialize(getDisplayNameComponent(player));
    }

    /**
     * Get prefix for a player, using PlaceholderAPI if configured
     * Returns a Component to preserve colors from PlaceholderAPI
     */
    public Component getPrefixComponent(org.bukkit.entity.Player player) {
        String papiPrefix = config.getString("prefix-placeholder");
        // Check if PlaceholderAPI is present AND the placeholder is configured (not null/empty)
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null
            && papiPrefix != null
            && !papiPrefix.trim().isEmpty()) {
            String resolved = PlaceholderAPI.setPlaceholders(player, papiPrefix);
            // PlaceholderAPI can return either § (section) or & (ampersand) color codes
            // Try ampersand first (most common), then section
            Component legacyComponent;
            if (resolved.contains("&")) {
                legacyComponent = LegacyComponentSerializer.legacyAmpersand().deserialize(resolved);
            } else {
                legacyComponent = LegacyComponentSerializer.legacySection().deserialize(resolved);
            }
            return legacyComponent;
        }
        return Component.empty();
    }

    /**
     * Get prefix as plain string (without colors)
     */
    public String getPrefix(org.bukkit.entity.Player player) {
        return PlainTextComponentSerializer.plainText().serialize(getPrefixComponent(player));
    }
}

