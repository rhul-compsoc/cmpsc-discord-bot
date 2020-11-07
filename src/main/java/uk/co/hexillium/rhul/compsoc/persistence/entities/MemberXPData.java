package uk.co.hexillium.rhul.compsoc.persistence.entities;

public class MemberXPData {

    private String nickname;
    private String username;
    private String discrim;
    private long memberID;
    private long xpTotal;
    private long numMessages;
    private long guildID;
    private long recentMessage;
    private String avatarURL;
    private int score;

    public MemberXPData(String nickname, String username, String discrim, String avatarURL, long memberID, long xpTotal, long numMessages, long guildID, long recentMessage, int score) {
        this.nickname = nickname;
        this.username = username;
        this.discrim = discrim;
        this.memberID = memberID;
        this.xpTotal = xpTotal;
        this.numMessages = numMessages;
        this.guildID = guildID;
        this.recentMessage = recentMessage;
        this.avatarURL = avatarURL;
        this.score = score;
    }

    public String getNickname() {
        return nickname;
    }

    public String getUsername() {
        return username;
    }

    public String getDiscrim() {
        return discrim;
    }

    public long getMemberID() {
        return memberID;
    }

    public String getMemberIDString() {
        return String.valueOf(memberID);
    }

    public long getXpTotal() {
        return xpTotal;
    }

    public long getNumMessages() {
        return numMessages;
    }

    public long getGuildID() {
        return guildID;
    }

    public String getAvatarURL() {
        return avatarURL;
    }

    public String getGuildIDString() {
        return String.valueOf(guildID);
    }

    public int getBooleanScore() {
        return score;
    }

    public long getRecentMessage() {
        return recentMessage;
    }
}
