package uk.co.hexillium.rhul.compsoc.persistence.entities;

import javax.annotation.Nonnull;
import java.util.function.Consumer;

public class GuildSettings {

    private long guildID;           //64-bit int

    private String prefix;          //1-5 chars

    private long modRoleID;         //64-bit int
    private long adminRoleID;       //64-bit int
    private long registeredRoleID;  //64-bit int
    private long jailRoleID;        //64-bit int

    private long logChannelID;      //64-bit int
    private long joinChannelID;     //64-bit int
    private long approvalChannelID; //64-bit int

    static final private String DEFAULT_prefix = "!";          //1-5 chars

    static final private long DEFAULT_modRoleID = 0;         //64-bit int
    static final private long DEFAULT_adminRoleID = 0;       //64-bit int
    static final private long DEFAULT_registeredRoleID = 0;  //64-bit int
    static final private long DEFAULT_jailRoleID = 0;        //64-bit int

    static final private long DEFAULT_logChannelID = 0;      //64-bit int
    static final private long DEFAULT_joinChannelID = 0;     //64-bit int
    static final private long DEFAULT_approvalChannelID = 0; //64-bit int

    /**
     * Initialise a GuildSettings.  Bear in mind this will not actually modify anything until pushed to the database.
     * {@link uk.co.hexillium.rhul.compsoc.persistence.GuildData#setGuildData(long, GuildSettings, Runnable, Consumer)}<br>
     *     this can be accessed from a static member in {@link uk.co.hexillium.rhul.compsoc.persistence.Database}
     * @param guildID The ID of the guild these settings are for
     * @param prefix the String of the command prefix - must be between 1 (incl) and 5 (incl) chars.
     * @param adminRoleID the ID of the role on this guild that will be treated as admin
     * @param modRoleID the ID of the role on this guild that will be treated as moderators
     * @param registeredRoleID the ID of the role that registered members of this server will have
     * @param jailRoleID the ID of the role that jailed members will have
     *                   (this will effectively reduce their permissions such that they cannot interact with the server).
     * @param logChannelID the ID of the channel where this bot will log events such as member signups.
     * @param joinChannelID the ID of the channel where this bot will log join and leave events.
     * @param approvalChannelID the ID of the channel where new membership requests are sent to be approved.
     */
    public GuildSettings(long guildID, String prefix, long adminRoleID, long modRoleID, long registeredRoleID, long jailRoleID,
                         long logChannelID, long joinChannelID, long approvalChannelID) {
        this.guildID = guildID;
        this.prefix = prefix;
        this.modRoleID = modRoleID;
        this.adminRoleID = adminRoleID;
        this.registeredRoleID = registeredRoleID;
        this.jailRoleID = jailRoleID;
        this.logChannelID = logChannelID;
        this.joinChannelID = joinChannelID;
        this.approvalChannelID = approvalChannelID;
    }

    public static GuildSettings getDefault(long guildID){
        return new GuildSettings(guildID, DEFAULT_prefix, DEFAULT_adminRoleID, DEFAULT_modRoleID, DEFAULT_registeredRoleID, DEFAULT_jailRoleID,
                DEFAULT_logChannelID, DEFAULT_joinChannelID, DEFAULT_approvalChannelID);
    }

    /**
     * The ID of the guild these settings are for
     * @return 64-bit Discord Snowflake for the guildID
     */
    public long getGuildID() {
        return guildID;
    }

    /**
     * Gets the command prefix for this guild.
     * @return The command prefix, as a String
     */
    @Nonnull
    public String getPrefix() {
        return prefix;
    }

    /**
     * the ID of the role on this guild that will be treated as admin
     * @return 64-bit Discord Snowflake for the role
     */
    public long getAdminRoleID() {
        return adminRoleID;
    }

    /**
     * the ID of the role on this guild that will be treated as moderators
     * @return 64-bit Discord Snowflake for the role
     */
    public long getModRoleID() {
        return modRoleID;
    }

    /**
     *the ID of the role that registered members of this server will have
     * @return 64-bit Discord Snowflake for the role
     */
    public long getRegisteredRoleID() {
        return registeredRoleID;
    }

    /**
     * the ID of the role that jailed members will have
     * (this will effectively reduce their permissions such that they cannot interact with the server).
     * @return 64-bit Discord Snowflake for the role
     */
    public long getJailRoleID() {
        return jailRoleID;
    }

    /**
     * the ID of the channel where this bot will log events such as member signups, rejections, jails, new roles etc.
     * @return 64-bit Discord Snowflake for the channel
     */
    public long getLogChannelID() {
        return logChannelID;
    }

    /**
     * the ID of the channel where new membership requests are sent to be approved.
     * @return 64-bit Discord Snowflake for the channel
     */
    public long getApprovalChannelID() {
        return approvalChannelID;
    }

    /**
     * The ID of the channel used for sending information about new members joining.
     * @return 64-bit Discord Snowflake for this channel
     */
    public long getJoinChannelID(){
        return joinChannelID;
    }


    public void setGuildID(long guildID) {
        this.guildID = guildID;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public void setModRoleID(long modRoleID) {
        this.modRoleID = modRoleID;
    }

    public void setAdminRoleID(long adminRoleID) {
        this.adminRoleID = adminRoleID;
    }

    public void setRegisteredRoleID(long registeredRoleID) {
        this.registeredRoleID = registeredRoleID;
    }

    public void setJailRoleID(long jailRoleID) {
        this.jailRoleID = jailRoleID;
    }

    public void setLogChannelID(long logChannelID) {
        this.logChannelID = logChannelID;
    }

    public void setJoinChannelID(long joinChannelID) {
        this.joinChannelID = joinChannelID;
    }

    public void setApprovalChannelID(long approvalChannelID) {
        this.approvalChannelID = approvalChannelID;
    }

    /**
     * Checks whether or not the requirements for this guild have been set
     * @return true if all requirements have been set, else false.
     */
    public boolean isComplete(){
        return modRoleID != 0 &&
                 adminRoleID != 0 &&
                registeredRoleID != 0 &&
                jailRoleID != 0 &&
                logChannelID != 0 &&
                joinChannelID != 0 &&
                approvalChannelID != 0;

    }
}
