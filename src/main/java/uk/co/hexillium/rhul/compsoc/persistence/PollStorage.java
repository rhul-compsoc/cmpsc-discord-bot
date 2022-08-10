package uk.co.hexillium.rhul.compsoc.persistence;

import com.zaxxer.hikari.HikariDataSource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import uk.co.hexillium.rhul.compsoc.persistence.entities.PollData;
import uk.co.hexillium.rhul.compsoc.persistence.entities.PollSelection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

public class PollStorage {

    private final HikariDataSource source;
    private final Logger logger = LogManager.getLogger(PollStorage.class);

    /*
    -- show who voted for who, in what polls
select coalesce(mi.nickname, mi.username || '#' || mi.discrim) as "user",
       opts.elem                                               as "candidate",
       opts.name                                               as "position"
from poll_selections as sel
         left join member_information mi on mi.member_id = sel.member_id
         inner join ((SELECT t.poll_id, a.elem, a.selections, t.description, t.name
                      FROM poll_data t,
                           unnest(t.options) WITH ORDINALITY a(elem, selections))) opts on opts.poll_id = sel.poll_id_fk
where opts.poll_id in (33, 34, 35)
  and pow(2, opts.selections - 1)::int & sel.selections <> 0
order by position;

-- show results
select results.position, results.candidate, count(*)
from (select opts.elem as "candidate",
             opts.name as "position"
      from poll_selections as sel
               inner join (SELECT t.poll_id, a.elem, a.selections, t.name
                           FROM poll_data t,
                                unnest(t.options) WITH ORDINALITY a(elem, selections)) opts
                          on opts.poll_id = sel.poll_id_fk
      where opts.poll_id in (33, 34, 35)
        and pow(2, opts.selections - 1)::int & sel.selections <> 0) as results
group by (results.candidate, results.position)
order by position, count(*) desc;
     */


    private static final String SELECT_POLL_DATA_SKELETON =
            "select poll_id, options, started, finished, expires, max_options, name, description," +
                    "channel_id, guild_id, message_id, visibility_flags " +
                    "from poll_data where poll_id = ?;";

    private static final String SELECT_POLL_SELECTIONS_FOR_POLL_DATA =
            "select poll_id_fk, member_id, selections, time_made " +
                    "from poll_selections where poll_id_fk = ?;";

    private static final String SELECT_POLL_SELECTION_FOR_MEMBER_POLL =
            "select poll_id_fk, member_id, selections, time_made " +
                    "from poll_selections where poll_id_fk = ? and member_id = ?;";

    private static final String UPSERT_POLL_SELECTION_ON_MEMBER_POLL =
            "insert into poll_selections(poll_id_fk, member_id, selections) values (?, ?, ?) " +
                    "on conflict (poll_id_fk, member_id) do update set selections = ?;";

    private static final String INSERT_NEW_POLL_DATA =
            "insert into poll_data(options, started, finished, expires, max_options, name, description, visibility_flags) values (?, ?, ?, ?, ?, ?, ?, ?) " +
                    "returning poll_id;";

    private static final String UPDATE_POLL_MESSAGE_LOCATION =
            "update poll_data set channel_id = ?, guild_id = ?, message_id = ? where poll_id = ?;";

    private static final String UPDATE_POLL_EXPIRE_POLL =
            "update poll_data set finished = true where poll_id = ?";


    public PollStorage(HikariDataSource source) {
        this.source = source;
    }

    private PollSelection constructSelection(ResultSet set) throws SQLException {
        return new PollSelection(
                set.getInt("poll_id_fk"),
                set.getLong("member_id"),
                set.getInt("selections"),
                set.getObject("time_made", OffsetDateTime.class)
        );
    }

    private PollData constructPollData(ResultSet set) throws SQLException {
        return new PollData(
                set.getInt("poll_id"),
                (String[]) set.getArray("options").getArray(),
                set.getObject("started", OffsetDateTime.class),
                set.getBoolean("finished"),
                set.getObject("expires", OffsetDateTime.class),
                set.getInt("max_options"),
                null,
                set.getString("name"),
                set.getString("description"),
                set.getLong("channel_id"),
                set.getLong("guild_id"),
                set.getLong("message_id"),
                set.getInt("visibility_flags")
        );
    }

