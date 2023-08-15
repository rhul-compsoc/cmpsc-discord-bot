package uk.co.hexillium.rhul.compsoc.commands.handlers;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.GenericSelectMenuInteractionEvent;

import java.util.List;

public interface ComponentInteractionHandler {

    void initComponentInteractionHandle(JDA jda);

    default void handleButtonInteraction(ButtonInteractionEvent interaction, String button){};
    default void handleSelectionMenuInteraction(GenericSelectMenuInteractionEvent<?, ?> interaction){};

    List<String> registerHandles();

}
