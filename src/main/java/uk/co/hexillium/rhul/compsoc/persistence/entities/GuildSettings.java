package uk.co.hexillium.rhul.compsoc.persistence.entities;

public class GuildSettings {

    private long guildID;           //64-bit int

    private String prefix;          //1-5 chars

    private long modRoleID;         //64-bit int
    private long registeredRoleID;  //64-bit int
    private long jailRoleID;        //64-bit int

    private long logChannelID;      //64-bit int
    private long approvalChannelID; //64-bit int

    public GuildSettings(long guildID, String prefix, long modRoleID, long registeredRoleID, long jailRoleID,
                         long logChannelID, long approvalChannelID) {
        this.guildID = guildID;
        this.prefix = prefix;
        this.modRoleID = modRoleID;
        this.registeredRoleID = registeredRoleID;
        this.jailRoleID = jailRoleID;
        this.logChannelID = logChannelID;
        this.approvalChannelID = approvalChannelID;
    }

    public long getGuildID() {
        return guildID;
    }

    public String getPrefix() {
        return prefix;
    }

    public long getModRoleID() {
        return modRoleID;
    }

    public long getRegisteredRoleID() {
        return registeredRoleID;
    }

    public long getJailRoleID() {
        return jailRoleID;
    }

    public long getLogChannelID() {
        return logChannelID;
    }

    public long getApprovalChannelID() {
        return approvalChannelID;
    }
}
