package uk.co.hexillium.rhul.compsoc.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.dv8tion.jda.api.JDA;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import spark.Filter;
import spark.Request;
import spark.Route;
import uk.co.hexillium.rhul.compsoc.persistence.Database;
import uk.co.hexillium.rhul.compsoc.persistence.entities.GameAccountBinding;

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

    private Route getGameBindingsForMember;
    private Route getGameBindingsForMemberGame;
    private Route getGameBindingsForGame;
    private Route getGameBindingsForGameUserID;
    private Route insertGameBinding;
    private Route deleteGameBinding;
    private Route getMemberInfo;

    private ScheduledExecutorService timer;

    private ObjectMapper mapper;

    private List<Token> tokens;

    private Map<Request, Long> timings = new HashMap<>();

    public RestAPI(int port, String slug, JDA jda, ObjectMapper om){
        this.mapper = om;
        timer = Executors.newSingleThreadScheduledExecutor();
        tokens = new ArrayList<>();
        initialiseRoutes(jda, om);
        port(port);
        before("/*", ((request, response) -> {
            logger.info(request.requestMethod() + " request for " + request.pathInfo());
        }));
        after("/*", ((request, response) -> {
            int len = 0;
            if (response.body() != null){
                len = response.body().length();
            }
            logger.info(request.requestMethod() + " " + request.pathInfo() + " returned " + response.status() + ", with body length: " + len);
        }));
        path("/" + slug, () -> {
            before("/*", authCheck);
            before("/*", ((request, response) -> timings.put(request, System.currentTimeMillis())));
            after("/*", ((request, response) -> {
                response.header("X-Time-Taken-Ms", String.valueOf(System.currentTimeMillis() - timings.getOrDefault(request, System.currentTimeMillis())));
                timings.remove(request);
            }));
            get("/hello", (req, resp) -> "hi!");
            get("/getmembers/:guildid", getMembers);
            get("/guild/:guildid/member/:memberid/info", getMemberInfo);
            path("/games/bindings", () -> {
                path("/guild/:guildid", () -> {
                    path("/member/:memberid", () -> {
                        get("/list", getGameBindingsForMember);
                        path("/game/:gameid", () -> {
                            get("/list", getGameBindingsForMemberGame);
                        });
                    });
                    path("/game/:gameid", () -> {
                        get("/list", getGameBindingsForGame);
                        get("/id/:gameuserid", getGameBindingsForGameUserID);
                    });
                });
                post("/create", insertGameBinding);
                delete("/remove", deleteGameBinding);
            });
        }); //[/api/games/bindings/guild/500612695570120704/id/abc-1234
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
        getGameBindingsForMember = (((request, response) -> {
            //guildid, memberid
            long guildID;
            try {
                guildID = Long.parseLong(request.params(":guildid"));
            }  catch (NumberFormatException ex){
                response.status(400);
                return "invalid GuildID data type";
            }
            long memberID;
            try {
                memberID = Long.parseLong(request.params(":memberid"));
            }  catch (NumberFormatException ex){
                response.status(400);
                return "invalid MemberID data type";
            }
            response.type("application/json");
            return om.writeValueAsString(Database.GAME_BINDING_STORAGE.getGameBindingsForMember(memberID, guildID));
        }));
        getGameBindingsForGame = (((request, response) -> {
            //guildid, gameid
            long guildID;
            try {
                guildID = Long.parseLong(request.params(":guildid"));
            }  catch (NumberFormatException ex){
                response.status(400);
                return "invalid GuildID data type";
            }
            response.type("application/json");
            return om.writeValueAsString(Database.GAME_BINDING_STORAGE.getGameBindingsForGame(guildID, request.params(":gameid")));
        }));
        getGameBindingsForMemberGame = (((request, response) -> {
            //guildid, gameid, memberid
            long guildID;
            try {
                guildID = Long.parseLong(request.params(":guildid"));
            }  catch (NumberFormatException ex){
                response.status(400);
                return "invalid GuildID data type";
            }

            long memberID;
            try {
                memberID = Long.parseLong(request.params(":memberid"));
            }  catch (NumberFormatException ex){
                response.status(400);
                return "invalid MemberID data type";
            }
            response.type("application/json");
            return om.writeValueAsString(Database.GAME_BINDING_STORAGE.getGameBindingsForMemberGame(memberID, guildID, request.params(":gameid")));
        }));
        getMemberInfo = (((request, response) -> {
            //guildid, gameid, memberid
            long guildID;
            try {
                guildID = Long.parseLong(request.params(":guildid"));
            }  catch (NumberFormatException ex){
                response.status(400);
                return "invalid GuildID data type";
            }

            long memberID;
            try {
                memberID = Long.parseLong(request.params(":memberid"));
            }  catch (NumberFormatException ex){
                response.status(400);
                return "invalid MemberID data type";
            }
            response.type("application/json");
            return om.writeValueAsString(Database.EXPERIENCE_STORAGE.getMemberInfo(guildID, memberID));
        }));
        getGameBindingsForGameUserID = (((request, response) -> {
            //guildid, gameid, memberid
            long guildID;
            try {
                guildID = Long.parseLong(request.params(":guildid"));
            }  catch (NumberFormatException ex){
                response.status(400);
                return "invalid GuildID data type";
            }
            response.type("application/json");
            return om.writeValueAsString(Database.GAME_BINDING_STORAGE.getGameBindingsForGameGameUserID(guildID, request.params(":gameid"), request.params(":gameuserid")));
        }));
        insertGameBinding = (((request, response) -> {
            //
            GameAccountBinding binding = om.readValue(request.body(), GameAccountBinding.class);
            if (binding == null){
                response.status(500);
                return "Binding not parsed";
            }
            GameAccountBinding ret = Database.GAME_BINDING_STORAGE.addGameBinding(binding);
            if (ret == null){
                response.status(500);
                return "Error";
            }
            response.status(201);
//            response.status(200);
            return om.writeValueAsString(ret);
        }));
        deleteGameBinding = (((request, response) -> {
            //
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = om.readValue(request.body(), HashMap.class);
                //long userID, int bindingID
                Object userIDObj = map.get("userId");
                if (userIDObj == null)
                    userIDObj = map.get("userID");
                if (userIDObj == null)
                    userIDObj = map.get("memberID");
                if (userIDObj == null)
                    userIDObj = map.get("memberId");
                if (userIDObj == null){
                    response.status(400);
                    return "Error: userId/memberId not present";
                }

                long userID = -1;
                if (userIDObj instanceof String){
                    userID = Long.parseLong((String) userIDObj);
                } else if (userIDObj instanceof Long){
                    userID = (Long) userIDObj;
                } else {
                    userID = Long.parseLong(userIDObj.toString());
                }
                int bindingID = 0;
                if (map.containsKey("bindingId")) {
                    bindingID = (Integer) map.get("bindingId");
                } else if (map.containsKey("bindingID")) {
                    bindingID = (Integer) map.get("bindingID");
                } else if (map.containsKey("id")) {
                    bindingID = (Integer) map.get("id");
                } else if (map.containsKey("Id")) {
                    bindingID = (Integer) map.get("Id");
                } else {
                    response.status(400);
                    return "Error: bindingId not present";
                }
                Database.GAME_BINDING_STORAGE.deleteBindingById(userID, bindingID);
                response.status(200);
                HashMap<String, Object> toRet = new HashMap<>();
                toRet.put("bindingId", bindingID);
                return om.writeValueAsString(toRet);
            } catch (ClassCastException | NumberFormatException ex){
                response.status(400);
                return "Bad datatypes.";
            }

        }));
    }



}
