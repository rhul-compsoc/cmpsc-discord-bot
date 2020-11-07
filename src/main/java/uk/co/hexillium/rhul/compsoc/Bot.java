package uk.co.hexillium.rhul.compsoc;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import uk.co.hexillium.rhul.compsoc.api.RestAPI;
import uk.co.hexillium.rhul.compsoc.chat.ChatXP;
import uk.co.hexillium.rhul.compsoc.handlers.InformationUpdateHandler;
import uk.co.hexillium.rhul.compsoc.persistence.Database;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import net.dv8tion.jda.internal.requests.RateLimiter;
import net.dv8tion.jda.internal.requests.ratelimit.BotRateLimiter;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import uk.co.hexillium.rhul.compsoc.time.JobScheduler;

import javax.security.auth.login.LoginException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class Bot {

    static String[] emojis = {"✅", "❓", "❌"};
    static Database database = Database.getInstance();
    static Logger logger = LogManager.getLogger(Bot.class);
    static ChatXP chatXP;
    static JDA jda;

    public static void main(String[] args) throws IOException, LoginException, InterruptedException {
//        Configurator.setLevel(BotRateLimiter.class.getName(), Level.TRACE);
//        Configurator.setLevel(RateLimiter.class.getName(), Level.TRACE);
        //read the token in
        jda = initBot();
//        new CommandDispatcher();


        RestAPI api = new RestAPI(6570, "api", jda, new ObjectMapper());
        ArrayList<String> argList = new ArrayList<>(Arrays.asList(args));
        if (argList.contains("-genToken")){
            Database.AUTH_TOKEN_STORAGE.addAuthToken(System.currentTimeMillis() + 1000 * 60 * 60 * 24 * 365, "Year-long token", bytes -> {
                logger.info("Super-secret token -> " + Base64.getEncoder().encodeToString(bytes));
            }, fail -> logger.error(fail.getMessage()));
        }
    }

    private static JDA initBot() throws IOException, LoginException, InterruptedException {
        List<String> lines = Files.readAllLines(Paths.get("token.txt"));
        EventManager manager = new EventManager();
        JDA jda = JDABuilder.createDefault(lines.get(0), EnumSet.allOf(GatewayIntent.class))
                .enableCache(EnumSet.allOf(CacheFlag.class))
                .setMemberCachePolicy(MemberCachePolicy.ALL)
                .setChunkingFilter(ChunkingFilter.ALL)
                .addEventListeners(manager)
                .build();
        CommandDispatcher dispatcher = new CommandDispatcher();
        manager.setDispatcher(dispatcher);
        JobScheduler scheduler = new JobScheduler(database, jda);
        dispatcher.loadScheduler(scheduler);
        chatXP = new ChatXP(jda);
        jda.awaitReady();
        InformationUpdateHandler updateHandler = new InformationUpdateHandler(jda);
        return jda;
    }

}
