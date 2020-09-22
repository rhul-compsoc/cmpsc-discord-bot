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

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CommandDispatcher {

    private final static String defaultCommandDelimiter = "!";

    Logger logger = LogManager.getLogger(CommandDispatcher.class);

    private List<Command> commands;

    public CommandDispatcher() {
        this.commands = new ArrayList<>();

        String pkg = "uk.co.hexillium.rhul.compsoc.commands";
        try (ScanResult scanResult =
                     new ClassGraph()
                             .enableClassInfo()
                             .whitelistPackages(pkg)
                             .enableAnnotationInfo()
                             .scan()) {
            System.out.println(scanResult.getAllClasses().toString());
            for (ClassInfo routeClassInfo : scanResult.getSubclasses(pkg + ".Command")) {
                System.out.println("Found " + routeClassInfo.toString());
                try {
                    this.commands.add((Command) routeClassInfo.loadClass().getDeclaredConstructor().newInstance());
                    logger.info("Loaded " + routeClassInfo.loadClass().getName());
                } catch (InstantiationException | IllegalAccessException | InvocationTargetException |
                        NoSuchMethodException | IllegalArgumentException e) {
                    logger.error("Failed to instantiate " + routeClassInfo.loadClass().getName() + ", " + e.getMessage());
                }
            }
        }
    }

    public void onLoad(JDA jda) {
        for (Command c : commands) {
            c.onLoad(jda, this);
        }
    }

    public void dispatchCommand(GuildMessageReceivedEvent event) {
        if (event.getAuthor().isBot() || event.getMember() == null) return;
        String message = event.getMessage().getContentRaw();
        Database.GUILD_DATA.fetchData(event.getGuild().getIdLong(), settings -> {
            String delim = settings == null ? defaultCommandDelimiter : settings.getPrefix();
            if (message.startsWith(delim)) {
                String[] args = message.split("\\s+");
                String command = args[0].substring(defaultCommandDelimiter.length());
                List<Command> triggers = getCommandsForTrigger(command, true);
                if (triggers.size() == 0) return;
                logger.info("[guildid: " + event.getGuild().getIdLong() + "/user: " + event.getAuthor().getAsTag() + "] ran guild commands " + triggers.stream().map(c -> c.getClass().getSimpleName()).collect(Collectors.joining(" ")));
                for (Command cmd : triggers) {
                    CommandEvent cmdE = new CommandEvent(event);
                    cmd.internalHandleCommand(cmdE);
                }

            }
        }, err -> {});

    }

    private List<Command> getCommandsForTrigger(String trigger, boolean guildCommand) {
        return commands.stream().filter(c -> guildCommand || !c.requireGuild()).filter(c -> Stream.of(c.getCommands()).anyMatch(trigger::equalsIgnoreCase)).collect(Collectors.toList());
    }

    public void dispatchCommand(PrivateMessageReceivedEvent event) {
        String message = event.getMessage().getContentRaw();
        if (!message.startsWith(defaultCommandDelimiter)) return;
        String[] args = message.split("\\s+");
        String command = args[0].substring(defaultCommandDelimiter.length());

        List<Command> triggers = getCommandsForTrigger(command, false);
        if (triggers.size() == 0) return;
        logger.info("Ran private commands " + triggers.stream().map(c -> c.getClass().getSimpleName()).collect(Collectors.joining(" ")));
        for (Command cmd : triggers) {
            CommandEvent cmdE = new CommandEvent(event);
            cmd.internalHandleCommand(cmdE);
        }
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
