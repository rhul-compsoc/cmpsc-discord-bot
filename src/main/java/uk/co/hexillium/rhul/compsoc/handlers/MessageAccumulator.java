package uk.co.hexillium.rhul.compsoc.handlers;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageDeleteEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageUpdateEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import org.jetbrains.annotations.NotNull;
import uk.co.hexillium.rhul.compsoc.persistence.Database;

public class MessageAccumulator implements EventListener {

    public MessageAccumulator(JDA jda){
        jda.addEventListener(this);
    }

    private void messageCreated(GuildMessageReceivedEvent event){
        Database.MESSAGE_STORAGE.insertMessage(event.getMessage());
    }

    private void messageUpdated(GuildMessageUpdateEvent event){
        Database.MESSAGE_STORAGE.insertMessage(event.getMessage());
    }

    private void messageDeleted(GuildMessageDeleteEvent event){
        Database.MESSAGE_STORAGE.deleteMessage(event.getMessageIdLong());
    }

    @Override
    public void onEvent(@NotNull GenericEvent event) {
        if (event instanceof GuildMessageReceivedEvent){
            messageCreated((GuildMessageReceivedEvent) event);
        } else if (event instanceof GuildMessageUpdateEvent){
            messageUpdated((GuildMessageUpdateEvent) event);
        } else if (event instanceof GuildMessageDeleteEvent){
            messageDeleted((GuildMessageDeleteEvent) event);
        }
    }
}
