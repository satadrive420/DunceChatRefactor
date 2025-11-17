package gg.corn.DunceChat.model;

import java.util.UUID;

/**
 * Represents player preferences for dunce chat
 */
public class PlayerPreferences {
    private final UUID playerUuid;
    private boolean dunceChatVisible;
    private boolean inDunceChat;

    public PlayerPreferences(UUID playerUuid) {
        this.playerUuid = playerUuid;
        this.dunceChatVisible = false;
        this.inDunceChat = false;
    }

    public PlayerPreferences(UUID playerUuid, boolean dunceChatVisible, boolean inDunceChat) {
        this.playerUuid = playerUuid;
        this.dunceChatVisible = dunceChatVisible;
        this.inDunceChat = inDunceChat;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public boolean isDunceChatVisible() {
        return dunceChatVisible;
    }

    public void setDunceChatVisible(boolean dunceChatVisible) {
        this.dunceChatVisible = dunceChatVisible;
    }

    public boolean isInDunceChat() {
        return inDunceChat;
    }

    public void setInDunceChat(boolean inDunceChat) {
        this.inDunceChat = inDunceChat;
    }
}

