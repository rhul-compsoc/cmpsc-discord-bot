package uk.co.hexillium.rhul.compsoc.commands;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import uk.co.hexillium.rhul.compsoc.CommandEvent;
import uk.co.hexillium.rhul.compsoc.Disabled;
import uk.co.hexillium.rhul.compsoc.constants.Regex;

import java.util.List;

@Disabled
public class UserInfo extends Command {

    private static final String[] COMMANDS = {"userdata", "userinfo"};

    public UserInfo() {
        super("UserInfo", "Get information about a user", "{{cmd_prefix}}userData <snowflake|mention|tag|messageLink>", COMMANDS, "admin");
    }

    @Override
    public void handleCommand(CommandEvent event) {
        if (event.getMember() == null) return;
        if (canRunCommand(event.getMember())){
            //run the command, but put the data in the commands channel
            boolean ping = false;
            if (event.getGuildMessageChannel().getIdLong() != 500621482901372928L){
                event.getMessage().delete().queue();
                ping = true;
            }
            GuildMessageChannel target = event.getGuild().getTextChannelById(500621482901372928L);
            if (target == null) target = event.getGuildMessageChannel(); // unlikely fallback

            String subject = event.getFullArg();
            long subjectId = extractID(subject);
            if (subjectId > 0L){
                //we have an ID
            } else if (Regex.USER_MENTION.matcher(subject).matches()){
                //we have found an ID
            } else if (Regex.USER_TAG.matcher(subject).matches()){
                //we have found a username
            } else if (Regex.JUMP_URL_PATTERN.matcher(subject).matches()){
                //we have found a message
            } else {
                List<Member> mList = event.getGuild().getMembersByEffectiveName(subject, true);
            }


        }
    }

    private long extractID(String input){
        try {
            return Long.parseLong(input);
        } catch (NumberFormatException ex){
            return -1;
        }
    }

    @Override
    public boolean requireGuild() {
        return true;
    }

    private boolean canRunCommand(Member member){
        return member.getRoles().contains(member.getGuild().getRoleById(1024355501124898867L));
    }
}
