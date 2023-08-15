package uk.co.hexillium.rhul.compsoc.commands.handlers;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.UserContextInteractionEvent;

public interface UserCommandHandler extends InteractionCommandHandler {

    void handleUserContextCommand(UserContextInteractionEvent event);

    @Override
    default void handleCommand(GenericInteractionCreateEvent event) {
        if (event instanceof UserContextInteractionEvent event1){
            handleUserContextCommand(event1);
        }
    }
}
