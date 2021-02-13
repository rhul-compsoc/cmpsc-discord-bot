package uk.co.hexillium.rhul.compsoc.handlers;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.update.GuildMemberUpdateNicknameEvent;
import net.dv8tion.jda.api.events.user.update.GenericUserUpdateEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import org.jetbrains.annotations.NotNull;
import uk.co.hexillium.rhul.compsoc.persistence.Database;

public class InformationUpdateHandler implements EventListener {

    public InformationUpdateHandler(JDA jda){
        jda.addEventListener(this);
    }

    @Override
    public void onEvent(@NotNull GenericEvent genericEvent) {
        if (genericEvent instanceof GuildMemberUpdateNicknameEvent){
            update(((GuildMemberUpdateNicknameEvent) genericEvent).getMember());
        }
        if (genericEvent instanceof GenericUserUpdateEvent){
            update(((GenericUserUpdateEvent) genericEvent).getUser());
        }
        if (genericEvent instanceof GuildMemberJoinEvent){
            Database.EXPERIENCE_STORAGE.importMembers(((GuildMemberJoinEvent) genericEvent).getMember());
        }
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
