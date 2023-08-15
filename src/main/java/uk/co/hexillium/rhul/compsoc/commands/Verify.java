package uk.co.hexillium.rhul.compsoc.commands;

import jakarta.mail.Address;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.UserContextInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.utils.TimeFormat;
import net.dv8tion.jda.api.utils.TimeUtil;
import uk.co.hexillium.rhul.compsoc.Bot;
import uk.co.hexillium.rhul.compsoc.commands.handlers.SlashCommandHandler;
import uk.co.hexillium.rhul.compsoc.commands.handlers.UserCommandHandler;
import uk.co.hexillium.rhul.compsoc.persistence.Database;
import uk.co.hexillium.rhul.compsoc.persistence.EmailVerification;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Verify implements SlashCommandHandler, UserCommandHandler {

    Pattern emailAddressVerifier = Pattern.compile("[a-z]{4}\\d{3,4}@live.rhul.ac.uk");
    String body = """
            <p>
            Hello, this email has been sent to you because {@discorduser} has requested a verification email.<br>
            If this was not you, please disregard this email in its entirety, and the code will expire with nothing being verified.<br>
            If you have had too many of these emails and you're not sure why, please reply to this email and I (the creator of this bot) will investigate.<br>
            </p>
            <p>
            Finally, if this was you, your code is {@code}<br>
            You can use this code with /verifycode to verify.
            </p>
            """;

    @Override
    public List<CommandData> registerGlobalCommands() {
        return List.of(
                Commands.slash("verifyemail", "Verify your email address to prove studentship")
                        .setDefaultPermissions(DefaultMemberPermissions.ENABLED)
                        .addOption(OptionType.STRING, "email_address", "Your RHUL email address, in zzzz000@live.rhul.ac.uk format", true, false),
                Commands.slash("verifycode", "Redeem your code to verify your email address")
                        .setDefaultPermissions(DefaultMemberPermissions.ENABLED)
                        .addOption(OptionType.STRING, "code", "The code sent in your email."),
                Commands.user("Verification Information")
                        .setDefaultPermissions(DefaultMemberPermissions.DISABLED)
        );
    }

    @Override
    public void handleSlashCommand(SlashCommandInteractionEvent event) {
        if (event.getName().equals("verifyemail")){
            if (event.getMember() == null) event.reply("Please use this command in the guild, it will still remain private.").queue();
            Duration lengthOfStay = Duration.between(OffsetDateTime.now(), event.getMember().getTimeJoined()).abs();
            if (lengthOfStay.toDays() < 28){
                event.reply("To prevent spam, there is currently a 28-day wait after joining before using this command")
                        .setEphemeral(true).queue();
                return;
            }

            String emailAddr = event.getOption("email_address", OptionMapping::getAsString);
            if (emailAddr == null){
                return;
            }
            emailAddr = emailAddr.toLowerCase();

            if (event.getMember().getRoles().stream().noneMatch(r -> r.getIdLong() == 1024355501124898867L)){
                event.reply("This command is currently limited").setEphemeral(true).queue();
                return;
            }

            if (Bot.mail == null){
                event.reply("Mail module is not currently running.  Please try again later.").setEphemeral(true).queue();
                return;
            }

            if (Database.EMAIL_VERIFICATION.getUserMostRecentSuccessfulVerification(event.getUser().getIdLong()) != null){
                event.reply("You are already verified.").setEphemeral(true).queue();
                return;
            }

            Matcher matcher = emailAddressVerifier.matcher(emailAddr);
            if (!matcher.matches()){
                event.getHook().sendMessage("Please ensure you have the email address in the correct RHUL format, " +
                        "containing your 4 letter code and 3 numbers, using `live.` in the domain.  (This check is run, but its result ignored for testing... proceeding anyway...")
                        .setEphemeral(true).queue();
//                return;
            }

            Address address;
            try {
                address = new InternetAddress(emailAddr, true);
            } catch (AddressException ex){
                event.reply("Your email address could not be parsed as an email address.\n" + ex.getMessage())
                        .setEphemeral(true).queue();
                return;
            }

            boolean isInUse = Database.EMAIL_VERIFICATION.isEmailAddressInUseByOtherPerson(emailAddr, event.getUser().getIdLong());

            if (isInUse){
                event.reply("Error: this email address is already in use.").setEphemeral(true).queue();
                return;
            }

            UUID uuid = UUID.randomUUID();
            String code = uuid.toString();
            byte[] bytes = asBytes(uuid);

            String toSend = body
                    .replace("{@discorduser}", event.getUser().getName())
                    .replace("{@code}", code);


                event.reply("Sending email...").setEphemeral(true).queue();
            String finalEmailAddr = emailAddr;
            Database.runLater(() -> {
                    try {
                        Bot.mail.sendMessage(address, "Verification Email for CompSocBot", toSend);
                        Database.EMAIL_VERIFICATION.registerEmailToSend(finalEmailAddr, OffsetDateTime.now().plusDays(2), bytes, event.getUser().getIdLong());
                        event.getHook().editOriginal("Email sent! Make sure to check your junk folder.").queue();
                    } catch (MessagingException e) {
                        event.getHook().editOriginal("Failed to send email.  Please contact `hexillium` for assistance").queue();
                    }
                });


        }
        if (event.getName().equals("verifycode")){

            String uuid = event.getOption("code", OptionMapping::getAsString);
            if (uuid == null) return;
            UUID code;
            try {
                code = UUID.fromString(uuid);
            } catch (IllegalArgumentException ex){
                event.reply("Code is in an invalid format").setEphemeral(true).queue();
                return;
            }
            byte[] bytes = asBytes(code);

            EmailVerification.VerificationResult result = Database.EMAIL_VERIFICATION.acceptToken(bytes, event.getUser().getIdLong());
            if (result.isError()){
                event.reply("An error with code " + result.toString()).setEphemeral(true).queue();
                return;
            } else {
                event.reply("Success! You've now been verified").setEphemeral(true).queue();
                //todo add some code here for things like roles
            }

        }
    }

    @Override
    public void handleUserContextCommand(UserContextInteractionEvent event) {
        if (event.getName().equals("Verification Information")){
            OffsetDateTime time = Database.EMAIL_VERIFICATION.getUserMostRecentSuccessfulVerification(event.getTarget().getIdLong());
            if (time == null) {
                event.reply("This user is not verified.").setEphemeral(true).queue();
            } else {
                event.reply("This user was verified at " + TimeUtil.getDateTimeString(time)
                        + " (" + TimeFormat.DATE_TIME_LONG.format(time) + ")").setEphemeral(true).queue();
            }
        }
    }
    @Override
    public void handleCommand(GenericInteractionCreateEvent event) {
        if (event instanceof SlashCommandInteractionEvent slash){
            handleSlashCommand(slash);
        } else if (event instanceof UserContextInteractionEvent user){
            handleUserContextCommand(user);
        }
    }

    public static UUID asUuid(byte[] bytes) {
        ByteBuffer bb = ByteBuffer.wrap(bytes);
        long firstLong = bb.getLong();
        long secondLong = bb.getLong();
        return new UUID(firstLong, secondLong);
    }

    public static byte[] asBytes(UUID uuid) {
        ByteBuffer bb = ByteBuffer.wrap(new byte[16]);
        bb.putLong(uuid.getMostSignificantBits());
        bb.putLong(uuid.getLeastSignificantBits());
        return bb.array();
    }

}
