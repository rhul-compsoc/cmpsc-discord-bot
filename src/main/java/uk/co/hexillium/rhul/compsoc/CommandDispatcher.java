package uk.co.hexillium.rhul.compsoc;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ScanResult;
import uk.co.hexillium.rhul.compsoc.commands.Command;
import uk.co.hexillium.rhul.compsoc.persistence.Database;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.events.message.priv.PrivateMessageReceivedEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import uk.co.hexillium.rhul.compsoc.persistence.entities.GuildSettings;
import uk.co.hexillium.rhul.compsoc.time.JobScheduler;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CommandDispatcher {

    public final static String defaultCommandDelimiter = "!";

    final static Logger logger = LogManager.getLogger(CommandDispatcher.class);

    private List<Command> commands;
    private HashMap<String, Command> triggerMap;

    public CommandDispatcher() {
        this.commands = new ArrayList<>();
        this.triggerMap = new HashMap<>();

        String pkg = "uk.co.hexillium.rhul.compsoc.commands";
        try (ScanResult scanResult =
                     new ClassGraph()
                             .enableClassInfo()
                             .whitelistPackages(pkg)
                             .enableAnnotationInfo()
                             .scan()) {
            System.out.println(scanResult.getAllClasses().toString());
            for (ClassInfo routeClassInfo : scanResult.getSubclasses(pkg + ".Command")
                    .exclude(scanResult.getClassesWithAnnotation("uk.co.hexillium.rhul.compsoc.Disabled"))) {
                try {
                    this.commands.add((Command) routeClassInfo.loadClass().getDeclaredConstructor().newInstance());
                    logger.info("Loaded command " + routeClassInfo.loadClass().getName());
                } catch (InstantiationException | IllegalAccessException | InvocationTargetException |
                        NoSuchMethodException | IllegalArgumentException e) {
                    logger.error("Failed to instantiate command " + routeClassInfo.loadClass().getName() + ", " + e.getMessage());
                }
            }
        }

        for (Command command : commands){
            for (String trigger : command.getCommands()){
                triggerMap.put(trigger, command);
            }
        }
    }

    public void onLoad(JDA jda) {
        for (Command c : commands) {
            c.onLoad(jda, this);
        }
    }
    public void loadScheduler(JobScheduler scheduler) {
        for (Command c : commands) {
            c.setScheduler(scheduler);
        }
    }

    public void dispatchCommand(GuildMessageReceivedEvent event) {
        if (event.getAuthor().isBot() || event.getMember() == null) return;
        String message = event.getMessage().getContentRaw();
        fetchGuildData(event.getGuild().getIdLong(), settings -> {
            String delim = settings == null ? defaultCommandDelimiter : settings.getPrefix();
            if (message.startsWith(delim)) {
                String[] args = message.split("\\s+");
                String command = args[0].substring(defaultCommandDelimiter.length());
                Command toRun = findCommand(command, true);
                if (toRun == null) return;
                logger.info("[guildid: " + event.getGuild().getIdLong() + "/user: " + event.getAuthor().getAsTag() + "] ran guild command " + command + " with args " + Arrays.toString(args));
                CommandEvent cmdE = new CommandEvent(event, settings);
                toRun.internalHandleCommand(cmdE);
            }
        });

    }

    private void fetchGuildData(long guildID, Consumer<GuildSettings> settings){
        if (Database.GUILD_DATA == null || true){ //todo
            Database.runLater(() -> {
                settings.accept(GuildSettings.getDefault(guildID));
            });
            return;
        }
        Database.GUILD_DATA.fetchData(guildID, settings, null);
    }

    private Command findCommand(String trigger, boolean guildCommand){
        Command command = triggerMap.get(trigger);
        if (command == null) return null;
        return !command.requireGuild() || guildCommand ? command : null;
    }

    private List<Command> getCommandsForTrigger(String trigger, boolean guildCommand) {
        return commands.stream().filter(c -> guildCommand || !c.requireGuild()).filter(c -> Stream.of(c.getCommands()).anyMatch(trigger::equalsIgnoreCase)).collect(Collectors.toList());
    }

    public void dispatchCommand(PrivateMessageReceivedEvent event) {
        String message = event.getMessage().getContentRaw();
        if (!message.startsWith(defaultCommandDelimiter)) return;
        String[] args = message.split("\\s+");
        String command = args[0].substring(defaultCommandDelimiter.length());
        Database.runLater(() -> {
            Command toRun = findCommand(command, false);
            if (toRun == null) return;
            logger.info("[DMs/user: " + event.getAuthor().getAsTag() + "] ran private command " + command + " with args " + Arrays.toString(args));
            CommandEvent cmdE = new CommandEvent(event);
            toRun.internalHandleCommand(cmdE);
        });

    }

    public boolean isCommand(String cmd) {
        return commands.stream().anyMatch(c -> Arrays.stream(c.getCommands()).anyMatch(cmd::equalsIgnoreCase));
    }

    public List<Command> getCommands() {
        return commands;
    }

    public Command getCommand(String cmd) {
        return commands.stream().filter(c -> Arrays.stream(c.getCommands()).anyMatch(cmd::equalsIgnoreCase)).findFirst().orElse(null);
    }


}
