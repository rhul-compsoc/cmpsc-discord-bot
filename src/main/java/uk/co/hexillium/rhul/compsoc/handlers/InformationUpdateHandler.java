package uk.co.hexillium.rhul.compsoc.handlers;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.channel.text.TextChannelCreateEvent;
import net.dv8tion.jda.api.events.channel.text.update.TextChannelUpdateNameEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.update.GuildMemberUpdateNicknameEvent;
import net.dv8tion.jda.api.events.user.update.GenericUserUpdateEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import uk.co.hexillium.rhul.compsoc.persistence.Database;

import java.util.List;

public class InformationUpdateHandler implements EventListener {

    Logger logger = LogManager.getLogger(InformationUpdateHandler.class);

    public InformationUpdateHandler(JDA jda){
        jda.addEventListener(this);
    }

    @Override
    public void onEvent(@NotNull GenericEvent genericEvent) {
        if (genericEvent instanceof GuildMemberUpdateNicknameEvent){
            update(((GuildMemberUpdateNicknameEvent) genericEvent).getMember());
        }
        if (genericEvent instanceof TextChannelUpdateNameEvent){
            updateChannelName(((TextChannelUpdateNameEvent) genericEvent).getChannel());
        }
        if (genericEvent instanceof TextChannelCreateEvent){
            insertChannel(((TextChannelCreateEvent) genericEvent).getChannel());
        }
        if (genericEvent instanceof GenericUserUpdateEvent){
            update(((GenericUserUpdateEvent<?>) genericEvent).getUser());
        }
        if (genericEvent instanceof GuildMemberJoinEvent){
            Database.EXPERIENCE_STORAGE.importMembers(((GuildMemberJoinEvent) genericEvent).getMember());
        }

    }

    public void ready(JDA jda){
        List<TextChannel> tcs = jda.getTextChannels();
        logger.info("Entering {} channels", tcs.size());
        Database.runLater(() -> {
            for (TextChannel tc : tcs)
                Database.MESSAGE_STORAGE.insertChannel(tc);
            logger.info("Finished inserting channels");
        });
    }

    private void updateChannelName(TextChannel tc) {
        Database.runLater(() -> {
            Database.MESSAGE_STORAGE.updateChannel(tc);
        });
    }

    private void insertChannel(TextChannel tc) {
        Database.runLater(() -> {
            Database.MESSAGE_STORAGE.insertChannel(tc);
        });
    }

    private void update(Member member){
        Database.runLater(() -> {
            Database.EXPERIENCE_STORAGE.updateMember(member);
        });
    }
    private void update(User user){
        Database.runLater(() -> {
            Database.EXPERIENCE_STORAGE.updateUser(user);
        });
    }
}
