package uk.co.hexillium.rhul.compsoc.persistence;

import com.zaxxer.hikari.HikariDataSource;
import me.jcsawyer.classroombot.entities.GuildSettings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import uk.co.hexillium.rhul.compsoc.persistence.entities.GuildSettings;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

public class GuildData {

    Logger logger = LogManager.getLogger(GuildData.class);

    HikariDataSource source;

    static String SET_GUILD_DATA =
            "todo";
    static String GET_GUILD_DATA =
            "todo";

    public GuildData(HikariDataSource source){
        this.source = source;
    }

    public void fetchData(long guildID, Consumer<GuildSettings> success, Consumer<SQLException> failure){
        fetchData(Database.dbPool, guildID, success, failure);
    }

    public void setGuildData(long guildID, GuildSettings data, Runnable success, Consumer<SQLException> failure) {
        setGuildData(Database.dbPool, guildID, data, success, failure);
    }


    private void setGuildData(ExecutorService exec, long guildID, GuildSettings data, Runnable success, Consumer<SQLException> failure) {
        exec.submit(
                () -> {
                    try (Connection connection = source.getConnection();
                         PreparedStatement setGuildData = connection.prepareStatement(SET_GUILD_DATA);
                    ){

                        setGuildData.setLong(1, guildID);
                        //todo
                        setGuildData.setString(5, data.getPrefix());

                        setGuildData.execute();

                        if (success != null) success.run();

                    } catch (SQLException ex) {
                        logger.warn("Committing to the database failed - ", ex);
                        if (failure != null) failure.accept(ex);
                    }
                }
        );
    }

    private void fetchData(ExecutorService exec, long guildID, Consumer<GuildSettings> success, Consumer<SQLException> failure) {
        exec.submit(
                () -> {
                    try (Connection connection = source.getConnection();
                         PreparedStatement getGuildData = connection.prepareStatement(GET_GUILD_DATA);
                    ) {

                        getGuildData.setLong(1, guildID); //the guildid to fetch

                        getGuildData.executeQuery();

                        try (ResultSet set = getGuildData.getResultSet()){

                            if (set == null || !set.next()) {
                                success.accept(null);
                                return;
                            }

                            GuildSettings settings = new GuildSettings(guildID,
                                    //todo
                                    ,set.getString("prefix"));

                            success.accept(settings);
                        }


                    } catch (SQLException ex) {
                        logger.warn("Failed to fetch guild prefix from the database - ", ex);
                        failure.accept(ex);
                    }
                });
    }

}
