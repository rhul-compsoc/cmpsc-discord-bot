package uk.co.hexillium.rhul.compsoc.commands;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.events.interaction.SelectionMenuEvent;
import net.dv8tion.jda.api.interactions.components.ButtonInteraction;
import uk.co.hexillium.rhul.compsoc.crypto.HMAC;

import java.util.List;

public interface ComponentInteractionHandler {

    void initComponentInteractionHandle(JDA jda);

    default void handleButtonInteraction(ButtonClickEvent interaction, String button){};
    default void handleSelectionMenuInteraction(SelectionMenuEvent interaction){};

    List<String> registerHandles();

}
