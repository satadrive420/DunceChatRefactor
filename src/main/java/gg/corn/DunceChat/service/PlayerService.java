package gg.corn.DunceChat.service;

import gg.corn.DunceChat.model.Player;
import gg.corn.DunceChat.repository.PlayerRepository;

import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for player-related operations
 */
public class PlayerService {

    private final PlayerRepository playerRepository;

    public PlayerService(PlayerRepository playerRepository) {
        this.playerRepository = playerRepository;
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
}

