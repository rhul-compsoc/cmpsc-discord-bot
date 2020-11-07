package uk.co.hexillium.rhul.compsoc.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Database {

    private static Database instance = null;
    private static Logger logger = LogManager.getLogger(Database.class);
    private static final int SQL_ATTEMPTS = 3;
    static ExecutorService dbPool = Executors.newFixedThreadPool(8);

    private HikariDataSource source;

    public static GuildData GUILD_DATA;
    public static JobStorage JOB_STORAGE;
    public static StudentVerification STUDENT_VERIFICATION;
    public static AuthTokenStorage AUTH_TOKEN_STORAGE;
    public static ExperienceStorage EXPERIENCE_STORAGE;
    public static TriviaStorage TRIVIA_STORAGE;

    public static Database getInstance(){
        if (instance == null){
            return new Database();
        } else {
            return instance;
        }
    }

    private Database(){
        instance = this;
        try {
            hikariConnect();
        } catch (IOException ex){
            logger.error("Database connection failed.");
        }
    }

    public static void runLater(Runnable runnable){
        dbPool.submit(runnable);
    }

    public void hikariConnect() throws IOException {
        ObjectMapper om = new ObjectMapper();
        HikariConfig config = new HikariConfig();
        HashMap<?, ?> configData = om.readValue(new File("database.json"), HashMap.class);
        logger.info(configData.toString());
        config.setJdbcUrl( (String) configData.get("url")      );  //"jdbc:postgresql://localhost/compsoc_bot
        config.setUsername((String) configData.get("username") );  //
        config.setPassword((String) configData.get("password") );  //
        config.setSchema(  (String) configData.get("schema")   );  //public
        config.setMaximumPoolSize(10);

        source = new HikariDataSource(config);

        GUILD_DATA = new GuildData(source);
        JOB_STORAGE = new JobStorage(source);
        STUDENT_VERIFICATION = new StudentVerification(source);
        AUTH_TOKEN_STORAGE = new AuthTokenStorage(source);
        EXPERIENCE_STORAGE = new ExperienceStorage(source);
        TRIVIA_STORAGE = new TriviaStorage(source);
    }

    public HikariDataSource getSource(){
        return source;
    }

}
