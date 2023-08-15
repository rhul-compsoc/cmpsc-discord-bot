package uk.co.hexillium.rhul.compsoc.commands.handlers;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

public interface SlashCommandHandler extends InteractionCommandHandler {

    void handleSlashCommand(SlashCommandInteractionEvent event);

    @Override
    default void handleCommand(GenericInteractionCreateEvent event){
        if (event instanceof SlashCommandInteractionEvent event1){
            handleSlashCommand(event1);
        }
    }
}
