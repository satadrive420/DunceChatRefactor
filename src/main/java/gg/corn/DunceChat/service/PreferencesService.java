package gg.corn.DunceChat.service;

import gg.corn.DunceChat.model.PlayerPreferences;
import gg.corn.DunceChat.repository.PreferencesRepository;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for player preferences operations
 * Uses in-memory caching to minimize database queries
 *
 * Memory optimization notes:
 * - Only online players are cached (cleaned up on quit)
 * - Uses ConcurrentHashMap for thread safety without locking overhead
 * - Maintains a live set of visible players to avoid iteration on every chat
 */
public class PreferencesService {

    private final PreferencesRepository preferencesRepository;

    // Cache for player preferences - only caches online players
    // Memory: ~100 bytes per player (UUID + 2 booleans + object overhead)
    private final Map<UUID, PlayerPreferences> preferencesCache = new ConcurrentHashMap<>();

    // Live set of players with dunce chat visible - avoids O(n) iteration on every chat message
    // Memory: ~40 bytes per UUID reference
    private final Set<UUID> dunceChatVisiblePlayers = ConcurrentHashMap.newKeySet();

    // Live set of players in dunce chat mode
    private final Set<UUID> inDunceChatPlayers = ConcurrentHashMap.newKeySet();

    public PreferencesService(PreferencesRepository preferencesRepository) {
        this.preferencesRepository = preferencesRepository;
    }

    /**
     * Get player preferences (uses cache for online players)
     */
    public PlayerPreferences getPreferences(UUID playerUuid) {
        return preferencesCache.computeIfAbsent(playerUuid,
            uuid -> preferencesRepository.getPreferences(uuid));
    }

    /**
     * Check if player has dunce chat visible (O(1) lookup)
     */
    public boolean isDunceChatVisible(UUID playerUuid) {
        // First check the fast set
        if (dunceChatVisiblePlayers.contains(playerUuid)) {
            return true;
        }
        // Fall back to cache/DB for players not yet loaded
        return getPreferences(playerUuid).isDunceChatVisible();
    }

    /**
     * Set dunce chat visibility
     */
    public void setDunceChatVisible(UUID playerUuid, boolean visible) {
        preferencesRepository.setDunceChatVisible(playerUuid, visible);

        // Update live set
        if (visible) {
            dunceChatVisiblePlayers.add(playerUuid);
        } else {
            dunceChatVisiblePlayers.remove(playerUuid);
        }

        // Update cache
        PlayerPreferences prefs = preferencesCache.get(playerUuid);
        if (prefs != null) {
            prefs.setDunceChatVisible(visible);
        }
    }

    /**
     * Check if player is in dunce chat (O(1) lookup)
     */
    public boolean isInDunceChat(UUID playerUuid) {
        // First check the fast set
        if (inDunceChatPlayers.contains(playerUuid)) {
            return true;
        }
        // Fall back to cache/DB for players not yet loaded
        return getPreferences(playerUuid).isInDunceChat();
    }

    /**
     * Set in dunce chat status
     */
    public void setInDunceChat(UUID playerUuid, boolean inDunceChat) {
        preferencesRepository.setInDunceChat(playerUuid, inDunceChat);

        // Update live set
        if (inDunceChat) {
            inDunceChatPlayers.add(playerUuid);
        } else {
            inDunceChatPlayers.remove(playerUuid);
        }

        // Update cache
        PlayerPreferences prefs = preferencesCache.get(playerUuid);
        if (prefs != null) {
            prefs.setInDunceChat(inDunceChat);
        }
    }

    /**
     * Get all online players who have dunce chat visible
     * Returns a view that resolves Player objects on demand
     */
    public Set<Player> getPlayersWithDunceChatVisible() {
        Set<Player> players = ConcurrentHashMap.newKeySet();

        for (UUID uuid : dunceChatVisiblePlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                players.add(player);
            }
        }

        return players;
    }

    /**
     * Toggle dunce chat visibility
     */
    public boolean toggleDunceChatVisibility(UUID playerUuid) {
        boolean current = isDunceChatVisible(playerUuid);
        boolean newValue = !current;
        setDunceChatVisible(playerUuid, newValue);
        return newValue;
    }

    /**
     * Toggle in dunce chat status
     */
    public boolean toggleInDunceChat(UUID playerUuid) {
        boolean current = isInDunceChat(playerUuid);
        boolean newValue = !current;
        setInDunceChat(playerUuid, newValue);
        return newValue;
    }

    /**
     * Clear cache entry for a player (call on quit to free memory)
     */
    public void invalidateCache(UUID playerUuid) {
        preferencesCache.remove(playerUuid);
        dunceChatVisiblePlayers.remove(playerUuid);
        inDunceChatPlayers.remove(playerUuid);
    }

    /**
     * Clear entire cache
     */
    public void clearCache() {
        preferencesCache.clear();
        dunceChatVisiblePlayers.clear();
        inDunceChatPlayers.clear();
    }

    /**
     * Pre-load preferences for a player (call on join)
     */
    public void loadIntoCache(UUID playerUuid) {
        PlayerPreferences prefs = preferencesRepository.getPreferences(playerUuid);
        preferencesCache.put(playerUuid, prefs);

        // Update live sets based on loaded preferences
        if (prefs.isDunceChatVisible()) {
            dunceChatVisiblePlayers.add(playerUuid);
        }
        if (prefs.isInDunceChat()) {
            inDunceChatPlayers.add(playerUuid);
        }
    }
}
