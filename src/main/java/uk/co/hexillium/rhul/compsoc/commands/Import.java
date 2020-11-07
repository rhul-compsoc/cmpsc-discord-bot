package uk.co.hexillium.rhul.compsoc.commands;

import uk.co.hexillium.rhul.compsoc.CommandEvent;
import uk.co.hexillium.rhul.compsoc.persistence.Database;

public class Import extends Command {

    private static final String[] commands = {"importtodb"};

    public Import() {
        super("Import", "Import users of this guild into the database", "help stub", commands, "debug");
    }

    @Override
    public void handleCommand(CommandEvent event) {
        if (event.getAuthor().getIdLong() != 187979032904728576L) return;
        Database.runLater( () -> {
            Database.EXPERIENCE_STORAGE.importMembers(event.getGuild().getMemberCache());
        });
    }
}
