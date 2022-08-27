package uk.co.hexillium.rhul.compsoc.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.message.guild.react.GuildMessageReactionAddEvent;
import net.dv8tion.jda.api.events.message.priv.PrivateMessageReceivedEvent;
import net.dv8tion.jda.api.events.user.update.UserUpdateNameEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import uk.co.hexillium.rhul.compsoc.CommandDispatcher;
import uk.co.hexillium.rhul.compsoc.CommandEvent;
import uk.co.hexillium.rhul.compsoc.persistence.Database;
import uk.co.hexillium.rhul.compsoc.persistence.entities.DuplicateEntry;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

public class Register extends Command implements EventListener {

    private static final String[] COMMANDS = {"register", "submit", "verify"};
    private static final Pattern LOGIN_NAME_VERIFY = Pattern.compile("(?:[a-zA-Z]{4}\\d{3}|(?:[a-zA-Z]+\\.){2}\\d{4})@(?:live\\.)?rhul\\.ac\\.uk");
    private static final Pattern STUDENT_NUMBER_VERIFY = Pattern.compile("10\\d{7}");
    private static final long CHANNEL_ID = 768209780124418058L;
    private static final long VERIFIED_ROLE_ID = 768202524163047484L;
    private static final long SERVER_MEMBER_ROLE_ID = 620613291844173844L;
//    private static final long CHANNEL_ID = 751541795585785976L;
//    private static final long VERIFIED_ROLE_ID = 768265402593574953L;


    //todo add student and membership roles to people who left and rejoin
    //todo ping tapir for data on membership for completed verifications

    //todo https://compsocbot:password123@tapir.compsoc.dev/admin/compsocbot/
    //

    public Register() {
        super("Register", "Register your university identity", "`{{cmd_prefix}}testCommand`, idk really what it does."
                , COMMANDS, "identity");
//        this.requiredBotPermissions = new Permission[]{Permission.ADMINISTRATOR};
//        this.requiredUserPermissions = new Permission[]{Permission.ADMINISTRATOR};
    }

    static final Logger logger = LogManager.getLogger(Register.class);

    @Override
    public void handleCommand(CommandEvent event) {
        if (event.getChannel() instanceof GuildChannel){
            //send a dm
            boolean canSubmit = Database.STUDENT_VERIFICATION.canSubmitRequest(event.getAuthor().getIdLong());
            event.getUser().openPrivateChannel().queue(pc -> {
                if (canSubmit) {
                    pc.sendMessage(getYourPrivateData()).queue(succ -> {
                        event.getMessage().delete().queue();
                    }, failure -> {
                        event.sendEmbed("Failed to DM you.", "I need to be able to slide into your DMs to send you the command usage.", 0xff0000);
                    });
                } else {
                    pc.sendMessage(alreadyRegistered()).queue(succ -> {
                        event.getMessage().delete().queue();
                    }, failure -> {
                        event.sendEmbed("You're already registered.", "And I can't DM you. Sad times.", 0xff0000);
                    });
                }
            });
            return;
        }
        if (!(event.getChannel() instanceof PrivateChannel)){
            //??? what happened
            logger.error("I don't know what happened: " + event.getChannel().getClass().getName());
            return;
        }
        if (event.getArgs().length == 0){
            event.reply(getYourPrivateData());
            return;
        }
        if (event.getArgs().length != 2){
            event.sendEmbed("Error in formatting", "I was expecting a picture of your card and `!register aaaa999@live.rhul.ac.uk 100999999`\n\n" +
                    "Your email can also be `first.last.year@live.rhul.ac.uk`", 0xff0000);
            return;
        }
        if (event.getMessage().getAttachments().size() == 0
                || !event.getMessage().getAttachments().get(0).isImage()){
            event.sendEmbed("Please resend, and attach a picture of your studentID",
                    "To ensure verification please resend your verification message, and attach a picture of your student ID.  \nWe will not store your image.", 0xff0000);
            return;
        }
        if (event.getMessage().getAttachments().get(0).getSize() > 8 * 1000 * 1000){
            event.sendEmbed("Try another image", "That image is too large for us to process. There is an 8MB limit.", 0xff0000);
            return;
        }
        String loginName = event.getArgs()[0].toLowerCase().replaceAll("@rhul\\.ac\\.uk$", "@live.rhul.ac.uk"); //standardise the input;
        String studentID = event.getArgs()[1];
        if (!LOGIN_NAME_VERIFY.matcher(loginName).matches()){
            event.sendEmbed("I don't understand that email", "It should be in the format aaaa999@live.rhul.ac.uk or `first.last.year@live.rhul.ac.uk`", 0xff0000);
            return;
        }
        if (!STUDENT_NUMBER_VERIFY.matcher(studentID).matches()){
            event.sendEmbed("I don't understand that studentID", "It should be in the format 100999999", 0xff0000);
            return;
        }
        if (!Database.STUDENT_VERIFICATION.canSubmitRequest(event.getAuthor().getIdLong())){
            event.reply(alreadyRegistered());
            return;
        }

        //emergency debugging
        TextChannel channel = event.getJDA().getTextChannelById(CHANNEL_ID);
        if (channel == null){
            logger.error("Cannot find channel!");
            logger.info(event.getJDA().getTextChannelCache().asList());
            return;
        }

        //looks good

        Message.Attachment attachment = event.getMessage().getAttachments().get(0);
        attachment.retrieveInputStream().thenAccept(is -> {
            channel.sendMessage(theirPrivateData(event.getUser(), loginName, studentID, attachment.getFileExtension())).addFile(is, "card." + attachment.getFileExtension()).queue(m -> {
                Database.STUDENT_VERIFICATION.addStudent(studentID, loginName, System.currentTimeMillis(),
                        event.getUser().getIdLong(), m.getIdLong());
                event.getUser().openPrivateChannel().queue(c -> c.sendMessage(sentYourPrivateData()).queue(vrfy -> {
                    m.addReaction("✅").queue();
                    m.addReaction("❌").queue();
                }));
            });
        });


    }

