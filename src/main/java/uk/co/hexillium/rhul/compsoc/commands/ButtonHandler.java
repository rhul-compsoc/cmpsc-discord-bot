package uk.co.hexillium.rhul.compsoc.commands;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.interactions.components.ButtonInteraction;
import uk.co.hexillium.rhul.compsoc.crypto.HMAC;

import java.util.List;

public interface ButtonHandler {

    void initButtonHandle(HMAC hmac, JDA jda);

    void handleButtonInteraction(ButtonInteraction interaction, String button, boolean userLockedHMAC);

    List<String> registerHandles();

}
