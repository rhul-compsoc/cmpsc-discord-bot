package uk.co.hexillium.rhul.compsoc;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.concrete.PrivateChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import uk.co.hexillium.rhul.compsoc.persistence.entities.GuildSettings;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

public class CommandEvent {

    GuildMessageChannel textChannel;
    PrivateChannel privateChannel;

    MessageReceivedEvent messageReceivedEvent;

    Message message;

    String fullText;
    String command;

    String[] args;
    String fullArg;

    GuildSettings settings;

    boolean isGuildMessage;

    public CommandEvent(MessageReceivedEvent event) {
        isGuildMessage = event.isFromGuild();
        this.messageReceivedEvent = event;
        if (!isGuildMessage){
            privateChannel = event.getChannel().asPrivateChannel();
        } else {
            textChannel = event.getChannel().asGuildMessageChannel();
        }
        common(event.getMessage().getContentRaw(), CommandDispatcher.defaultCommandDelimiter);
        this.message = event.getMessage();
    }

    /**
     * Common splits between guild and private messages
     * @param message The message that is common between them
     */
    private void common(String message, String prefix) {
        String[] a = message.split("\\s+");
        command = a[0].substring(prefix.length());
        args = new String[a.length - 1];
        System.arraycopy(a, 1, args, 0, args.length);
        fullText = message;
        String[] chunks = fullText.split("\\s", 2);
        fullArg = chunks.length == 1 ? "" : chunks[1];
    }


    /**
     * Get the JDA instance
     *
     * @return the JDA instance associated with this bot.
     */
    @Nonnull
    public JDA getJDA() {
        return message.getJDA();
    }

    /**
     * Gets the text channel associated with this event.  Will throw iff run from a DM.
     *
     * @return the TextChannel from which this command was invoked
     * @throws IllegalStateException iff this command was not run from a guild
     */
    @Nonnull
    public GuildMessageChannel getGuildMessageChannel() {
        if (!isGuildMessage()) throw new IllegalStateException("Not a guild channel.");
        return textChannel;
    }

    /**
     * Gets the private channel associated with this event.  Will throw iff run from a guild.
     *
     * @return the PrivateChannel from which this command was run
     * @throws IllegalStateException iff the command was run from a guild
     */
    @Nonnull
    public PrivateChannel getPrivateChannel() {
        if (isGuildMessage()) throw new IllegalStateException("Not a private channel.");
        return privateChannel;
    }

    /**
     * Get the {@link MessageChannel} this was sent in.
     *
     * @return the text or private channel, depending on the context.
     */
    @Nonnull
    public MessageChannel getMessageChannel() {
        return isGuildMessage ? textChannel : privateChannel;
    }

    /**
     * Gets the raw MessageReceivedEvent.
     *
     * @return the raw GuildMessageReceivedEvent.
     */
    public MessageReceivedEvent getEvent(){
        return messageReceivedEvent;
    }

    /**
     * Gets the Message object from which this command was invoked (ie, the user input)
     *
     * @return the Message object
     */
    @Nonnull
    public Message getMessage() {
        return message;
    }

    /**
     * Gets the full String content of the message.
     *
     * @return the full command String, including the prefix and command.
     */
    @Nonnull
    public String getFullText() {
        return fullText;
    }

    /**
     * Gets the full String content of the argument.
     *
     * @return the argument String, (not including the prefix and command).
     */
    @Nonnull
    public String getFullArg() {
        return fullArg;
    }

    /**
     * Gets the arguments provided to this command. May be empty if no arguments specified.
     * Does not include the prefix or command.
     *
     * @return the array of arguments
     */
    @Nonnull
    public String[] getArgs() {
        return args;
    }

    /**
     * Gets the command slug used for this command.
     * Returns only the command - no prefix or arguments.
     *
     * @return the string used to run this command.
     */
    @Nonnull
    public String getCommand() {
        return command;
    }

    /**
     * Returns true if this command was invoked from a guild
     *
     * @return true if from a guild; else false.
     */
    public boolean isGuildMessage() {
        return isGuildMessage;
    }

    /**
     * Gets the {@link Guild} from which this command was run.
     * Can be null if not run from a guild
     *
     * @return the guild this event was run from, null if not run from a guild
     */
    @Nonnull
    public Guild getGuild() {
        if (!isGuildMessage()) throw new IllegalStateException("Not run from a guild");
        return messageReceivedEvent.getGuild();
    }

    /**
     * Gets the {@link User} who ran this command
     *
     * @return The user who ran this command.
     */
    @Nonnull
    public User getUser() {
        return messageReceivedEvent.getAuthor();
    }

    /**
     * Gets the {@link User} who ran this command.
     * The same as {@link CommandEvent#getUser()}
     *
     * @return the user who ran this command.
     */
    @Nonnull
    public User getAuthor() {
        return getUser();
    }

    /**
     * Gets the {@link Member} who ran this command.
     * Can be null if not run from a {@link Guild}
     *
     * @return the member who ran this command; null if not from a guild.
     */
    @Nullable
    public Member getMember() {
        return messageReceivedEvent.getMember();
    }

    /**
     * Gets the {@link MessageChannel} that the command was sent in
     *
     * @return Non-null message channel
     */
    @Nonnull
    public MessageChannel getChannel() {
        return isGuildMessage ? textChannel : privateChannel;
    }

    /**
     * Gets the bot as a member of the guild from which this command was run
     * @return the Bot, as a Member.
     * @throws IllegalStateException iff the event is not a guild event
     */
    @Nonnull
    public Member getSelfMember(){
        if (!isGuildMessage()) throw new IllegalStateException("Not a guild event.");
        return messageReceivedEvent.getGuild().getSelfMember();
    }

