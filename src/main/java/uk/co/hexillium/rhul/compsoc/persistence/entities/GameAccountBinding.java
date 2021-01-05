package uk.co.hexillium.rhul.compsoc.persistence.entities;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class GameAccountBinding {

    private int id;
    private String gameId;
    private long memberId;
    private long guildId;
    private String gameUsername;
    private String gameUserId;
    private boolean userBanned;

    public GameAccountBinding(int id, String gameId, long memberId, long guildId, String gameUsername, String gameUserId, boolean userBanned) {
        this.id = id;
        this.gameId = gameId;
        this.memberId = memberId;
        this.guildId = guildId;
        this.gameUsername = gameUsername;
        this.gameUserId = gameUserId;
        this.userBanned = userBanned;
    }

    public GameAccountBinding(int id, String gameId, String memberId, String guildId, String gameUsername, String gameUserId, boolean userBanned) {
        this(id, gameId, Long.parseLong(memberId), Long.parseLong(guildId), gameUsername, gameUserId, userBanned);
    }

    public GameAccountBinding(String id, String gameId, String memberId, String guildId, String gameUsername, String gameUserId, boolean userBanned) {
        this(Integer.parseInt(id), gameId, memberId, guildId, gameUsername, gameUserId, userBanned);
    }

    public GameAccountBinding(String gameId, String memberId, String guildId, String gameUsername, String gameUserId, boolean userBanned) {
        this(-1, gameId, memberId, guildId, gameUsername, gameUserId, userBanned);
    }

    @JsonCreator
    public GameAccountBinding(@JsonProperty("gameId") String gameId,
                              @JsonProperty("memberId") String memberId,
                              @JsonProperty("guildId") String guildId,
                              @JsonProperty("gameUsername") String gameUsername,
                              @JsonProperty("gameUserId") String gameUserId) {
        this(gameId, memberId, guildId, gameUsername, gameUserId, false);
    }

    public int getBindingId() {
        return id;
    }

    public String getGameId() {
        return gameId;
    }

    public long getMemberId() {
        return memberId;
    }

    public long getGuildId() {
        return guildId;
    }

    public String getMemberId_str() {
        return String.valueOf(memberId);
    }

    public String getGuildId_str() {
        return String.valueOf(guildId);
    }

    public String getGameUsername() {
        return gameUsername;
    }

    public String getGameUserId() {
        return gameUserId;
    }

    public boolean isUserBanned() {
        return userBanned;
    }
}