    @Override
    public void onLoad(JDA jda, CommandDispatcher dispatcher) {
        logger.info("Test command loaded!");
        jda.addEventListener(this);
    }

    @Override
    public boolean requireGuild() {
        return false;
    }

    private MessageEmbed getYourPrivateData(){
        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("Register for CompSoc");
        embed.setDescription("Please send your RHUL email address, and your student number, with an image of your card attached.\n\nIt should look like this:\n`!register aaaa999@live.rhul.ac.uk 100999999`\n\n" +
                "Please contact a member of committee for any additional information.");
        embed.setImage("https://cdn.discordapp.com/attachments/647059719705329675/768537348816240650/2020-10-21_19-11-42.gif");
        embed.setColor(0xb000b0);
        return embed.build();
    }

    private MessageEmbed alreadyRegistered(){
        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("Request failed.");
        embed.setDescription("Looks like you either have an active request open or are already registered.\n\n" +
                "Contact committee if you believe this is an error.");
        embed.setColor(0xd00000);
        return embed.build();
    }

    private MessageEmbed sentYourPrivateData(){
        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("Your data has been sent for review");
        embed.setDescription("You'll get another message here when a verdict has been reached");
        embed.setColor(0xa0a000);
        return embed.build();
    }


    private MessageEmbed privateDataAccepted(){
        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("Your data has been accepted");
        embed.setDescription("You've been verified as a member.");
        embed.setColor(0x30a030);
        return embed.build();
    }


    private MessageEmbed privateDataDenied(){
        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("Your data has been denied.");
        embed.setDescription("Please review the information and submit another.");
        embed.setColor(0xa03030);
        return embed.build();
    }

    private MessageEmbed missingCommand(){
        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("I don't know what to do with this.");
        embed.setDescription("If this was as part of a verification attempt, please send the verification command with this image attached.");
        embed.setColor(0xa03030);
        return embed.build();
    }

    private MessageEmbed theirPrivateData(User user, String loginName, String studentID, String fileExtension){
        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("New verification attempt:");
        embed.addField(user.getAsTag(),
                  "Login Name: `" + loginName + "`\r\n" +
                        "Student ID: `" + studentID + "`\r\n" +
                          "Discord ID: `" + user.getIdLong() + "`\r\n" +
                          "Mention: " + user.getAsMention(), false);
        List<DuplicateEntry> entires = Database.STUDENT_VERIFICATION.getDuplicatesForID(studentID);
        if (entires.size() > 0){
            String ver = Arrays.toString(entires.stream().filter(e -> e.isVerified() && !e.isInvalidated()).map(DuplicateEntry::getSnowflake).toArray());
            String unver =  Arrays.toString(entires.stream().filter(e -> !e.isVerified() && e.isInvalidated()).map(DuplicateEntry::getSnowflake).toArray());
            String pending = Arrays.toString(entires.stream().filter(e -> !e.isVerified() && !e.isInvalidated()).map(DuplicateEntry::getSnowflake).toArray());

            embed.addField("⚠ This student ID has been seen before:",
                    (ver.equals("[]") ? "" : "Verified:" + ver + "\n\n") +
                            (unver.equals("[]") ? "" : "Unverified: " + unver + "\n\n") +
                            (pending.equals("[]") ? "" : "Pending: " + pending),
                    true);
        }
        entires = Database.STUDENT_VERIFICATION.getDuplicatesForName(loginName);
        if (entires.size() > 0){
            String ver = Arrays.toString(entires.stream().filter(e -> e.isVerified() && !e.isInvalidated()).map(DuplicateEntry::getSnowflake).toArray());
            String unver =  Arrays.toString(entires.stream().filter(e -> !e.isVerified() && e.isInvalidated()).map(DuplicateEntry::getSnowflake).toArray());
            String pending = Arrays.toString(entires.stream().filter(e -> !e.isVerified() && !e.isInvalidated()).map(DuplicateEntry::getSnowflake).toArray());

            embed.addField("⚠ This student Name has been seen before:",
                    (ver.equals("[]") ? "" : "Verified: " + ver + "\n\n") +
                            (unver.equals("[]") ? "" : "Unverified: " + unver + "\n\n") +
                            (pending.equals("[]") ? "" : "Pending: " + pending),
                    true);
        }
        embed.setImage("attachment://card." + fileExtension);
        embed.setDescription("React with a ✅ to allow the request, and ❌ to deny the request.");
        embed.setColor(0x4040d0);
        return embed.build();
    }

//    private void updateMessage(Message message){
//        EmbedBuilder embed = new EmbedBuilder();
//        VerificationMessage vMsg = Database.STUDENT_VERIFICATION.updateVerificationMessage(message.getIdLong());
//        if (vMsg.isStudentVerified()){
//            // v3erif
//        }
//        embed.addField(user.getAsTag(),
//                "Login Name: `" + loginName + "`\r\n" +
//                        "Student ID: `" + studentID + "`\r\n" +
//                        "Discord ID: `" + user.getIdLong() + "`\r\n" +
//                        "Mention: " + user.getAsMention(), false);
//
//    }

