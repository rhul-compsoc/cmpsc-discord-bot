package uk.co.hexillium.rhul.compsoc;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.ReadyEvent;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.events.message.priv.PrivateMessageReceivedEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.interactions.components.ButtonInteraction;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import uk.co.hexillium.rhul.compsoc.commands.SlashCommandHandler;

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
    public void onEvent(@Nonnull GenericEvent event) {
        if (event instanceof ReadyEvent){
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
        if (event instanceof GuildMessageReceivedEvent){
                dispatcher.dispatchCommand((GuildMessageReceivedEvent) event);
        } else if (event instanceof PrivateMessageReceivedEvent){
                dispatcher.dispatchCommand((PrivateMessageReceivedEvent) event);
        } else if (event instanceof ButtonInteraction){
                dispatcher.dispatchButtonPress((ButtonInteraction) event);
        } else if (event instanceof SlashCommandEvent){
            dispatcher.handleSlashCommand((SlashCommandEvent) event);
        }
    }
}
