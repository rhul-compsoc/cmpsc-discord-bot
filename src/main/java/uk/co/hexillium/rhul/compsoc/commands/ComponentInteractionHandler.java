package uk.co.hexillium.rhul.compsoc.commands;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.events.interaction.SelectionMenuEvent;
import net.dv8tion.jda.api.interactions.components.ButtonInteraction;
import uk.co.hexillium.rhul.compsoc.crypto.HMAC;

import java.util.List;

public interface ComponentInteractionHandler {

    void initComponentInteractionHandle(HMAC hmac, JDA jda);

    default void handleButtonInteraction(ButtonClickEvent interaction, String button, boolean userLockedHMAC){};
    default void handleSelectionMenuInteraction(SelectionMenuEvent interaction, String button, boolean userLockedHMAC){};

    List<String> registerHandles();

}