    @Override
    public void onEvent(@NotNull GenericEvent genericEvent) {
        if (genericEvent instanceof GuildMemberJoinEvent){
            GuildMemberJoinEvent event = (GuildMemberJoinEvent) genericEvent;
            if (event.getMember().getUser().isBot()){
                return;
            }
            Database.runLater(() -> {
                boolean verified = Database.STUDENT_VERIFICATION.isDiscordAccountValidated(event.getMember().getUser().getIdLong());
                if (verified){
                    event.getGuild().addRoleToMember(event.getMember(), event.getGuild().getRoleById(VERIFIED_ROLE_ID)).queue();
                    event.getGuild().addRoleToMember(event.getMember(), event.getGuild().getRoleById(SERVER_MEMBER_ROLE_ID)).queue();
                    logger.info("Previously verified user joined server, adding roles");
                }
            });
            return;
        }
        if (!(genericEvent instanceof GuildMessageReactionAddEvent)){
            if (genericEvent instanceof PrivateMessageReceivedEvent){
                PrivateMessageReceivedEvent event = (PrivateMessageReceivedEvent) genericEvent;
                if (event.getMessage().getAttachments().size() > 0 && event.getMessage().getContentRaw().isBlank()){
                    event.getAuthor().openPrivateChannel().queue(ch -> {
                        ch.sendMessage(missingCommand()).queue();
                    });
                    return;
                }
            }
            return;
        }
        GuildMessageReactionAddEvent event = (GuildMessageReactionAddEvent) genericEvent;
        if (event.getChannel().getIdLong() != CHANNEL_ID){
            return;
        }
        if (event.getUser().isBot()) return;

        long messageID = event.getMessageIdLong();
        if (event.getReactionEmote().getName().equals("✅")){
            Database.STUDENT_VERIFICATION.validateStudent(System.currentTimeMillis(), messageID, studentID -> {
                event.getGuild().addRoleToMember(studentID, event.getGuild().getRoleById(VERIFIED_ROLE_ID)).queue();
                event.getGuild().addRoleToMember(studentID, event.getGuild().getRoleById(SERVER_MEMBER_ROLE_ID)).queue();
                // also give them the server_member role
                event.getChannel().retrieveMessageById(messageID).queue(msg -> {
                    EmbedBuilder bld = new EmbedBuilder(msg.getEmbeds().get(0));
                    bld.setTitle("Approved Member");
                    bld.setDescription(null);
                    bld.setColor(0x00ff00);
                    bld.setFooter("Verified by: " + event.getUser().getAsTag());
                    bld.setImage(null);
                    msg.editMessage(bld.build()).override(true).queue();
                    msg.clearReactions().queue();
//                    msg.addReaction("↩").queue();
//                    msg.addReaction("🔄").queue();
                    msg.getJDA().openPrivateChannelById(studentID).queue(ch -> {
                        ch.sendMessage(privateDataAccepted()).queue();
                    });
                });
            });
        } else if (event.getReactionEmote().getName().equals("❌")){
            Database.STUDENT_VERIFICATION.invalidateStudent(System.currentTimeMillis(), messageID, studentID -> {
                event.getChannel().retrieveMessageById(messageID).queue(msg -> {
                    EmbedBuilder bld = new EmbedBuilder(msg.getEmbeds().get(0));
                    bld.setTitle("Denied Member");
                    bld.setDescription(null);
                    bld.setColor(0xff0000);
                    bld.setFooter("Denied by: " + event.getUser().getAsTag());
                    bld.setImage(null);
                    msg.editMessage(bld.build()).override(true).queue();
                    msg.clearReactions().queue();
//                    msg.addReaction("↩").queue();
//                    msg.addReaction("🔄").queue();
                    msg.getJDA().openPrivateChannelById(studentID).queue(ch -> {
                        ch.sendMessage(privateDataDenied()).queue();
                    });
                });
            });
        }
    }

    /*




     */
}
