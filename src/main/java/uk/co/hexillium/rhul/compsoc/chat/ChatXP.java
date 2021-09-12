package uk.co.hexillium.rhul.compsoc.chat;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import uk.co.hexillium.rhul.compsoc.persistence.Database;
import uk.co.hexillium.rhul.compsoc.persistence.entities.MemberXPData;

import java.math.BigInteger;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

public class ChatXP implements EventListener {

    private JDA jda;
    private Random random;

    private static final Logger logger = LogManager.getLogger(ChatXP.class);

    private static final long COOLDOWN_TIME = 1000 * 60 * 2; //2 minutes

    public ChatXP(JDA jda){
        this.jda = jda;
        jda.addEventListener(this);
        random = ThreadLocalRandom.current();
    }

    @Override
    public void onEvent(@NotNull GenericEvent genericEvent) {
        if (!(genericEvent instanceof GuildMessageReceivedEvent)) return;
        GuildMessageReceivedEvent event = (GuildMessageReceivedEvent) genericEvent;

        if (event.getMember() == null) return;
        if (event.getAuthor().isBot()) return;
        Database.runLater(() -> {
            MemberXPData data = Database.EXPERIENCE_STORAGE.getMemberXP(event.getGuild().getIdLong(), event.getAuthor().getIdLong());
            if (data == null){
                Database.EXPERIENCE_STORAGE.newMemberXP(event.getAuthor().getIdLong(), event.getGuild().getIdLong(), generateRandomXP(), System.currentTimeMillis());
                return;
            }
            long mostRecent = data.getRecentMessage();
            if (System.currentTimeMillis() < mostRecent + COOLDOWN_TIME){ // they've sent a message too soon to be calculated
                Database.EXPERIENCE_STORAGE.updateUserMessages(event.getAuthor().getIdLong(), event.getGuild().getIdLong());
                return;
            }
            long currentXP = data.getXpTotal();
            int gain = generateRandomXP();

            if (getLevelFromXP(currentXP + gain) > getLevelFromXP(currentXP)){
//                event.getMessage().addReaction("\u2B06\uFE0F").queue();  //⬆️
            }
            Database.EXPERIENCE_STORAGE.updateUserXP(event.getAuthor().getIdLong(), event.getGuild().getIdLong(), gain, System.currentTimeMillis());
        });
    }

    private int generateRandomXP(){
        return random.nextInt(10) + 10;
    }

    private int getLevelFromXP(long xp){
        xp *= 2;
        xp += 225;
        BigInteger epp = new BigInteger(String.valueOf(xp), 10);
        return (int) Math.floor((epp.sqrt().longValue() - 15) / 10d);
    }
}
