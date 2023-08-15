package uk.co.hexillium.rhul.compsoc.persistence;

import com.zaxxer.hikari.HikariDataSource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.*;
import java.time.OffsetDateTime;

public class EmailVerification {

    private final static Logger LOGGER = LogManager.getLogger(EmailVerification.class);

    private final static String REGISTER_EMAIL_TO_SEND =
            "insert into email_verification(email_addr, time_expiry, token, discord_snowflake) " +
                    "VALUES (?, ?, ?, ?);";

    private final static String IS_EMAIL_ADDRESS_IN_USE =
            "select verif_id from email_verification where " +
                    "email_addr ilike ? and discord_snowflake <> ? and " + // search for the email address in use by other people
                    "verification_completed = true;";                   // make sure it was successfully bound to someone else
    private final static String FIND_VERIFICATION_BY_TOKEN =
            "select verif_id, discord_snowflake, time_expiry from email_verification where token = ? and verification_completed = false;";

    private final static String UPDATE_VERIFICATION_MARK_SUCCESS =
            "update email_verification set verification_completed_time = now() and verification_completed = true " +
                    "where verif_id = ?;";

    private final static String GET_MOST_RECENT_ATTEMPT_ANY =
            "select time_submitted from email_verification where discord_snowflake = ? order by time_submitted desc;";

    private final static String GET_MOST_RECENT_ATTEMPT_SUCCESSFUL =
            "select verification_completed_time from email_verification " +
                    "where discord_snowflake = ? " +
                    "and verification_completed = true " +
                    "order by verification_completed_time desc;";

    private HikariDataSource source;

    public EmailVerification(HikariDataSource source){
        this.source = source;
    }

    public void registerEmailToSend(String emailAddress, OffsetDateTime timeExpires, byte[] token, long userOwnerSnowflake){
        try (Connection connection = source.getConnection();
             PreparedStatement statement = connection.prepareStatement(REGISTER_EMAIL_TO_SEND)){

            statement.setString(1, emailAddress);
            statement.setObject(2, timeExpires);
            statement.setBytes(3, token);
            statement.setLong(4, userOwnerSnowflake);
            statement.executeUpdate();

        } catch (SQLException ex){
            LOGGER.error("Failed insert verification request", ex);
        }
    }

    public VerificationResult acceptToken(byte[] token, long requestedOwnerSnowflake){
        try (Connection connection = source.getConnection();
             PreparedStatement statement = connection.prepareStatement(FIND_VERIFICATION_BY_TOKEN)){

            // verif_id, discord_snowflake, time_expiry
            statement.setBytes(1, token);
            ResultSet resultSet = statement.executeQuery();

            if (!resultSet.next()){
                return VerificationResult.INVALID_TOKEN;
            }
            long verificationId = resultSet.getLong("verif_id");
            long actualSnowflakeOwner = resultSet.getLong("discord_snowflake");
            OffsetDateTime expiryTime = resultSet.getObject("time_expiry", OffsetDateTime.class);

            if (actualSnowflakeOwner != requestedOwnerSnowflake){
                return VerificationResult.MISMATCHING_USER_ID;
            }

            if (expiryTime.isAfter(OffsetDateTime.now())){
                return VerificationResult.EXPIRED_TOKEN;
            }

            try (PreparedStatement update = connection.prepareStatement(UPDATE_VERIFICATION_MARK_SUCCESS)){
                update.setLong(1, verificationId);
                update.executeUpdate();
                return VerificationResult.SUCCESS;
            }

        } catch (SQLException ex){
            LOGGER.error("Failed update email verification", ex);
        }
        return VerificationResult.UNKNOWN_ERROR;
    }

    public boolean isEmailAddressInUseByOtherPerson(String emailAddressTo, long primaryUserSnowflake){
        try (Connection connection = source.getConnection();
             PreparedStatement statement = connection.prepareStatement(IS_EMAIL_ADDRESS_IN_USE)){
            statement.setString(1, emailAddressTo);
            statement.setLong(2, primaryUserSnowflake);

            ResultSet set = statement.executeQuery();

            return set.next(); //if we have any results, then it's in use
        } catch (SQLException ex){
            LOGGER.error("Failed to check if email address is in use", ex);
        }
        return false;
    }

    public OffsetDateTime getMostRecentVerificationAttempt(long userSnowflake){
        try (Connection connection = source.getConnection();
             PreparedStatement statement = connection.prepareStatement(GET_MOST_RECENT_ATTEMPT_ANY)){
            statement.setLong(1, userSnowflake);

            ResultSet set = statement.executeQuery();

            if (!set.next()) return null;

            return set.getObject(1, OffsetDateTime.class);
        } catch (SQLException ex){
            LOGGER.error("Failed to fetch most recent attempt", ex);
        }
        return null;
    }

    public OffsetDateTime getUserMostRecentSuccessfulVerification(long userSnowflake){
        try (Connection connection = source.getConnection();
             PreparedStatement statement = connection.prepareStatement(GET_MOST_RECENT_ATTEMPT_SUCCESSFUL)){
            statement.setLong(1, userSnowflake);

            ResultSet set = statement.executeQuery();

            if (!set.next()) return null;

            return set.getObject(1, OffsetDateTime.class);
        } catch (SQLException ex){
            LOGGER.error("Failed to fetch most recent success", ex);
        }
        return null;
    }

    public enum VerificationResult{
        SUCCESS(false),
        INVALID_TOKEN(true),
        MISMATCHING_USER_ID(true),
        EXPIRED_TOKEN(true),
        UNKNOWN_ERROR(true);

        private final boolean isError;
        VerificationResult(boolean isError){
            this.isError = isError;
        }

        public boolean isError(){
            return isError;
        }
    }
}
