package gg.corn.DunceChat.model;

import java.sql.Timestamp;
import java.util.UUID;

/**
 * Represents a player in the system
 */
public class Player {
    private final UUID uuid;
    private String username;
    private Timestamp firstJoin;
    private Timestamp lastJoin;
    private Timestamp lastQuit;

    public Player(UUID uuid, String username) {
        this.uuid = uuid;
        this.username = username;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public Timestamp getFirstJoin() {
        return firstJoin;
    }

    public void setFirstJoin(Timestamp firstJoin) {
        this.firstJoin = firstJoin;
    }

    public Timestamp getLastJoin() {
        return lastJoin;
    }

    public void setLastJoin(Timestamp lastJoin) {
        this.lastJoin = lastJoin;
    }

    public Timestamp getLastQuit() {
        return lastQuit;
    }

    public void setLastQuit(Timestamp lastQuit) {
        this.lastQuit = lastQuit;
    }
}

