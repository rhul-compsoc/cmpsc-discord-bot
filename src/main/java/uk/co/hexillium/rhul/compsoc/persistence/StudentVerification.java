package uk.co.hexillium.rhul.compsoc.persistence;

import com.zaxxer.hikari.HikariDataSource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import uk.co.hexillium.rhul.compsoc.persistence.entities.DuplicateEntry;
import uk.co.hexillium.rhul.compsoc.persistence.entities.VerificationMessage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class StudentVerification {

    private final static Logger LOGGER = LogManager.getLogger(StudentVerification.class);

    private final static String ADD_STUDENT =
            "insert into student_verification(student_id, student_login_name, student_details_submitted,"+
                    " student_discord_snowflake, student_verification_message_snowflake)" +
                    " values (?, ?, ?," +
                    " ?, ?);";
    private final static String INVALIDATE_STUDENT =
            "update student_verification set student_details_invalidated = TRUE, student_details_invalidated_time = ? " +
                    "where student_verification_message_snowflake = ? " +
                    "and student_verified = false and student_details_invalidated = false returning student_discord_snowflake;" ;
    private final static String VALIDATE_STUDENT_WITH_TYPE =
            "update student_verification set student_type = ?, student_verified = TRUE, student_verified_time = ? " +
                    "where student_verification_message_snowflake = ? " +
                    "and student_verified = false and student_details_invalidated = false returning student_discord_snowflake;";
    private final static String VALIDATE_STUDENT =
            "update student_verification set student_verified = TRUE, student_verified_time = ? " +
                    "where student_verification_message_snowflake = ? " +
                    "and student_verified = false and student_details_invalidated = false returning student_discord_snowflake;";
    private final static String CAN_SUBMIT_REQUEST =
            "select count(*) from student_verification " +
                    "where student_discord_snowflake = ? " +
                        "and (student_verified = TRUE or (student_details_invalidated = FALSE and student_verified = FALSE));";
    private final static String PAST_DUPE_IDS =
            "select student_discord_snowflake, student_verified, student_details_invalidated from student_verification where student_id = ?::varchar;";
    private final static String PAST_DUPE_NAMES =
            "select student_discord_snowflake, student_verified, student_details_invalidated from student_verification where student_login_name = ?::varchar;";
    private final static String FETCH_MESSAGE_UPDATE =
            "select student_id, student_login_name, student_verified, student_verified_time, " +
                    "       student_details_submitted, student_details_invalidated, student_discord_snowflake, " +
                    "       student_details_invalidated_time " +
                    "from student_verification where student_verification_message_snowflake = ?;";
    private final static String IS_DISCORD_ACCOUNT_VALIDATED =
            "select student_verified from student_verification where student_discord_snowflake = ? order by student_pk desc limit 1;";

    private final static String FIND_DISCORD_IDS_MATCHING_SU_ID =
            "select student_discord_snowflake from student_verification where student_id in ?;";

    private HikariDataSource source;

    public StudentVerification(HikariDataSource source){
        this.source = source;
    }

    public void addStudent(String studentID, String studentLoginName, long timeSubmitted, long userSnowflake,
                            long verificationMessageID){
        try (Connection connection = source.getConnection();
             PreparedStatement statement = connection.prepareStatement(ADD_STUDENT)){

            statement.setString(1, studentID);
            statement.setString(2, studentLoginName);
            statement.setLong(3, timeSubmitted);
            statement.setLong(4, userSnowflake);
            statement.setLong(5, verificationMessageID);
            statement.executeUpdate();

        } catch (SQLException ex){
            LOGGER.error("Failed to insert student", ex);
        }
    }


    public void invalidateStudent(long studentDetailsInvalidTime, long verificationMessageID, Consumer<Long> studentID){
        try (Connection connection = source.getConnection();
             PreparedStatement statement = connection.prepareStatement(INVALIDATE_STUDENT)){

            statement.setLong(1, studentDetailsInvalidTime);
            statement.setLong(2, verificationMessageID);
//            statement.executeUpdate();
            try (ResultSet rs = statement.executeQuery()){
                if (rs == null) return;
                while (rs.next()){
                    studentID.accept(rs.getLong("student_discord_snowflake"));
                }
            }
        } catch (SQLException ex){
            LOGGER.error("Failed to invalidate student", ex);
        }
    }

    public List<DuplicateEntry> getDuplicatesForID(String ID){
        List<DuplicateEntry> entries = new ArrayList<>();
        try (Connection connection = source.getConnection();
             PreparedStatement statement = connection.prepareStatement(PAST_DUPE_IDS)){

            statement.setString(1, ID);

            try (ResultSet rs = statement.executeQuery()){
                if (rs == null) return entries;
                while (rs.next()){
                    entries.add(new DuplicateEntry(rs.getLong("student_discord_snowflake"),
                            rs.getBoolean("student_verified"),
                            rs.getBoolean("student_details_invalidated")));
                }
            }
        } catch (SQLException ex){
            LOGGER.error("Failed to fetch historical duplicates", ex);
        }
        return entries;
    }

    public List<DuplicateEntry> getDuplicatesForName(String name){
        List<DuplicateEntry> entries = new ArrayList<>();
        try (Connection connection = source.getConnection();
             PreparedStatement statement = connection.prepareStatement(PAST_DUPE_NAMES)){

            statement.setString(1, name);
            try (ResultSet rs = statement.executeQuery()){
                if (rs == null) return entries;
                while (rs.next()){
                    entries.add(new DuplicateEntry(rs.getLong("student_discord_snowflake"),
                            rs.getBoolean("student_verified"),
                            rs.getBoolean("student_details_invalidated")));
                }
            }
        } catch (SQLException ex){
            LOGGER.error("Failed to fetch historical duplicates", ex);
        }
        return entries;
    }

    public boolean canSubmitRequest(long studentDiscordID){
        try (Connection connection = source.getConnection();
             PreparedStatement statement = connection.prepareStatement(CAN_SUBMIT_REQUEST)){

            statement.setLong(1, studentDiscordID);
//            statement.executeUpdate();
            try (ResultSet rs = statement.executeQuery()){
                if (rs == null) return false;
                rs.next();
                return rs.getInt("count") == 0;
            }
        } catch (SQLException ex){
            LOGGER.error("Failed to test if student can submit a request", ex);
        }
        return false;
    }

    public void validateStudent(int studentType, long studentVerifiedTime, long verificationMessageID, Consumer<Long> studentID){
        try (Connection connection = source.getConnection();
             PreparedStatement statement = connection.prepareStatement(VALIDATE_STUDENT_WITH_TYPE)){

            statement.setInt(1, studentType);
            statement.setLong(2, studentVerifiedTime);
            statement.setLong(3, verificationMessageID);
//            statement.executeUpdate();
            try (ResultSet rs = statement.executeQuery()){
                if (rs == null) return;
                while (rs.next()){
                    studentID.accept(rs.getLong("student_discord_snowflake"));
                }
            }

        } catch (SQLException ex){
            LOGGER.error("Failed to validate student", ex);
        }
    }

    public void validateStudent(long studentVerifiedTime, long verificationMessageID, Consumer<Long> studentID){
        try (Connection connection = source.getConnection();
             PreparedStatement statement = connection.prepareStatement(VALIDATE_STUDENT)){

            statement.setLong(1, studentVerifiedTime);
            statement.setLong(2, verificationMessageID);
//            statement.executeUpdate();
//            statement.executeQuery();
            try (ResultSet rs = statement.executeQuery()){
                if (rs == null) return;
                while (rs.next()){
                    studentID.accept(rs.getLong("student_discord_snowflake"));
                }
            }
        } catch (SQLException ex){
            LOGGER.error("Failed to validate student", ex);
        }
    }

    public boolean isDiscordAccountValidated(long discordId){
        try (Connection connection = source.getConnection();
             PreparedStatement statement = connection.prepareStatement(IS_DISCORD_ACCOUNT_VALIDATED)){

            statement.setLong(1, discordId);

            try (ResultSet rs = statement.executeQuery()){
                if (rs == null) return false;
                if (rs.next()){
                    return rs.getBoolean("student_verified");
                }
                return false;
            }
        } catch (SQLException ex){
            LOGGER.error("Failed to fetch student validation state", ex);
        }
        return false;
    }

    public VerificationMessage updateVerificationMessage(long messageID){
        try (Connection connection = source.getConnection();
                PreparedStatement statement = connection.prepareStatement(FETCH_MESSAGE_UPDATE)){
            statement.setLong(1, messageID);

            try (ResultSet set = statement.executeQuery()) {
                if (!set.next()){
                    return null;
                }
                /*
                student_id, student_login_name, student_verified, student_verified_time,
       student_details_submitted, student_details_invalidated, student_discord_snowflake,
       student_details_invalidated_time
                 */
                return new VerificationMessage(
                        set.getString("student_id"),
                        set.getString("student_login_name"),
                        set.getBoolean("student_verified"),
                        set.getLong("student_verified_time"),
                        set.getLong("student_details_submitted"),
                        set.getBoolean("student_details_invalidated"),
                        set.getLong("student_discord_snowflake"),
                        set.getLong("student_details_invalidated_time")
                );
            }

        } catch (SQLException e) {
            LOGGER.error("Failed to fetch verification message update");
        }
        return null;
    }

    public List<Long> findStudentsWithSUIds(String[] suIDs){
        try (Connection connection = source.getConnection();
             PreparedStatement statement = connection.prepareStatement(FIND_DISCORD_IDS_MATCHING_SU_ID)) {
            statement.setArray(1, connection.createArrayOf("text[]", suIDs));

            ResultSet set = statement.executeQuery();
            List<Long> found = new ArrayList<>();
            while (set.next()){
                found.add(set.getLong(1));
            }
            return found;

        } catch (SQLException ex){
            LOGGER.error("Failed to fetch members with matching SU IDS", ex);
        }
        return null;
    }

}
