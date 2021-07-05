package uk.co.hexillium.rhul.compsoc.commands;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;

import java.util.List;

public interface SlashCommandHandler {

    default void initSlashCommandHandler(JDA jda){};

    List<CommandData> registerGlobalCommands();

    default List<CommandData> registerGuildRestrictedCommands(Guild guild){
        return null;
    }

    void handleSlashCommand(SlashCommandEvent event);
}
