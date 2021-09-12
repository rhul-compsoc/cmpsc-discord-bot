package uk.co.hexillium.rhul.compsoc.commands;

import net.dv8tion.jda.api.entities.TextChannel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import uk.co.hexillium.rhul.compsoc.CommandEvent;
import uk.co.hexillium.rhul.compsoc.persistence.Database;

public class Import extends Command {

    private static final String[] commands = {"importtodb"};

    private static final Logger logger = LogManager.getLogger(Import.class);

    public Import() {
        super("Import", "Import users of this guild into the database", "help stub", commands, "debug");
    }

    @Override
    public void handleCommand(CommandEvent event) {
        if (event.getAuthor().getIdLong() != 187979032904728576L) return;
        Database.runLater( () -> {
            Database.EXPERIENCE_STORAGE.importMembers(event.getGuild().getMemberCache());
        });
        for (TextChannel channel : event.getGuild().getTextChannels()){
            Database.runLater(() -> backlogMessages(channel));
        }
    }

    private void backlogMessages(TextChannel channel){
        channel.getIterableHistory().takeWhileAsync((m) -> true).thenAcceptAsync(
                (msgs) -> {
                    Database.MESSAGE_STORAGE.insertBulkMessage(msgs);
                    logger.info("inserted " + msgs.size() + " into " + channel.getName());
                }
        );
        logger.info("Completed logging of " + channel.getName());
    }
}
