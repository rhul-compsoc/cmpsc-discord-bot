package uk.co.hexillium.rhul.compsoc.persistence;

import com.zaxxer.hikari.HikariDataSource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import uk.co.hexillium.rhul.compsoc.persistence.entities.TriviaScore;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
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
    private final static String totalPages = "select count(*) as count from numvember where season_id = ?;";
    private HikariDataSource source;

    public TriviaStorage(HikariDataSource source) {
        this.source = source;
    }

    public void updateMemberScore(long memberSnowflake, int change, int season) {
        try (Connection connection = source.getConnection();
             PreparedStatement statement = connection.prepareStatement(updateMemberScore)) {

            statement.setLong(1, memberSnowflake);
            statement.setInt(2, change);
            statement.setInt(3, season);
            statement.setInt(4, change);

            statement.executeUpdate();

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