    public PollData fetchPollData(int pollId, boolean populate){
        PollData data = fetchPollDataSkeleton(pollId);
        if (data == null || !populate){
            return data;
        }
        List<PollSelection> selData = fetchSelectionDataForPoll(pollId);
        if (selData == null) return data;
        data.setSelections(selData.toArray(new PollSelection[0]));
        return data;
    }

    private PollData fetchPollDataSkeleton(int pollId){
        try (Connection connection = source.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_POLL_DATA_SKELETON)){

            statement.setInt(1, pollId);
            ResultSet set = statement.executeQuery();
            if (set.next()){
                return constructPollData(set);
            }

        } catch (SQLException ex){
            logger.error("Failed to fetch skeleton data. ", ex);
        }
        return null;
    }

    public void updatePollMessageLocation(int pollId, long channelId, long guildId, long messageId){
        try (Connection connection = source.getConnection();
             PreparedStatement statement = connection.prepareStatement(UPDATE_POLL_MESSAGE_LOCATION)){

            statement.setLong(1, channelId);
            statement.setLong(2, guildId);
            statement.setLong(3, messageId);
            statement.setInt(4, pollId);

            statement.executeUpdate();

        } catch (SQLException ex){
            logger.error("Failed to update message position. ", ex);
        }
    }

    public void updatePollSetFinished(int pollId){
        try (Connection connection = source.getConnection();
             PreparedStatement statement = connection.prepareStatement(UPDATE_POLL_EXPIRE_POLL)){

            statement.setInt(1, pollId);

            statement.executeUpdate();

        } catch (SQLException ex){
            logger.error("Failed to update message expiry. ", ex);
        }
    }

    private List<PollSelection> fetchSelectionDataForPoll(int pollId){
        try (Connection connection = source.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_POLL_SELECTIONS_FOR_POLL_DATA)){

            List<PollSelection> selections = new ArrayList<>();

            statement.setInt(1, pollId);
            ResultSet set = statement.executeQuery();
            while (set.next()){
                selections.add(constructSelection(set));
            }
            return selections;

        } catch (SQLException ex){
            logger.error("Failed to fetch selection data. ", ex);
        }
        return null;
    }

    public PollSelection fetchPollSelectionForUser(int pollId, long memberId){
        try (Connection connection = source.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_POLL_SELECTION_FOR_MEMBER_POLL)){

            statement.setInt(1, pollId);
            statement.setLong(2, memberId);

            ResultSet set = statement.executeQuery();
            if (set.next()){
                return constructSelection(set);
            }
            return null;

        } catch (SQLException ex){
            logger.error("Failed to fetch selection data. ", ex);
        }
        return null;
    }

    public void upsertPollSelectionForMember(int pollId, long memberId, int options){
        try (Connection connection = source.getConnection();
             PreparedStatement statement = connection.prepareStatement(UPSERT_POLL_SELECTION_ON_MEMBER_POLL)){

            statement.setInt(1, pollId);
            statement.setLong(2, memberId);
//
//            Object[] values = Arrays.stream(options).boxed().toArray();
//            Array array = connection.createArrayOf("int", values);

//            statement.setArray(3, array);
//            statement.setArray(4, array);

            statement.setInt(3, options);
            statement.setInt(4, options);

            statement.executeUpdate();

        } catch (SQLException ex) {
            logger.error("Failed to upsert vote ", ex);
        }
    }

    public int insertNewPollData(String[] options, OffsetDateTime started,
                                       boolean finished, OffsetDateTime expires,
                                       int maxOpts, String name, String description,
                                       int visibilityFlags){
        try (Connection connection = source.getConnection();
             PreparedStatement statement = connection.prepareStatement(INSERT_NEW_POLL_DATA)){

            statement.setArray(1, connection.createArrayOf("text", options));
            statement.setObject(2, started);
            statement.setBoolean(3, finished);
            statement.setObject(4, expires);
            statement.setInt(5, maxOpts);
            statement.setString(6, name);
            statement.setString(7, description);
            statement.setInt(8, visibilityFlags);


            statement.execute();

            ResultSet ret = statement.getResultSet();
            ret.next();
            return ret.getInt("poll_id");

        } catch (SQLException ex) {
            logger.error("Failed to upsert vote ", ex);
        }
        return -1;
    }

}