    /**
     * Return the settings for this guild.  This will give information on prefix values, as well as roleIDs.
     * @return the guild settings.  Null iff this was called from a privatemessage.
     */
    @Nullable
    public GuildSettings getGuildSettings(){
        return settings;
    }


    /**
     * Sends a Message to the origin channel, as an Embed.
     * @param title The title of the embed
     * @param description the description of the embed
     * @param colour the colour of the embed
     * @param fields varargs (optional) of any additional fields in the embed.
     */
    public void sendEmbed(String title, String description, int colour, MessageEmbed.Field... fields){
        EmbedBuilder eb = new EmbedBuilder();
        eb.setDescription(description);
        eb.setTitle(title);
        eb.setColor(colour);
        for (MessageEmbed.Field f : fields){
            eb.addField(f);
        }
        this.getChannel().sendMessageEmbeds(eb.build()).queue();
    }

    /**
     * Sends a {@link MessageEmbed} reply to the channel
     *
     * @param embed the embed to send
     */
    public void reply(@Nonnull MessageEmbed embed) {
        getChannel().sendMessageEmbeds(embed).queue();
    }

    /**
     * Sends a String reply to the channel
     *
     * @param message the message to send
     */
    public void reply(@Nonnull String message) {
        sendSplitMessage(getChannel(), message, null, null);
    }

    /**
     * Sends a String reply to the channel, with a success consumer.
     * This method will split the message up if it is too long
     *
     * @param message the string to send
     * @param success the success consumer, which will run when all messages completed
     */
    public void reply(@Nonnull String message, Consumer<List<? super Message>> success) {
        sendSplitMessage(getChannel(), message, success, null);
    }

    /**
     * Sends a String reply to the channel, with a success consumer.
     * This method will <b>not</b> split the message up if it is too long
     *
     * @param message the string to send
     * @param success the success consumer, which will run when all messages completed
     */
    public void replySingle(@Nonnull String message, Consumer<? super Message> success) {
        sendSingleMessage(getChannel(), message, success, null);
    }

    /**
     * Adds a reaction to the origin message.  Can be in unicode format,
     * or {@literal <a?:.+:\d+>} format, or {@literal .+:\d+}.
     *
     * @param reaction the code or the reaction to send.
     */
    public void react(@Nullable String reaction) {
        if (reaction == null || reaction.isEmpty())
            return;
        getMessage().addReaction(Emoji.fromFormatted(reaction)).queue();
    }

    /**
     * Sends a String reply to the channel, with a success and failure consumer.
     *
     * @param channel the channel to which the message is sent
     * @param message the string to send
     * @param success the success consumer, which will run when the message send is completed
     * @param failure the failure consumer, which will be called if the message fails to send.
     */
    public void sendSingleMessage(MessageChannel channel, String message, Consumer<? super Message> success, Consumer<? super Throwable> failure) {
        channel.sendMessage(message).queue(success, failure);
    }

    /**
     * Sends a String reply to the channel, with a success and failure consumer.
     * This method will split the message into 2k char chunks.
     *
     * @param channel the channel to which the message is sent
     * @param text    the string split then send to send
     * @param success the success consumer, which will run when all of the messages are sent
     * @param failure the failure consumer, which will be called if the message fails to send.
     */
    public void sendSplitMessage(MessageChannel channel, String text, Consumer<List<? super Message>> success, Consumer<? super Throwable> failure) {
        ArrayList<String> splitMessage = splitMessage(text);
        List<Message> messages = new ArrayList<Message>();
        for (int i = 0; i < splitMessage.size(); i++) {
            final int messageno = i;
            channel.sendMessage(splitMessage.get(i)).queue(sent -> {
                if (success == null) return;
                messages.add(sent);
                if (messageno == splitMessage.size() - 1) success.accept(messages);
            }, failure);
        }
    }

    /**
     * Reacts with the tick to the origin message
     */
    public void reactSuccess() {
        react(message, 0);
    }

    /**
     * Reacts with a question mark to the origin message
     */
    public void reactWarning() {
        react(message, 1);
    }

    /**
     * Reacts with an X to the origin message
     */
    public void reactFailure() {
        react(message, 2);
    }

    private void react(Message message, int reactionID) {
        message.addReaction(Emoji.fromUnicode(Bot.emojis[reactionID])).queue();
    }

    @Override
    public String toString() {
        return getUser().getName() + (getUser().getDiscriminator().equals("0000") ? "#" + getUser().getDiscriminator() : "") + " issued command `" + fullText + "` at " + HelperUtil.getDateAndTime() + ".";
    }

    /**
     * Get the origin message ID
     *
     * @return the origin message ID, as a long
     */
    public long getMessageIdLong() {
        return message.getIdLong();
    }

    private static ArrayList<String> splitMessage(String stringtoSend) {
        ArrayList<String> msgs = new ArrayList<>();
        if (stringtoSend != null) {
            stringtoSend = sanitiseMentions(stringtoSend);
            while (stringtoSend.length() > 2000) {
                int leeway = 2000 - (stringtoSend.length() % 2000);
                int index = stringtoSend.lastIndexOf("\n", 2000);
                if (index < leeway)
                    index = stringtoSend.lastIndexOf(" ", 2000);
                if (index < leeway)
                    index = 2000;
                String temp = stringtoSend.substring(0, index).trim();
                if (!temp.equals(""))
                    msgs.add(temp);
                stringtoSend = stringtoSend.substring(index).trim();
            }
            if (!stringtoSend.equals(""))
                msgs.add(stringtoSend);
        }
        return msgs;
    }

    public static String sanitiseMentions(String message){
        return message.replace("@everyone", "@\u0435veryone").replace("@here", "@h\u0435re").trim();
    }


}
