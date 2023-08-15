package uk.co.hexillium.rhul.compsoc.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.exceptions.ParsingException;
import net.dv8tion.jda.api.utils.data.DataArray;
import net.dv8tion.jda.api.utils.data.DataObject;
import net.dv8tion.jda.internal.JDAImpl;
import net.dv8tion.jda.internal.entities.EntityBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import spark.Filter;
import spark.Request;
import spark.Route;
import uk.co.hexillium.rhul.compsoc.persistence.Database;
import uk.co.hexillium.rhul.compsoc.persistence.entities.GameAccountBinding;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static spark.Spark.*;

public class RestAPI {

    private final Logger logger = LogManager.getLogger(RestAPI.class);

    private Filter authCheck;
    private Route getMembers;

    private Route getGameBindingsForMember;
    private Route getGameBindingsForMemberGame;
    private Route getGameBindingsForGame;
    private Route getGameBindingsForGameUserID;
    private Route insertGameBinding;
    private Route deleteGameBinding;
    private Route getMemberInfo;
    private Filter guildCheck;
    private Filter channelCheck;
    private Route getGuildInfo;
    private Route sendMessage;
    private Route updateMembershipRoles;

    private final ScheduledExecutorService timer;

    private final ObjectMapper mapper;

    private List<Token> tokens;

    private final Map<Request, Long> timings = new HashMap<>();

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
            get("/getmembers/:guildid", getMembers); //DEPRECATED; FOR REMOVAL
            path("/guild/:guildid", () ->{
                before("/*", guildCheck);
                get("/info", getGuildInfo);
                get("/members", getMembers);
                post("/memberships", updateMembershipRoles);
                get("/member/:memberid/info", getMemberInfo);
                path("/channels/:channelid", () -> {
                    before("/*", channelCheck);
                    post("/sendmessage", sendMessage);
                });
            });
//            get("/guild/:guildid/member/:memberid/info", getMemberInfo);
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
        guildCheck = ((request, response) -> {
            if (jda.getGuildById(request.params(":guildid")) == null){
                halt(404, "Invalid guild.");
            }
        });
        channelCheck = ((request, response) -> {
            if (jda.getGuildById(request.params(":guildid"))
                    //we know that this isn't null, as the guild has been checked already
                    .getGuildChannelById(request.params(":channelid")) == null){
                halt(404, "Invalid channel, or guild->channel.");
            }
        });
        getGuildInfo = (((request, response) -> {
            Guild guild = jda.getGuildById(request.params(":guildid"));
            if (guild == null){
                logger.error("Guild was null, but passed nullcheck filter.", new IllegalStateException());
                return 500;
            }
            DataObject guildObj = DataObject.empty();
            guildObj.put("name", guild.getName());
            guildObj.put("snowflake", guild.getId());
            guildObj.put("memberCount", guild.getMemberCount());
            DataArray channels = DataArray.empty();
            for (GuildChannel channel : guild.getChannels()){
                DataObject channelObj = DataObject.empty();
                channelObj.put("name", channel.getName());
                if (channel instanceof TextChannel tc) {
                    channelObj.put("description", tc.getTopic());
                }
                channelObj.put("type", channel.getType().name());
                channelObj.put("snowflake", channel.getId());
                channels.add(channelObj);
            }
            response.type("application/json");
            return guildObj.toJson();
        }));
        sendMessage = (((request, response) -> {
            DataObject messageJson = DataObject.fromJson(request.body());
            TextChannel channel = jda.getGuildById(request.params(":guildid"))
                    //we know that this isn't null, as the guild has been checked already
                    .getTextChannelById(request.params(":channelid"));
            if (channel == null){
                halt(404, "Channel is not of type TEXT.");
                return "Channel is not of type TEXT.";
            }
            EntityBuilder entityBuilder = ((JDAImpl) jda).getEntityBuilder();
            String content = messageJson.getString("content", "");
            Collection<MessageEmbed> msgEmbeds = new ArrayList<>();
            try {
                DataArray embeds = messageJson.getArray("embeds");
                for (Object embedO : embeds){
                    DataObject embedObj = (DataObject) embedO;
                    embedObj.put("type", "rich");
                    msgEmbeds.add(entityBuilder.createMessageEmbed(embedObj));
                }
            } catch (ParsingException ignored){}
            if (content.isEmpty() && msgEmbeds.isEmpty()){
                return "Error; no content specified.";
            }
            try {
                if (!content.isEmpty()) {
                    channel.sendMessage(content).setEmbeds(msgEmbeds).queue();
                } else {
                    channel.sendMessageEmbeds(msgEmbeds).queue();
                }
                return "Probably a success.";
            } catch (Exception ex){
                return ex.getMessage();
            }
        }));
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
        updateMembershipRoles = (request, response) -> {
            @SuppressWarnings("unchecked")
            HashMap<String, Object> map = om.readValue(request.body(), HashMap.class);
            Object idsObj = map.get("ids");
            if (idsObj == null){
                response.status(400);
                return "Missing \"ids\" array";
            }
            if (!(idsObj instanceof List)){
                response.status(400);
                return "\"ids\" is not of type array";
            }
            long guildId = Long.parseLong(request.params(":guildid"));

            @SuppressWarnings("unchecked")
            List<String> ids = (List<String>) map.get("ids");

            List<Long> discordUsers = Database.STUDENT_VERIFICATION.findStudentsWithSUIds(ids.toArray(new String[0]));

            if (discordUsers == null){
                response.status(500);
                return "Failure running server-side SQL.";
            }

            HashSet<Long> discordUsersSet = new HashSet<>(discordUsers);

            long roleId = 1019906006303649802L;
            Guild guild = jda.getGuildById(guildId);
            Role role = guild.getRoleById(roleId);

            Set<Member> membersWithRole = guild.getMemberCache().stream().filter(m -> m.getRoles().contains(role)).collect(Collectors.toSet());
            Set<Member> membersRoleTarget = guild.getMemberCache().stream().filter(m -> discordUsersSet.contains(m.getIdLong())).collect(Collectors.toSet());

            HashSet<Member> common = new HashSet<>(membersWithRole);
            common.retainAll(membersRoleTarget);

            membersRoleTarget.removeAll(common);
            membersWithRole.removeAll(common);


            membersWithRole.forEach(m -> m.getGuild().removeRoleFromMember(m, role).queue());
            membersRoleTarget.forEach(m -> m.getGuild().addRoleToMember(m, role).queue());

            String result = "Added role to " + membersRoleTarget.size() + " members, removed role from " + membersWithRole.size() + " members, and " + common.size() + " unchanged.";
            logger.info("New: " + membersRoleTarget + ", Current: " + common + ", Expired: " + membersWithRole);

            logger.info(result);

            response.status(200);
            return result;

        };
    }



}
