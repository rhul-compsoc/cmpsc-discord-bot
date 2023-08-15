package uk.co.hexillium.rhul.compsoc;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.JDAInfo;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import uk.co.hexillium.rhul.compsoc.api.RestAPI;
import uk.co.hexillium.rhul.compsoc.chat.ChatXP;
import uk.co.hexillium.rhul.compsoc.handlers.InformationUpdateHandler;
import uk.co.hexillium.rhul.compsoc.mail.Mail;
import uk.co.hexillium.rhul.compsoc.persistence.Database;
import uk.co.hexillium.rhul.compsoc.time.JobScheduler;

import javax.security.auth.login.LoginException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.util.*;

public class Bot {

    public static final String[] emojis = {"✅", "❓", "❌"};
    public static final Database database = Database.getInstance();
    public static Mail mail;
    static final Logger logger = LogManager.getLogger(Bot.class);
    static ChatXP chatXP;
    static JDA jda;

    public static void main(String[] args) throws IOException, LoginException, InterruptedException, NoSuchAlgorithmException {
//        Configurator.setLevel(BotRateLimiter.class.getName(), Level.TRACE);
//        Configurator.setLevel(RateLimiter.class.getName(), Level.TRACE);
        //read the token in

        Properties properties;
        Path mailconfig = Paths.get("mail.properties");
        properties = new Properties(Mail.defaultMailProperties);
        if (!Files.exists(mailconfig)){
            properties.store(Files.newBufferedWriter(mailconfig), "mail.properties");
            mail = null;
            logger.warn("Missing mail properties.  Mail will not work with these unset.");
        } else {
            properties.load(Files.newBufferedReader(mailconfig));

            Mail.MailConfiguration configuration = new Mail.MailConfiguration();
            configuration.setFromAddress(properties.getProperty("mail.from.email"));
            configuration.setUsername(properties.getProperty("host.mailbox.username"));
            configuration.setPassword(properties.getProperty("host.mailbox.password"));
            configuration.setPort(Short.parseShort(properties.getProperty("host.port")));
            configuration.setHostUrl(properties.getProperty("host.url"));

            mail = new Mail(configuration);
        }
        jda = initBot();
//        new CommandDispatcher();


        RestAPI api = new RestAPI(6570, "api", jda, new ObjectMapper());
        ArrayList<String> argList = new ArrayList<>(Arrays.asList(args));
        if (argList.contains("-genToken")){
            Database.AUTH_TOKEN_STORAGE.addAuthToken(System.currentTimeMillis() + 1000L * 60 * 60 * 24 * 365, "Year-long token", bytes -> {
                logger.info("Super-secret token -> " + Base64.getEncoder().encodeToString(bytes));
            }, fail -> logger.error(fail.getMessage()));
        }
    }

    private static JDA initBot() throws IOException, LoginException, InterruptedException, NoSuchAlgorithmException {
        List<String> lines = Files.readAllLines(Paths.get("token.txt"));
        EventManager manager = new EventManager();
        JDA jda = JDABuilder.createDefault(lines.get(0), EnumSet.allOf(GatewayIntent.class))
                .enableCache(EnumSet.allOf(CacheFlag.class))
                .setMemberCachePolicy(MemberCachePolicy.ALL)
                .setChunkingFilter(ChunkingFilter.ALL)
                .addEventListeners(manager)
                .build();
        JobScheduler scheduler = new JobScheduler(database, jda);
        CommandDispatcher dispatcher = new CommandDispatcher();
        dispatcher.loadScheduler(scheduler);
        manager.setDispatcher(dispatcher);
        chatXP = new ChatXP(jda);
        jda.awaitReady();
        InformationUpdateHandler updateHandler = new InformationUpdateHandler(jda);
        updateHandler.ready(jda);
        //MessageAccumulator accumulator = new MessageAccumulator(jda);
        logger.info("CompSocBot started, running JDA version " + JDAInfo.VERSION + "!");
        return jda;
    }

}
