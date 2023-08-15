package uk.co.hexillium.rhul.compsoc;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ScanResult;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.GenericSelectMenuInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import uk.co.hexillium.rhul.compsoc.commands.Command;
import uk.co.hexillium.rhul.compsoc.commands.handlers.ComponentInteractionHandler;
import uk.co.hexillium.rhul.compsoc.commands.handlers.InteractionCommandHandler;
import uk.co.hexillium.rhul.compsoc.commands.handlers.SlashCommandHandler;
import uk.co.hexillium.rhul.compsoc.crypto.HMAC;
import uk.co.hexillium.rhul.compsoc.persistence.Database;
import uk.co.hexillium.rhul.compsoc.persistence.entities.GuildSettings;
import uk.co.hexillium.rhul.compsoc.time.JobScheduler;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CommandDispatcher {

    public final static String defaultCommandDelimiter = "!";

    final static Logger logger = LogManager.getLogger(CommandDispatcher.class);

    private List<Command> commands;
    private HashMap<String, Command> triggerMap;
    private HashMap<String, ComponentInteractionHandler> buttonMap;
    private List<ComponentInteractionHandler> buttons;
//    private List<SlashCommandHandler> slashCommands;
    private List<InteractionCommandHandler> interactionCommandHandlers;
    private HashMap<String, InteractionCommandHandler> slashCommandMap;
    private final HMAC hmac;

    public CommandDispatcher() throws NoSuchAlgorithmException {
        this.commands = new ArrayList<>();
        this.triggerMap = new HashMap<>();
        this.buttonMap = new HashMap<>();
        this.slashCommandMap = new HashMap<>();

        buttons = new ArrayList<>();
        interactionCommandHandlers = new ArrayList<>();


        String pkg = "uk.co.hexillium.rhul.compsoc.commands";
        try (ScanResult scanResult =
                     new ClassGraph()
                             .enableClassInfo()
                             .acceptPackages(pkg)
                             .enableAnnotationInfo()
                             .scan()) {
            System.out.println(scanResult.getAllClasses().toString());
            for (ClassInfo routeClassInfo : scanResult.getSubclasses(pkg + ".Command")
                    .union(scanResult.getClassesImplementing(pkg + ".ComponentInteractionHandler"))
                    .exclude(scanResult.getClassesWithAnnotation("uk.co.hexillium.rhul.compsoc.Disabled"))) {
                try {
                    Class<?> current = routeClassInfo.loadClass();
                    Object newInst = current.getDeclaredConstructor().newInstance();
                    logger.info("Inspecting " + current.getName() + " to load");
                    if (newInst instanceof ComponentInteractionHandler){
                        buttons.add((ComponentInteractionHandler) newInst);
                        logger.info("Adding Button Handler " + current.getName());
                    }
                    if (newInst instanceof Command){
                        this.commands.add((Command) newInst);
                        logger.info("Adding Command Handler " + current.getName());
                    }
                    if (newInst instanceof InteractionCommandHandler){
                        this.interactionCommandHandlers.add((SlashCommandHandler) newInst);
                        logger.info("Adding Slash Command Handler " + current.getName());
                    }
                    logger.info("Loaded " + routeClassInfo.loadClass().getName());
                } catch (InstantiationException | IllegalAccessException | InvocationTargetException |
                        NoSuchMethodException | IllegalArgumentException e) {
                    logger.error("Failed to instantiate handler " + routeClassInfo.loadClass().getName() + ".", e);
                }
            }
        }

        for (Command command : commands){
            for (String trigger : command.getCommands()){
                triggerMap.put(trigger, command);
            }
        }

        for (ComponentInteractionHandler handler : buttons){
            for (String trigger : handler.registerHandles()){
                this.buttonMap.put(trigger, handler);
            }
        }

        byte[] sk = new byte[1];
        try {
            sk = getHMACSecretKey();
        } catch (IOException e) {
            logger.error("Failed to initialise key for HMAC.  Expect buttons to be impacted.");
        }
        hmac = new HMAC(sk);

    }

    private byte[] getHMACSecretKey() throws IOException {
        //check if a keyfile exists
        File keyFile = new File("keyfile.bin");
        if (keyFile.exists()){
            return Files.readAllBytes(Path.of("keyfile.bin"));
        }
        logger.warn("Key generation needed. Generating key...");
        Random secureRandom;
        try {
            secureRandom = SecureRandom.getInstanceStrong();
        } catch (NoSuchAlgorithmException e) {
            logger.error("Failed to get a secure random instance");
            secureRandom = new Random();
        }
        byte[] secretKey = new byte[32];
        secureRandom.nextBytes(secretKey);

        //save it
        Files.write(Path.of("keyfile.bin"), secretKey);
        logger.info("Saved keyfile.");

        return secretKey;
    }


    public void onLoad(JDA jda) {
        for (Command c : commands) {
            c.onLoad(jda, this);
        }
        for (ComponentInteractionHandler handler : buttons){
            handler.initComponentInteractionHandle(jda);
        }
        CommandListUpdateAction updateCmds = jda.getGuildById(500612695570120704L).updateCommands();
//        CommandListUpdateAction updateCmds = jda.updateCommands();
        for (InteractionCommandHandler handler : interactionCommandHandlers){
            handler.initSlashCommandHandler(jda);
            List<CommandData> data = handler.registerGlobalCommands();
            updateCmds.addCommands(data);
            for (CommandData cmd : data) {
                this.slashCommandMap.put(cmd.getName(), handler);
            }
        }
        updateCmds.queue();

    }

    public void loadScheduler(JobScheduler scheduler) {
        for (Command c : commands) {
            c.setScheduler(scheduler);
        }
        scheduler.initialise();
    }

    public void dispatchCommand(MessageReceivedEvent event){
        if (event.getAuthor().isBot() || (event.isFromGuild() && event.getMember() == null) || event.isWebhookMessage()) return;
        String message = event.getMessage().getContentRaw();
        if (!message.startsWith(defaultCommandDelimiter)) return;

        String[] args = message.split("\\s+");
        String command = args[0].substring(defaultCommandDelimiter.length());
        logger.info("[{}] ran command {} with args {}.", event.getAuthor().getName(), command, args);
        Database.runLater(() -> {
            Command toRun = findCommand(command, event.isFromGuild());
            if (toRun == null) return;
            CommandEvent cmdE = new CommandEvent(event);
            toRun.internalHandleCommand(cmdE);
        });
    }

    public void handleSlashCommand(SlashCommandInteractionEvent event){
        String key = event.getName();
        InteractionCommandHandler handler = slashCommandMap.get(key);
        if (handler == null){
            logger.error("Unregistered command ID: " + event.getCommandId() + ", tag: " + event.getName() + " " + event);
            return;
        }
        logger.info("Member " + event.getMember() + " executed " + handler.getClass().getName() + " slash command " + event.getCommandString());
        Database.runLater(() -> {
            try {
                handler.handleCommand(event);
            } catch (Exception ex){
                logger.error("Failed to execute slash command ", ex);
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


    public boolean isCommand(String cmd) {
        return commands.stream().anyMatch(c -> Arrays.stream(c.getCommands()).anyMatch(cmd::equalsIgnoreCase));
    }

    public List<Command> getCommands() {
        return commands;
    }

    public Command getCommand(String cmd) {
        return commands.stream().filter(c -> Arrays.stream(c.getCommands()).anyMatch(cmd::equalsIgnoreCase)).findFirst().orElse(null);
    }


    public void dispatchButtonPress(ButtonInteractionEvent event) {
        //perform HMAC check:
        //HMAC is no longer needed...
//        String comp = event.getComponentId();
//        if (comp.length() <= 23){
//            event.reply("Failed interaction; component not long enough to contain a valid MAC.").setEphemeral(true).queue();
//            return;
//        }
//        String hmac_str = comp.substring(comp.length() - 23);
//        String usr = comp.substring(0, comp.length() - 23);
//        boolean memberLock = hmac.verify(usr.getBytes(StandardCharsets.UTF_8), HMAC.toByteArray(hmac_str), event.getUser().getIdLong(), event.getChannel().getIdLong());
//        boolean channelLock = hmac.verify(usr.getBytes(StandardCharsets.UTF_8), HMAC.toByteArray(hmac_str), 0, event.getChannel().getIdLong());
//
//        if (!memberLock && !channelLock){
//            // this interaction dies here, and is dropped due to being faulty.
//            logger.warn("Attempted interaction forgery, or HMAC error. From " + event.getUser() + " with content: " + event.getComponentId());
//            return;
//        }
        String[] components = event.getComponentId().split("\\|", 2);
        ComponentInteractionHandler handler = buttonMap.get(components[0]);
        logger.info("User " + event.getUser() + " dispatched " + handler.getClass().getName() + " with button interaction + " + event.getComponentId());
        String dataTag = components.length == 1 ? "" : components[1];
        Database.runLater(() -> {
            handler.handleButtonInteraction(event, dataTag);
        });
    }

    public void dispatchSelectionMenu(GenericSelectMenuInteractionEvent<?, ?> event){
        String[] components = event.getComponentId().split("\\|", 2);
        ComponentInteractionHandler handler = buttonMap.get(components[0]);
        logger.info("User " + event.getUser() + " dispatched " + handler.getClass().getName() + " with selectionMenu interaction + " + event.getComponentId());
        Database.runLater(() -> {
            handler.handleSelectionMenuInteraction(event);
        });
    }
}
