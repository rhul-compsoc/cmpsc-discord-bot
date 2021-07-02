package uk.co.hexillium.rhul.compsoc;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.ReadyEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.events.message.priv.PrivateMessageReceivedEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.interactions.components.ButtonInteraction;

import javax.annotation.Nonnull;

public class EventManager implements EventListener{

    CommandDispatcher dispatcher;

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
        if (event instanceof GuildMessageReceivedEvent){
            if (dispatcher != null)
                dispatcher.dispatchCommand((GuildMessageReceivedEvent) event);
        }
        if (event instanceof PrivateMessageReceivedEvent){
            if (dispatcher != null)
                dispatcher.dispatchCommand((PrivateMessageReceivedEvent) event);
        }
        if (event instanceof ButtonInteraction){
            if (dispatcher != null)
                dispatcher.dispatchButtonPress((ButtonInteraction) event);
        }
    }
}
