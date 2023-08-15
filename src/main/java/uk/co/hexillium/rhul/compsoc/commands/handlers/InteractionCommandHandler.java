package uk.co.hexillium.rhul.compsoc.commands.handlers;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;

import java.util.List;

public interface InteractionCommandHandler {

    default void initSlashCommandHandler(JDA jda){};

    List<CommandData> registerGlobalCommands();

    default List<CommandData> registerGuildRestrictedCommands(Guild guild){
        return null;
    }

    void handleCommand(GenericInteractionCreateEvent event);

}
