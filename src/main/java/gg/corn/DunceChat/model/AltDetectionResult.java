package gg.corn.DunceChat.model;

import java.sql.Timestamp;
import java.util.*;

/**
 * Model representing alt detection results for a player
 */
public class AltDetectionResult {

    private final UUID targetPlayer;
    private final String targetPlayerName;
    private final Set<AltAccount> directAlts;      // Same current IP
    private final Set<AltAccount> historicalAlts;  // Shared historical IP
    private final Map<String, Set<UUID>> ipToPlayers;  // IP -> Players mapping
    private final Map<UUID, Set<String>> playerToIPs;  // Player -> IPs mapping

    public AltDetectionResult(UUID targetPlayer, String targetPlayerName) {
        this.targetPlayer = targetPlayer;
        this.targetPlayerName = targetPlayerName;
        this.directAlts = new HashSet<>();
        this.historicalAlts = new HashSet<>();
        this.ipToPlayers = new HashMap<>();
        this.playerToIPs = new HashMap<>();
    }

    public UUID getTargetPlayer() {
        return targetPlayer;
    }

    public String getTargetPlayerName() {
        return targetPlayerName;
    }

    public Set<AltAccount> getDirectAlts() {
        return directAlts;
    }

    public Set<AltAccount> getHistoricalAlts() {
        return historicalAlts;
    }

    public Map<String, Set<UUID>> getIpToPlayers() {
        return ipToPlayers;
    }

    public Map<UUID, Set<String>> getPlayerToIPs() {
        return playerToIPs;
    }

    public void addDirectAlt(AltAccount alt) {
        directAlts.add(alt);
    }

    public void addHistoricalAlt(AltAccount alt) {
        historicalAlts.add(alt);
    }

    public void addIpMapping(String ip, UUID playerUuid) {
        ipToPlayers.computeIfAbsent(ip, k -> new HashSet<>()).add(playerUuid);
    }

    public void addPlayerIP(UUID playerUuid, String ip) {
        playerToIPs.computeIfAbsent(playerUuid, k -> new HashSet<>()).add(ip);
    }

    /**
     * Get all unique alt accounts (both direct and historical)
     */
    public Set<AltAccount> getAllAlts() {
        Set<AltAccount> all = new HashSet<>();
        all.addAll(directAlts);
        all.addAll(historicalAlts);
        return all;
    }

    /**
     * Get total count of unique alts
     */
    public int getTotalAltCount() {
        Set<UUID> uniqueUuids = new HashSet<>();
        for (AltAccount alt : directAlts) {
            uniqueUuids.add(alt.getPlayerUuid());
        }
        for (AltAccount alt : historicalAlts) {
            uniqueUuids.add(alt.getPlayerUuid());
        }
        return uniqueUuids.size();
    }

    /**
     * Represents a detected alt account
     */
    public static class AltAccount {
        private final UUID playerUuid;
        private final String playerName;
        private final Set<String> sharedIPs;
        private final Timestamp lastSeen;
        private final boolean isCurrentIPMatch;

        public AltAccount(UUID playerUuid, String playerName, Set<String> sharedIPs,
                         Timestamp lastSeen, boolean isCurrentIPMatch) {
            this.playerUuid = playerUuid;
            this.playerName = playerName;
            this.sharedIPs = sharedIPs;
            this.lastSeen = lastSeen;
            this.isCurrentIPMatch = isCurrentIPMatch;
        }

        public UUID getPlayerUuid() {
            return playerUuid;
        }

        public String getPlayerName() {
            return playerName;
        }

        public Set<String> getSharedIPs() {
            return sharedIPs;
        }

        public Timestamp getLastSeen() {
            return lastSeen;
        }

        public boolean isCurrentIPMatch() {
            return isCurrentIPMatch;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            AltAccount that = (AltAccount) o;
            return Objects.equals(playerUuid, that.playerUuid);
        }

        @Override
        public int hashCode() {
            return Objects.hash(playerUuid);
        }
    }
}

