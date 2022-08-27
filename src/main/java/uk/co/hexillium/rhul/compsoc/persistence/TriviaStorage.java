package uk.co.hexillium.rhul.compsoc.persistence;

import com.zaxxer.hikari.HikariDataSource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import uk.co.hexillium.rhul.compsoc.persistence.entities.ScoreHistory;
import uk.co.hexillium.rhul.compsoc.persistence.entities.TriviaScore;


import java.sql.*;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TriviaStorage {

    private static final Logger LOGGER = LogManager.getLogger(TriviaStorage.class);
    private final static String updateMemberScore =
            "insert into numvember (member_snowflake, score, season_id) values (?, ?, ?) " +
                    "on conflict (member_snowflake, season_id) " +
                    "do update set score = numvember.score + ?;";
    private final static String fetchLeaderboardForSeason =
            "select member_snowflake, score, username, discrim, RANK() over(order by score desc) as rank from numvember " +
                    "         left join member_information on member_snowflake = member_id " +
                    "where season_id = ? " +
                    "order by score desc offset ? fetch next 10 rows only;";
    private final static String fetchUserPos =
            "select * from (select member_snowflake, score, username, discrim, RANK() over(order by score desc) as rank from numvember " +
                    "         left join member_information on member_snowflake = member_id where season_id = ?) as ranks " +
                    " where ranks.member_snowflake = ? ;";

    private final static String logHistoryUpdate =
            "insert into numvember_history (guild_id, member_snowflake, point_time, season_num, score_modifier) values " +
                    " (?, ?, ?, ?, ?);";

    private final static String fetchScoreHistory = "with data as (select nh.member_snowflake, " +
            "                     nh.season_num, " +
            "                     nh.guild_id, " +
            "                     date_trunc('day', nh.point_time) as day, " +
            "                     sum(score_modifier)              as score " +
            "              from numvember_history nh " +
            "              where member_snowflake = any (?) " +
            "                and season_num = ? " +
            "                and guild_id = ? " +
            "              group by nh.season_num, nh.member_snowflake, nh.guild_id, day) " +
            " " +
            "select day, season_num, member_snowflake, score_data.guild_id, score::int[], mi.username || '#' || mi.discrim as name " +
            "from (select array_agg(day)   as day, " +
            "             season_num, " +
            "             member_snowflake, " +
            "             guild_id, " +
            "             array_agg(score) as score " +
            "      from (select day, " +
            "                   season_num, " +
            "                   member_snowflake, " +
            "                   guild_id, " +
            "                   sum(score) " +
            "                   over (partition by member_snowflake, season_num, guild_id order by day asc rows between unbounded preceding and current row) as score " +
            "            from data) as d " +
            "      group by member_snowflake, season_num, guild_id) score_data " +
            "         left join member_information mi " +
            "                   on score_data.guild_id = mi.guild_id and score_data.member_snowflake = mi.member_id; ";

    private final static String totalPages = "select count(*) as count from numvember where season_id = ?;";
    private HikariDataSource source;

    public TriviaStorage(HikariDataSource source) {
        this.source = source;
    }

    public ScoreHistory[] fetchScoreHistory(int season, long guildId, long... userIDs){
        if (userIDs.length == 0) return new ScoreHistory[0];
        try (Connection connection = source.getConnection();
             PreparedStatement statement = connection.prepareStatement(fetchScoreHistory)){

//            statement.setString(1, "(" + Arrays.stream(userIDs).mapToObj(Long::toString).collect(Collectors.joining(", ")) + ")");
            statement.setArray(1, connection.createArrayOf("bigint", Arrays.stream(userIDs).boxed().toArray()));
            statement.setInt(2, season);
            statement.setLong(3, guildId);

            ResultSet set = statement.executeQuery();

            List<ScoreHistory> scoreHistory = new ArrayList<>();

            while (set.next()){
                // day, season_num, member_snowflake, score_data.guild_id, score,  name
                scoreHistory.add(new ScoreHistory(
                        set.getLong("member_snowflake"),
                        set.getString("name"),
                        set.getLong("guild_id"),
                        set.getInt("season_num"),
                        convertSQLTimestamp((Timestamp[]) set.getArray("day").getArray()),
//                        set.getObject("day", OffsetDateTime[].class),
                        Arrays.stream((Integer[]) set.getArray("score").getArray()).mapToInt(Integer::intValue).toArray()
                ));
            }

            return scoreHistory.toArray(new ScoreHistory[0]);

        }catch (SQLException ex){
            LOGGER.error("Failed to fetch history", ex);
            return new ScoreHistory[0];
        }
    }

    private static OffsetDateTime[] convertSQLTimestamp(Timestamp[] times){
        OffsetDateTime[] out = new OffsetDateTime[times.length];
        for (int i = 0; i < times.length; i++){
            out[i] = OffsetDateTime.ofInstant(Instant.ofEpochMilli(times[i].getTime()), ZoneId.of("UTC"));
        }
        return out;
    }

    public void updateMemberScore(long memberSnowflake, long guildId, int change, int season) {
        try (Connection connection = source.getConnection()) {
            connection.prepareStatement("BEGIN TRANSACTION;");
            try (PreparedStatement statement = connection.prepareStatement(updateMemberScore)) {

                statement.setLong(1, memberSnowflake);
                statement.setInt(2, change);
                statement.setInt(3, season);
                statement.setInt(4, change);

                statement.executeUpdate();

            }

            try (PreparedStatement statement = connection.prepareStatement(logHistoryUpdate)){
                statement.setLong(1, guildId);
                statement.setLong(2, memberSnowflake);
                statement.setObject(3, OffsetDateTime.now());
                statement.setInt(4, season);
                statement.setInt(5, change);

                statement.executeUpdate();
            }
            connection.prepareStatement("COMMIT TRANSACTION;");
        } catch (SQLException ex) {
            LOGGER.error("Failed to update score", ex);
        }
    }

    public List<TriviaScore> fetchLeaderboard(int page, int seasonId) {
        List<TriviaScore> scores = new ArrayList<>();
        try (Connection connection = source.getConnection();
             PreparedStatement statement = connection.prepareStatement(fetchLeaderboardForSeason)) {

            statement.setInt(1, seasonId);
            statement.setInt(2, page * 10);

            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    scores.add(new TriviaScore(set.getLong("member_snowflake"), set.getString("username") + "#" + set.getString("discrim"),
                            set.getInt("score"),
                            set.getInt("rank")));
                }
            }

        } catch (SQLException ex) {
            LOGGER.error("Failed to fetch scores", ex);
        }
        return scores;
    }

    public TriviaScore fetchUserScore(long userID, int seasonId) {
        try (Connection connection = source.getConnection();
             PreparedStatement statement = connection.prepareStatement(fetchUserPos)) {

            statement.setInt(1, seasonId);
            statement.setLong(2, userID);

            try (ResultSet set = statement.executeQuery()) {
                if (set.next()) {
                    return new TriviaScore(set.getLong("member_snowflake"), set.getString("username") + "#" + set.getString("discrim"),
                            set.getInt("score"),
                            set.getInt("rank"));
                }
            }

        } catch (SQLException ex) {
            LOGGER.error("Failed to fetch score", ex);
        }
        return null;
    }

    public int fetchTotalDatabaseMembers(int seasonId) {
        try (Connection connection = source.getConnection();
             PreparedStatement statement = connection.prepareStatement(totalPages)) {

            statement.setInt(1, seasonId);

            try (ResultSet set = statement.executeQuery()) {
                if (set.next()) {
                    return set.getInt("count");
                }
            }

        } catch (SQLException ex) {
            LOGGER.error("Failed to fetch size", ex);
        }
        return 0;
    }


}
