package uk.co.hexillium.rhul.compsoc.commands;

import uk.co.hexillium.rhul.compsoc.CommandDispatcher;
import uk.co.hexillium.rhul.compsoc.CommandEvent;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Message;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TestCommand extends Command {

    private static final String[] COMMANDS = {"testcommand", "test"};

    public TestCommand() {
        super("TestCommand", "A simple test command", "`{{cmd_prefix}}testCommand`, idk really what it does.", COMMANDS, "debug");
        this.requiredBotPermissions = new Permission[]{Permission.ADMINISTRATOR};
        this.requiredUserPermissions = new Permission[]{Permission.ADMINISTRATOR};
    }

    Logger logger = LogManager.getLogger(TestCommand.class);

    @Override
    public void handleCommand(CommandEvent event) {
        event.sendEmbed("This is it.", "Literally nothing else.", 0x000000);

    }

    @Override
    public void onLoad(JDA jda, CommandDispatcher dispatcher) {
        logger.info("Test command loaded!");
    }
}
