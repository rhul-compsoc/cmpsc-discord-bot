package uk.co.hexillium.rhul.compsoc.persistence.entities;

import java.util.List;

public class GuildXPData {

    List<MemberXPData> leaderboard;
    String guildName;
    String guildAvatar;
    long guildID;

    public GuildXPData(List<MemberXPData> leaderboard, String guildName, String guildAvatar, long guildID) {
        this.leaderboard = leaderboard;
        this.guildName = guildName;
        this.guildAvatar = guildAvatar;
        this.guildID = guildID;
    }

    public List<MemberXPData> getLeaderboard() {
        return leaderboard;
    }

    public String getGuildName() {
        return guildName;
    }

    public String getGuildAvatar() {
        return guildAvatar;
    }

    public long getGuildID() {
        return guildID;
    }

    public String getGuildIDString() {
        return String.valueOf(guildID);
    }
}
