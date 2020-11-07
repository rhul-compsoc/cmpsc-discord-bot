package uk.co.hexillium.rhul.compsoc.persistence;

import com.zaxxer.hikari.HikariDataSource;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import uk.co.hexillium.rhul.compsoc.persistence.entities.GuildXPData;
import uk.co.hexillium.rhul.compsoc.persistence.entities.MemberXPData;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ExperienceStorage {

    private HikariDataSource source;
    private static final Logger logger = LogManager.getLogger(ExperienceStorage.class);

    private static final String FETCH_ALL_MEMBERS = "select mi.nickname, mi.username, mi.discrim, mi.avatar_url, ml.member_id, ml.guild_id, xp_total, num_messages, recent_xp_gain, score  " +
            "from member_levels ml " +
            "left join member_information mi on ml.member_id = mi.member_id and ml.guild_id = mi.guild_id " +
            "left join numvember nu on ml.member_id = nu.member_snowflake " +
            "where ml.guild_id = ? and hidden = false;";

   /*

   select xp_total, num_messages, recent_xp_gain from member_levels where member_id = ? and guild_id = ?;

insert into member_levels(member_id, guild_id, xp_total, num_messages) values (?, ?, ?, 0);

update member_levels set xp_total = xp_total + ?, num_messages = num_messages + ?, recent_xp_gain = ?;

    */

    private static final String getMemberXP = "select xp_total, num_messages, recent_xp_gain from member_levels where member_id = ? and guild_id = ?;";
    private static final String newMemberXP = "insert into member_levels(member_id, guild_id, xp_total, num_messages, recent_xp_gain) values (?, ?, ?, 0, ?);";
    private static final String updateMemberXPAndMessages = "update member_levels set xp_total = xp_total + ?, num_messages = num_messages + 1, recent_xp_gain = ? where member_id = ? and guild_id = ?;";
    private static final String updateMemberMessages = "update member_levels set num_messages = num_messages + 1  where member_id = ? and guild_id = ?;";

    /*

                statement.setLong(1, member.getIdLong());
                statement.setLong(2, member.getGuild().getIdLong());
                statement.setString(3, member.getNickname());
                statement.setString(4, member.getUser().getName());
                statement.setString(5, member.getUser().getDiscriminator());
                statement.setString(6, member.getUser().getAvatarUrl());
                statement.setString(7, member.getNickname());
                statement.setString(8, member.getUser().getName());
                statement.setString(9, member.getUser().getDiscriminator());
                statement.setString(10, member.getUser().getAvatarUrl());
     */

    private static final String insertMembers = "insert into member_information(member_id, guild_id, nickname, username, discrim, avatar_url) VALUES (?, ?, ?, ?, ?, ?) on conflict (member_id, guild_id) do update " +
            "set nickname = ?, username = ?, discrim = ?, avatar_url = ?" +
            ";";
    private static final String updateMember = "update member_information set nickname = ?, username = ?, discrim = ?, avatar_url = ? where member_id = ? and guild_id = ?;";
    private static final String updateUser = "update member_information set username = ?, discrim = ?, avatar_url = ? where member_id = ?;";

    ExperienceStorage(HikariDataSource source){
        this.source = source;
    }

    /*
    public List<MemberXPData> getMemberXPData(long guildID){
        try (Connection connection = source.getConnection();
             PreparedStatement statement = connection.prepareStatement(FETCH_ALL_MEMBERS) ){

            List<MemberXPData> data = new ArrayList<>();

            statement.setLong(1, guildID);
            try (ResultSet set = statement.executeQuery()) {

                while (set.next()) {
                    data.add(new MemberXPData(
                            set.getString("nickname"),
                            set.getString("username"),
                            set.getString("discrim"),
                            set.getString("avatar_url"),
                            set.getLong("member_id"),
                            set.getLong("xp_total"),
                            set.getLong("num_messages"),
                            set.getLong("guild_id"),
                            set.getLong("recent_xp_gain")
                    ));
                }
            }
            return data;
        } catch (SQLException ex){
            logger.error("Failed to fetch user data", ex);
            return null;
        }
    }
     */

    public GuildXPData getGuildData(long guildID, Guild guild){
        try (Connection connection = source.getConnection();
             PreparedStatement statement = connection.prepareStatement(FETCH_ALL_MEMBERS) ){

            List<MemberXPData> data = new ArrayList<>();

            statement.setLong(1, guildID);
            try (ResultSet set = statement.executeQuery()) {

                while (set.next()) {
                    data.add(new MemberXPData(
                            set.getString("nickname"),
                            set.getString("username"),
                            set.getString("discrim"),
                            set.getString("avatar_url"),
                            set.getLong("member_id"),
                            set.getLong("xp_total"),
                            set.getLong("num_messages"),
                            set.getLong("guild_id"),
                            set.getLong("recent_xp_gain"),
                            set.getInt("score")
                    ));
                }
            }
            return new GuildXPData(data, guild.getName(), guild.getIconUrl(), guild.getIdLong());
        } catch (SQLException ex){
            logger.error("Failed to fetch user data", ex);
            return null;
        }
    }

    public void newMemberXP(long memberID, long guildID, int xp, long recentGain){
        try (Connection connection = source.getConnection();
             PreparedStatement statement = connection.prepareStatement(newMemberXP) ){
            statement.setLong(1, memberID);
            statement.setLong(2, guildID);
            statement.setInt(3, xp);
            statement.setLong(4, recentGain);
            statement.executeUpdate();
        } catch (SQLException ex){
            logger.error("Failed to insert XP", ex);
        }
    }

    public void updateUserXP(long memberID, long guildID, int xpGain, long recentGain){
        try (Connection connection = source.getConnection();
             PreparedStatement statement = connection.prepareStatement(updateMemberXPAndMessages) ){
            statement.setInt(1, xpGain);
            statement.setLong(2, recentGain);
            statement.setLong(3, memberID);
            statement.setLong(4, guildID);
            statement.executeUpdate();
        } catch (SQLException ex){
            logger.error("Failed to update XP", ex);
        }
    }

    public void updateUserMessages(long memberID, long guildID){
        try (Connection connection = source.getConnection();
             PreparedStatement statement = connection.prepareStatement(updateMemberMessages) ){
            statement.setLong(1, memberID);
            statement.setLong(2, guildID);
            statement.executeUpdate();
        } catch (SQLException ex){
            logger.error("Failed to update messages", ex);
        }
    }


    public MemberXPData getMemberXP(long guildID, long memberID){
        try (Connection connection = source.getConnection();
             PreparedStatement statement = connection.prepareStatement(getMemberXP) ){
            statement.setLong(1, memberID);
            statement.setLong(2, guildID);
            try (ResultSet resultSet = statement.executeQuery()){
                if (resultSet.next()){
                    return new MemberXPData(
                            null, null, null, null,
                            memberID, resultSet.getLong("xp_total"), resultSet.getLong("num_messages"),
                            guildID, resultSet.getLong("recent_xp_gain"), 0
                    );
                }
            }
        } catch (SQLException ex){
            logger.error("Failed to fetch XP", ex);
        }
        return null;
    }

    public void importMembers(Iterable<Member> members){
        try (Connection connection = source.getConnection();
             PreparedStatement statement = connection.prepareStatement(insertMembers) ){
            for (Member member : members) {
                //member_id, guild_id, nickname, username, discrim
                statement.setLong(1, member.getIdLong());
                statement.setLong(2, member.getGuild().getIdLong());
                statement.setString(3, member.getNickname());
                statement.setString(4, member.getUser().getName());
                statement.setString(5, member.getUser().getDiscriminator());
                statement.setString(6, member.getUser().getAvatarUrl());
                statement.setString(7, member.getNickname());
                statement.setString(8, member.getUser().getName());
                statement.setString(9, member.getUser().getDiscriminator());
                statement.setString(10, member.getUser().getAvatarUrl());
                statement.addBatch();
            }
            statement.executeBatch();
        } catch (SQLException ex){
            logger.error("Failed to insert members", ex);
        }
    }

    public void importMembers(Member member){
        try (Connection connection = source.getConnection();
             PreparedStatement statement = connection.prepareStatement(insertMembers) ){
            //member_id, guild_id, nickname, username, discrim
            statement.setLong(1, member.getIdLong());
            statement.setLong(2, member.getGuild().getIdLong());
            statement.setString(3, member.getNickname());
            statement.setString(4, member.getUser().getName());
            statement.setString(5, member.getUser().getDiscriminator());
            statement.setString(6, member.getUser().getAvatarUrl());
            statement.setString(7, member.getNickname());
            statement.setString(8, member.getUser().getName());
            statement.setString(9, member.getUser().getDiscriminator());
            statement.setString(10, member.getUser().getAvatarUrl());
            statement.executeUpdate();
        } catch (SQLException ex){
            logger.error("Failed to insert member", ex);
        }
    }

    public void updateMember(Member member){
        try (Connection connection = source.getConnection();
             PreparedStatement statement = connection.prepareStatement(updateMember) ){
            //nick, user, discrim, member_id, guild_id
            statement.setString(1, member.getNickname());
            statement.setString(2, member.getUser().getName());
            statement.setString(3, member.getUser().getDiscriminator());
            statement.setString(4, member.getUser().getAvatarUrl());
            //query
            statement.setLong(5, member.getIdLong());
            statement.setLong(6, member.getGuild().getIdLong());

            statement.executeUpdate();
        } catch (SQLException ex){
            logger.error("Failed to update member", ex);
        }
    }

    public void updateUser(User user){
        try (Connection connection = source.getConnection();
             PreparedStatement statement = connection.prepareStatement(updateUser) ){
            //nick, user, discrim, member_id, guild_id
            statement.setString(1, user.getName());
            statement.setString(2, user.getDiscriminator());
            statement.setString(3, user.getAvatarUrl());
            statement.setLong(4, user.getIdLong());

            statement.executeUpdate();
        } catch (SQLException ex){
            logger.error("Failed to update user", ex);
        }
    }

}
