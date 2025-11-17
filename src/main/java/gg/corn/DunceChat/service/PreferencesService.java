package gg.corn.DunceChat.service;

import gg.corn.DunceChat.model.PlayerPreferences;
import gg.corn.DunceChat.repository.PreferencesRepository;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Service for player preferences operations
 */
public class PreferencesService {

    private final PreferencesRepository preferencesRepository;

    public PreferencesService(PreferencesRepository preferencesRepository) {
        this.preferencesRepository = preferencesRepository;
    }

    /**
     * Get player preferences
     */
    public PlayerPreferences getPreferences(UUID playerUuid) {
        return preferencesRepository.getPreferences(playerUuid);
    }

    /**
     * Check if player has dunce chat visible
     */
    public boolean isDunceChatVisible(UUID playerUuid) {
        return preferencesRepository.getPreferences(playerUuid).isDunceChatVisible();
    }

    /**
     * Set dunce chat visibility
     */
    public void setDunceChatVisible(UUID playerUuid, boolean visible) {
        preferencesRepository.setDunceChatVisible(playerUuid, visible);
    }

    /**
     * Check if player is in dunce chat
     */
    public boolean isInDunceChat(UUID playerUuid) {
        return preferencesRepository.getPreferences(playerUuid).isInDunceChat();
    }

    /**
     * Set in dunce chat status
     */
    public void setInDunceChat(UUID playerUuid, boolean inDunceChat) {
        preferencesRepository.setInDunceChat(playerUuid, inDunceChat);
    }

    /**
     * Get all online players who have dunce chat visible
     */
    public Set<Player> getPlayersWithDunceChatVisible() {
        Set<Player> players = new HashSet<>();

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (isDunceChatVisible(player.getUniqueId())) {
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
}

