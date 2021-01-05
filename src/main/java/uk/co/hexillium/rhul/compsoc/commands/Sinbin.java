package uk.co.hexillium.rhul.compsoc.commands;

import net.dv8tion.jda.api.Permission;
import uk.co.hexillium.rhul.compsoc.CommandEvent;
import uk.co.hexillium.rhul.compsoc.Disabled;

@Disabled
public class Sinbin extends Command {

    private static final String[] commands = {"sinbin", "banish"};

    public Sinbin() {
        super("Sinbin", "Sinbin a naughty member.", "Strips a user of all roles", commands, "admin");
    }

    @Override
    public void handleCommand(CommandEvent event) {

        if (!event.getMember().hasPermission(Permission.ADMINISTRATOR)) {
            event.reply("You can't use this without administrator perms.");
            return;
        }
        if (!event.getSelfMember().hasPermission(Permission.ADMINISTRATOR)){
            event.reply("I don't have administrator permissions.");
            return;
        }

        if (event.getArgs().length == 0){
            event.reply("Specify a member to sinbin.  Specify an optional time argument to un-sinbin them later.");
            return;
        }



    }
}
