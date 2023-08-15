package uk.co.hexillium.rhul.compsoc;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.UserContextInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.GenericSelectMenuInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;

public class EventManager implements EventListener{

    CommandDispatcher dispatcher;
    final static private Logger logger = LogManager.getLogger(EventManager.class);

    EventManager(){}

    volatile boolean missingDispatcher = true;
    JDA jda;

    public void setDispatcher(CommandDispatcher dispatcher) {
        this.dispatcher = dispatcher;
        if (!missingDispatcher){
            dispatcher.onLoad(jda);
        }
        missingDispatcher = false;
    }

    @Override
    public void onEvent(@Nonnull GenericEvent genericEvent) {
        if (genericEvent instanceof ReadyEvent event){
            System.out.println("Ready!");
            if (missingDispatcher){
                missingDispatcher = false;
                jda = event.getJDA();
            } else {
                dispatcher.onLoad(event.getJDA());
            }
        }
        if (dispatcher == null){
            logger.debug("Event failed due to missing dispatcher.");
            return;
        }
        if (genericEvent instanceof MessageReceivedEvent event){
                dispatcher.dispatchCommand(event);
        } else if (genericEvent instanceof ButtonInteractionEvent event){
                dispatcher.dispatchButtonPress(event);
        } else if (genericEvent instanceof SlashCommandInteractionEvent event){
            dispatcher.handleSlashCommand(event);
        } else if (genericEvent instanceof GenericSelectMenuInteractionEvent<?,?> event){
            dispatcher.dispatchSelectionMenu( event);
        } else if (genericEvent instanceof UserContextInteractionEvent event){
            dispatcher.handleUserContextCommand(event);
        }
    }
}
