package gg.corn.DunceChat.model;

import java.sql.Timestamp;
import java.util.UUID;

/**
 * Represents a dunce record
 */
public class DunceRecord {
    private int id;
    private final UUID playerUuid;
    private boolean isDunced;
    private String reason;
    private UUID staffUuid;
    private Timestamp duncedAt;
    private Timestamp expiresAt;
    private Timestamp unduncedAt;
    private String triggerMessage;

    public DunceRecord(UUID playerUuid) {
        this.playerUuid = playerUuid;
        this.isDunced = false;
    }

    public DunceRecord(int id, UUID playerUuid, boolean isDunced, String reason,
                      UUID staffUuid, Timestamp duncedAt, Timestamp expiresAt, Timestamp unduncedAt, String triggerMessage) {
        this.id = id;
        this.playerUuid = playerUuid;
        this.isDunced = isDunced;
        this.reason = reason;
        this.staffUuid = staffUuid;
        this.duncedAt = duncedAt;
        this.expiresAt = expiresAt;
        this.unduncedAt = unduncedAt;
        this.triggerMessage = triggerMessage;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public boolean isDunced() {
        return isDunced;
    }

    public void setDunced(boolean dunced) {
        isDunced = dunced;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public UUID getStaffUuid() {
        return staffUuid;
    }

    public void setStaffUuid(UUID staffUuid) {
        this.staffUuid = staffUuid;
    }

    public Timestamp getDuncedAt() {
        return duncedAt;
    }

    public void setDuncedAt(Timestamp duncedAt) {
        this.duncedAt = duncedAt;
    }

    public Timestamp getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Timestamp expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Timestamp getUnduncedAt() {
        return unduncedAt;
    }

    public void setUnduncedAt(Timestamp unduncedAt) {
        this.unduncedAt = unduncedAt;
    }

    public String getTriggerMessage() {
        return triggerMessage;
    }

    public void setTriggerMessage(String triggerMessage) {
        this.triggerMessage = triggerMessage;
    }

    /**
     * Check if the dunce has expired
     */
    public boolean isExpired() {
        if (!isDunced || expiresAt == null) {
            return false;
        }
        return System.currentTimeMillis() > expiresAt.getTime();
    }
}

