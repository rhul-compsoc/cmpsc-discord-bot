package uk.co.hexillium.rhul.compsoc.persistence.entities;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.utils.data.DataObject;
import org.jetbrains.annotations.NotNull;

import java.time.OffsetDateTime;

public class ReminderEntity implements Comparable<ReminderEntity>{

    String messageJumpUrl;
    long channel_target;
    long author;
    OffsetDateTime target;
    String message;

    public static ReminderEntity fromDataObject(DataObject dataObject){
        return new ReminderEntity(
                dataObject.getString("messageJumpUrl"),
                dataObject.getLong("channel_target"),
                dataObject.getLong("author"),
                OffsetDateTime.parse(dataObject.getString("target")),
                dataObject.getString("message")
        );
    }

    public DataObject toDataObject(){
        DataObject object = DataObject.empty();
        object.put("messageJumpUrl", messageJumpUrl);
        object.put("channel_target", channel_target);
        object.put("author", author);
        object.put("target", target.toString());
        object.put("message", message);
        return object;
    }

    public ReminderEntity(String messageJumpUrl, long channel_target, long author, OffsetDateTime target, String message) {
        this.messageJumpUrl = messageJumpUrl;
        this.channel_target = channel_target;
        this.author = author;
        this.target = target;
        this.message = message;
    }

    public String getMessageJumpUrl() {
        return messageJumpUrl;
    }

    public long getTargetChannel() {
        return channel_target;
    }

    public long getAuthor() {
        return author;
    }

    public OffsetDateTime getEpochTarget() {
        return target;
    }

    public String getMessage() {
        return message;
    }

    public EmbedBuilder getAsEmbed(){
        EmbedBuilder builder = new EmbedBuilder();
        builder.setTitle("Reminder");
        builder.setDescription(message);
        builder.addField("Your message:", "[Jump!](" + messageJumpUrl + ")", false);
        return builder;
    }

    @Override
    public int compareTo(@NotNull ReminderEntity other) { //negative if _this_ comes before _o_
        return this.target.compareTo(other.target);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ReminderEntity entity = (ReminderEntity) o;

        if (channel_target != entity.channel_target) return false;
        if (author != entity.author) return false;
        if (target != entity.target) return false;
        return messageJumpUrl.equals(entity.messageJumpUrl);
    }

    @Override
    public int hashCode() {
        int result = messageJumpUrl.hashCode();
        result = 31 * result + (int) (channel_target ^ (channel_target >>> 32));
        result = 31 * result + (int) (author ^ (author >>> 32));
        result = 31 * result + (int) (target.hashCode());
        return result;
    }

    @Override
    public String toString() {
        return "{messageJumpUrl: \"" + messageJumpUrl + "\"" +
                ", channel_target: " + channel_target +
                ", author: " + author +
                ", epochTarget: " + target +
                ", message: \"" + message + "\"" +
                "}";
    }
}
