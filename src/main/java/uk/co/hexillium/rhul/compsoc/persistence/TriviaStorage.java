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
    private HikariDataSource source;

    private final static String updateMemberScore = "insert into numvember (member_snowflake, score) values (?, ?) on conflict (member_snowflake) do update set score = numvember.score + ?;";

    private final static String fetchLeaderboard =
            "select member_snowflake, score, username, discrim, RANK() over(order by score desc) as rank from numvember " +
                    "         left join member_information on member_snowflake = member_id " +
                    "order by score desc limit 10 offset ? ;";

    private final static String fetchUserPos =
            "select member_snowflake, score, username, discrim, RANK() over(order by score desc) as rank from numvember " +
                    "         left join member_information on member_snowflake = member_id " +
                    "where member_snowflake = ? ;";

    private final static String totalPages = "select count(*) as count from numvember;";

    public TriviaStorage(HikariDataSource source){
        this.source = source;
    }

    public void updateMemberScore(long memberSnowflake, int change){
        try (Connection connection = source.getConnection();
             PreparedStatement statement = connection.prepareStatement(updateMemberScore)){

            statement.setLong(1, memberSnowflake);
            statement.setInt(2, change);
            statement.setInt(3, change);

            statement.executeUpdate();

        } catch (SQLException ex){
            LOGGER.error("Failed to update score", ex);
        }
    }

    public List<TriviaScore> fetchLeaderboard(int page){
        List<TriviaScore> scores = new ArrayList<>();
        try (Connection connection = source.getConnection();
             PreparedStatement statement = connection.prepareStatement(fetchLeaderboard)){

            statement.setInt(1, page * 10);

            try (ResultSet set = statement.executeQuery()){
                while (set.next()){
                    scores.add(new TriviaScore(set.getString("username") + "#" + set.getString("discrim"),
                            set.getInt("score"),
                            set.getInt("rank")));
                }
            }

        } catch (SQLException ex){
            LOGGER.error("Failed to fetch scores", ex);
        }
        return scores;
    }

    public TriviaScore fetchUserScore(long userID){
        try (Connection connection = source.getConnection();
             PreparedStatement statement = connection.prepareStatement(fetchUserPos)){

            statement.setLong(1, userID);

            try (ResultSet set = statement.executeQuery()){
                if (set.next()){
                    return new TriviaScore(set.getString("username") + "#" + set.getString("discrim"),
                            set.getInt("score"),
                            set.getInt("rank"));
                }
            }

        } catch (SQLException ex){
            LOGGER.error("Failed to fetch score", ex);
        }
        return null;
    }

    public int fetchTotalDatabaseMembers(){
        try (Connection connection = source.getConnection();
             PreparedStatement statement = connection.prepareStatement(totalPages)){

            try (ResultSet set = statement.executeQuery()){
                if (set.next()){
                    return set.getInt("count");
                }
            }

        } catch (SQLException ex){
            LOGGER.error("Failed to fetch size", ex);
        }
        return 0;
    }


}
