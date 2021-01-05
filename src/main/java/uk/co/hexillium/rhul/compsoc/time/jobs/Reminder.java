package uk.co.hexillium.rhul.compsoc.time.jobs;

import net.dv8tion.jda.api.JDA;
import uk.co.hexillium.rhul.compsoc.persistence.Database;
import uk.co.hexillium.rhul.compsoc.time.Job;

public class Reminder extends Job {

    public static final int REMINDER_JOB_TYPE = 1;

    long entityId;
    long guildId;
    long channelId;
//    long messageId;     //<<--- maybe
    long delta;
    long epochTarget;
    String message;
    String link;
    String mentionString;

    public Reminder(long entityId, long guildId, long channelId, long epochTarget, String message,
                    String link, String mentionString) {
        super(entityId, 0, epochTarget, REMINDER_JOB_TYPE);
        this.entityId = entityId;
        this.guildId = guildId;
        this.channelId = channelId;
        this.epochTarget = epochTarget;
        this.message = message;
        this.link = link;
        this.mentionString = mentionString;
    }

    public long getGuildId() {
        return guildId;
    }

    public long getChannelId() {
        return channelId;
    }

    public long getDelta() {
        return delta;
    }

    public String getMessage() {
        return message;
    }

    public String getLink() {
        return link;
    }

    public String getMentionString() {
        return mentionString;
    }

    @Override
    public void run(JDA jda, Database database) {

    }


}
