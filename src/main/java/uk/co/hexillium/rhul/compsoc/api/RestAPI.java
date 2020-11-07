package uk.co.hexillium.rhul.compsoc.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.utils.data.DataObject;
import net.dv8tion.jda.internal.entities.EntityBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import spark.Filter;
import spark.Route;
import uk.co.hexillium.rhul.compsoc.persistence.Database;

import javax.xml.crypto.Data;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static spark.Spark.*;
import static spark.Spark.halt;

public class RestAPI {

    private Logger logger = LogManager.getLogger(RestAPI.class);

    private Filter authCheck;
    private Route getMembers;

    private ScheduledExecutorService timer;

    private ObjectMapper mapper;

    private List<Token> tokens;

    public RestAPI(int port, String slug, JDA jda, ObjectMapper om){
        this.mapper = om;
        timer = Executors.newSingleThreadScheduledExecutor();
        tokens = new ArrayList<>();
        initialiseRoutes(jda, om);
        port(port);
        path("/" + slug, () -> {
            before("/*", authCheck);
            get("/hello", (req, resp) -> "hi!");
            get("/getmembers/:guildid", getMembers);
        });
        Consumer<List<Token>> cons = (List<Token> newTokens) -> this.tokens = newTokens;
        timer.scheduleAtFixedRate(() -> Database.AUTH_TOKEN_STORAGE.fetchAuthTokens(cons, (e) -> logger.error("Failed to fetch tokens.", e)),
                0, 10, TimeUnit.SECONDS);
    }

    private boolean verifyAuthToken(String token){
        //hold tokens in memory, and hit database every 10 seconds-
        if (token == null) return false;
        return tokens.stream().anyMatch(t -> t.getToken().equals(token));
    }

    private void initialiseRoutes(JDA jda, ObjectMapper om){
        authCheck = ((request, response) -> {
            if (!verifyAuthToken(request.headers("X-Auth-Token"))){
                halt(403, "Invalid, expired, incorrect or missing token.");
            }
        });
        getMembers = ((request, response) -> {
            long id;
            try {
                id = Long.parseLong(request.params(":guildid"));
            }  catch (NumberFormatException ex){
                response.status(400);
                return "invalid GuildID data type";
            }
            if (jda.getGuildById(id) == null){
                response.status(404);
                return "Guild not found.";
            }
            response.type("application/json");
            return mapper.writeValueAsString(Database.EXPERIENCE_STORAGE.getGuildData(id, jda.getGuildById(id)));
        });
    }



}
