package uk.co.hexillium.rhul.compsoc.persistence;

import com.zaxxer.hikari.HikariDataSource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import uk.co.hexillium.rhul.compsoc.persistence.entities.GameAccountBinding;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class GameBindingStorage {

    private HikariDataSource source;
    private static final Logger logger = LogManager.getLogger(GameBindingStorage.class);

    private final static String getGameBindingsForMember = "" +
            "select record_id, game_id, discord_member_snowflake, discord_guild_snowflake, game_username, game_user_id, user_banned\n" +
            "from game_account_bindings where discord_member_snowflake = ? and discord_guild_snowflake = ?;";

    private final static String getGameBindingsForGame = "" +
            "select record_id, game_id, discord_member_snowflake, discord_guild_snowflake, game_username, game_user_id, user_banned\n" +
            "from game_account_bindings where discord_guild_snowflake = ? and game_id = ?;";

    private final static String getGameBindingsForGameUserID = "" +
            "select record_id, game_id, discord_member_snowflake, discord_guild_snowflake, game_username, game_user_id, user_banned\n" +
            "from game_account_bindings where discord_guild_snowflake = ? and game_id = ? and game_user_id = ?;";

    private final static String getGameBindingsForMemberGame = "" +
            "select record_id, game_id, discord_member_snowflake, discord_guild_snowflake, game_username, game_user_id, user_banned\n" +
            "from game_account_bindings where discord_guild_snowflake = ? and discord_member_snowflake = ? and game_id = ?;";

    private final static String insertGameBinding = "" +
            "\n" +
            "insert into game_account_bindings(game_id, discord_member_snowflake, discord_guild_snowflake, game_username, game_user_id, user_banned)\n" +
            "values (?, ?, ?, ?, ?, ?) returning *;";

    private final static String deleteGameBinding = "" +
            "delete from game_account_bindings where discord_member_snowflake = ? and record_id = ?;";


    GameBindingStorage(HikariDataSource source){
        this.source = source;
    }

    public List<GameAccountBinding> getGameBindingsForMember(long memberID, long guildID){
        try (Connection connection = source.getConnection();
             PreparedStatement statement = connection.prepareStatement(getGameBindingsForMember)){
            statement.setLong(1, memberID);
            statement.setLong(2, guildID);
            try (ResultSet set = statement.executeQuery()){
                return retrieveData(set);
            }

        } catch (SQLException ex){
            logger.warn("Failed to fetch game bindings for member", ex);
        }
        return null;
    }

    public List<GameAccountBinding> getGameBindingsForGame(long guildID, String gameID){
        try (Connection connection = source.getConnection();
             PreparedStatement statement = connection.prepareStatement(getGameBindingsForGame)){
            statement.setLong(1, guildID);
            statement.setString(2, gameID);
            try (ResultSet set = statement.executeQuery()){
                return retrieveData(set);
            }

        } catch (SQLException ex){
            logger.warn("Failed to fetch game bindings for game", ex);
        }
        return null;
    }

    public List<GameAccountBinding> getGameBindingsForGameGameUserID(long guildID, String gameID, String gameUserID){
        try (Connection connection = source.getConnection();
             PreparedStatement statement = connection.prepareStatement(getGameBindingsForGameUserID)){
            statement.setLong(1, guildID);
            statement.setString(2, gameID);
            statement.setString(3, gameUserID);
            try (ResultSet set = statement.executeQuery()){
                return retrieveData(set);
            }

        } catch (SQLException ex){
            logger.warn("Failed to fetch game bindings for game and userid", ex);
        }
        return null;
    }

    public List<GameAccountBinding> getGameBindingsForMemberGame(long memberID, long guildID, String gameID){
        try (Connection connection = source.getConnection();
             PreparedStatement statement = connection.prepareStatement(getGameBindingsForMemberGame)){
            statement.setLong(1, guildID);
            statement.setLong(2, memberID);
            statement.setString(3, gameID);
            try (ResultSet set = statement.executeQuery()){
                return retrieveData(set);
            }

        } catch (SQLException ex){
            logger.warn("Failed to fetch game bindings for member + game", ex);
        }
        return null;
    }

    public GameAccountBinding addGameBinding(GameAccountBinding binding){
        try (Connection connection = source.getConnection();
             PreparedStatement statement = connection.prepareStatement(insertGameBinding)){
            statement.setString(1, binding.getGameId());
            statement.setLong(2, binding.getMemberId());
            statement.setLong(3, binding.getGuildId());
            statement.setString(4, binding.getGameUsername());
            statement.setString(5, binding.getGameUserId());
            statement.setBoolean(6, binding.isUserBanned());

            statement.execute();
            ResultSet set = statement.getResultSet();
            if (!set.next())
                return null;
            return new GameAccountBinding(
                    set.getInt(1),
                    set.getString(2),
                    set.getLong(3),
                    set.getLong(4),
                    set.getString(5),
                    set.getString(6),
                    set.getBoolean(7)
            );
        } catch (SQLException ex){
            logger.warn("Failed to insert new binding", ex);
        }
        return null;
    }

    public void deleteBindingById(long userID, int bindingID){
        try (Connection connection = source.getConnection();
             PreparedStatement statement = connection.prepareStatement(deleteGameBinding)){
            statement.setLong(1, userID);
            statement.setInt(2, bindingID);

            statement.execute();

        } catch (SQLException ex){
            logger.warn("Failed to delete binding", ex);
        }
    }

    private List<GameAccountBinding> retrieveData(ResultSet set) throws SQLException {
        //record_id, game_id, discord_member_snowflake, discord_guild_snowflake, game_username, game_user_id, user_banned
        List<GameAccountBinding> bindings = new ArrayList<>();
        while (set.next()){
            bindings.add(new GameAccountBinding(
                    set.getInt(1),
                    set.getString(2),
                    set.getLong(3),
                    set.getLong(4),
                    set.getString(5),
                    set.getString(6),
                    set.getBoolean(7)
            ));
        }
        return bindings;
    }



}
