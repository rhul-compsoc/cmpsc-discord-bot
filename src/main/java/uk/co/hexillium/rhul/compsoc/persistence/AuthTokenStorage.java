package uk.co.hexillium.rhul.compsoc.persistence;

import com.zaxxer.hikari.HikariDataSource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import uk.co.hexillium.rhul.compsoc.api.Token;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

public class AuthTokenStorage {

    private static final Logger logger = LogManager.getLogger(AuthTokenStorage.class);
    private static final String ADD_AUTH_TOKEN = "insert into api_tokens(token_string, token_expiry, token_comment, token_invalided) values (?, ?, ?, ?);";
    private static final String FETCH_AUTH_TOKENS = "select token_id, token_string, token_expiry, token_comment from api_tokens where not token_invalided and token_expiry > ?;";
    private final HikariDataSource source;

    SecureRandom sr = new SecureRandom();

    private final static int TOKEN_SIZE = 16;


    public AuthTokenStorage(HikariDataSource source) {
        this.source = source;
    }


    /**
     * Adds a new auth token to the database.
     *
     * @param tokenExpiry the time at which the token will no longer be valid.
     * @param tokenComment a comment about the token, such as whose it is.
     * @param success   the callback for a successful transaction.
     * @param failure   the callback for an unsuccessful transaction.
     */
    public void addAuthToken(long tokenExpiry, String tokenComment, Consumer<byte[]> success, Consumer<SQLException> failure) {
        addAuthToken(Database.dbPool, genToken(TOKEN_SIZE), tokenExpiry,tokenComment, false, success, failure);
    }

    /**
     * Fetches all of the valid tokens.
     *
     * @param success   the callback for a successful transaction.
     * @param failure   the callback for an unsuccessful transaction.
     */
    public void fetchAuthTokens(Consumer<List<Token>>  success, Consumer<SQLException> failure) {
        fetchAuthTokens(Database.dbPool, success, failure);
    }

    private void addAuthToken(ExecutorService exec, byte[] tokenString, long tokenExpiry, String tokenComment, boolean tokenInvalided, Consumer<byte[]> success, Consumer<SQLException> failure) {
        exec.submit(
                () -> {
                    try (Connection connection = source.getConnection();
                         PreparedStatement addAuthToken = connection.prepareStatement(ADD_AUTH_TOKEN);
                    ) {

                        //token_string, token_expiry, token_comment, token_invalided
                        addAuthToken.setBytes(1, tokenString);
                        addAuthToken.setLong(2, tokenExpiry);
                        addAuthToken.setString(3, tokenComment);
                        addAuthToken.setBoolean(4, tokenInvalided);

                        addAuthToken.executeUpdate();

                        if (success != null) success.accept(tokenString);

                    } catch (SQLException ex) {
                        logger.warn("Commiting an auth token to the database failed - ", ex);
                        if (failure != null) failure.accept(ex);
                    }
                }
        );
    }

    private void fetchAuthTokens(ExecutorService exec, Consumer<List<Token>> success, Consumer<SQLException> failure) {
        exec.submit(
                () -> {
                    try (Connection connection = source.getConnection();
                         PreparedStatement fetchAuthTokens = connection.prepareStatement(FETCH_AUTH_TOKENS);
                    ) {

                        //token_string, token_expiry, token_comment, token_invalided

                        fetchAuthTokens.setLong(1, System.currentTimeMillis());

                        ResultSet set = fetchAuthTokens.executeQuery();

                        List<Token> tokens = new ArrayList<>();
                        while (set.next()){
                            tokens.add(
                                    new Token(
                                            set.getBytes("token_string"),
                                            set.getLong("token_expiry"),
                                            set.getString("token_comment")
                                    )
                            );
                        }

                        if (success != null) success.accept(tokens);

                    } catch (SQLException ex) {
                        logger.warn("Fetching auth tokens from the database failed - ", ex);
                        if (failure != null) failure.accept(ex);
                    }
                }
        );
    }


    private byte[] genToken(int bytes){
        byte[] token = new byte[bytes];
        sr.nextBytes(token);
        return token;
    }


}
