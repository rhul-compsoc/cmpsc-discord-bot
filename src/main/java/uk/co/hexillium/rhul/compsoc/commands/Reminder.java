package uk.co.hexillium.rhul.compsoc.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.utils.TimeFormat;
import net.dv8tion.jda.api.utils.data.DataObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import uk.co.hexillium.rhul.compsoc.CommandDispatcher;
import uk.co.hexillium.rhul.compsoc.CommandEvent;
import uk.co.hexillium.rhul.compsoc.persistence.Database;
import uk.co.hexillium.rhul.compsoc.persistence.entities.Job;
import uk.co.hexillium.rhul.compsoc.persistence.entities.ReminderEntity;
import uk.co.hexillium.rhul.compsoc.time.TimeUtils;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A command to interact with the Reminder module
 *
 * Reminders are stored in the database until activated, when they are removed
 *
 * Reminders due in the next two hours will be pulled from the database, on a task every 30 minutes.
 */
public class Reminder extends Command {

    private static final Logger logger = LogManager.getLogger(Reminder.class);
    private static final String[] commands = new String[]{"timer"};

    JDA jda;

    private static final DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("E dd LLLL uu HH:mm:ss O"); // example: Wed 06 May 20 01:13:08 GMT


    private static final Pattern messagePattern = Pattern.compile(
            "((?:(?:[0-9]+)[ \\-.,]*?" +
                    "(?:" +
                    "mo|mnth|month|months" +
                    "|w|wk|wks|weeks" +
                    "|h|hrs|hours" +
                    "|d|day|days" +
                    "|m|min|mins|minutes" +
                    "|s|sec|secs|seconds)[ \\-.,]*)+) (.*?$)", Pattern.DOTALL
    );

    // this _is_ called, by the CommandManager
    public Reminder(){
        super("Reminder", "Makes a reminder, which will ping you and link your message when triggered.",
                "`timer <time string> <message ... ...>`\n" +
                        "The time string may use (__underscored__ characters can be used as abbreviations) __s__econds, __m__inutes, __h__ours, __d__ays, __w__eeks and __mo__nths, and come separated with spaces, hyphens or nothing.\n" +
                        "Examples: `1d2h`  `2d,1w 2s` `4seconds`", commands, "util");
        if (Database.JOB_STORAGE == null){
            logger.warn("Cannot start Reminders when database is not initialised.");
            return;
        }
    }

    private void incomingJob(DataObject object){
        handleSend(ReminderEntity.fromDataObject(object));
    }

    private void handleSend(ReminderEntity reminder){

        TextChannel tc = jda.getTextChannelById(reminder.getTargetChannel());
        boolean useUserChannel = false;
        if (tc == null){
            User user = jda.getUserById(reminder.getAuthor());
            if (user == null){
                return;
            }
            useUserChannel = true;
        }

        OffsetDateTime now = OffsetDateTime.now();
        EmbedBuilder embed = reminder.getAsEmbed();
        long delta = Duration.between(now, reminder.getEpochTarget()).abs().toMillis();
        if (now.isAfter(reminder.getEpochTarget())){
            embed.addField("Delta", humanReadableFormat(Duration.of(delta, ChronoUnit.MILLIS)) + " late", false);
        } else {
            embed.addField("Delta", humanReadableFormat(Duration.of(-delta, ChronoUnit.MILLIS)) + " early", false);
        }

        String message = "<@" + reminder.getAuthor() + "> ";
        if (useUserChannel){
            jda.openPrivateChannelById(reminder.getAuthor()).queue(pc -> {
                pc.sendMessageEmbeds(embed.build()).content(message).queue();
            });
        } else {
            tc.sendMessageEmbeds(embed.build()).content(message).queue();
        }
    }


    private static String humanReadableFormat(Duration duration) {
        long days = duration.toDays();
        long hrs = duration.toHours() - TimeUnit.DAYS.toHours(duration.toDays());
        long mins = duration.toMinutes() - TimeUnit.HOURS.toMinutes(duration.toHours());
        long secs = duration.getSeconds() - TimeUnit.MINUTES.toSeconds(duration.toMinutes());
        long millis = duration.getNano() / 1_000_000;
        StringBuilder strbld = new StringBuilder();
        int count = 0;
        if (millis > 0){
            strbld.insert(0, millis + " ms" + (count > 0 ? (count > 1 ? ", " : " and ") : ""));
            count++;
        }
        if (secs > 0){
            strbld.insert(0, secs + " secs" + (count > 0 ? (count > 1 ? ", " : " and ") : ""));
            count++;
        }
        if (mins > 0){
            strbld.insert(0, mins + " mins" + (count > 0 ? (count > 1 ? ", " : " and ") : ""));
            count++;
        }
        if (hrs > 0){
            strbld.insert(0, mins + " hours" + (count > 0 ? (count > 1 ? ", " : " and ") : ""));
            count++;
        }
        if (days > 0){
            strbld.insert(0, mins + " days" + (count > 0 ? (count > 1 ? ", " : " and ") : ""));
            count++;
        }
        return strbld.toString();
    }

    @Override
    public void onLoad(JDA jda, CommandDispatcher manager) {
        this.jda = jda;
        getScheduler().registerHandle("reminder", this::incomingJob);
    }

    @Override
    public String[] getCommands() {
        return commands;
    }


    @Override
    public void handleCommand(CommandEvent event) {
        if (Database.JOB_STORAGE == null){
            event.reply("Not currently available.");
            return;
        }
        if (event.getArgs().length == 0){
            event.reply("Specify a duration of time to wait, then your message.");
            return;
        }

        if (event.getFullArg().equalsIgnoreCase("debug")){
            event.reply(getScheduler().getDebugInfo());
            return;
        }

        Matcher m = messagePattern.matcher(event.getFullArg());
        if (!m.matches()){
            event.reply("Specify a duration of time to wait, then your message.");
            return;
        }
        OffsetDateTime target = TimeUtils.parseTarget(m.group(1));
        if (Duration.between(OffsetDateTime.now(), target).minus(10, ChronoUnit.SECONDS).isNegative()){
            event.reply("It's got to be more than ten seconds in the future...");
            return;
        }
        String diff = TimeUtils.getDeltaTime(OffsetDateTime.now(), target);
        String message = m.group(2);
        event.reactSuccess();
        event.reply("Okay, I'll remind you on " + TimeFormat.DATE_TIME_SHORT.format(target) // + Instant.ofEpochMilli(epoch).atOffset(ZoneOffset.UTC).format(dateFormat.withZone(ZoneId.of("UTC")))
               // + " (that's in " + diff + ").");
                + " (that's " + TimeFormat.RELATIVE.format(target) + "  -- " + diff + ").");

        ReminderEntity reminder = new ReminderEntity(event.getMessage().getJumpUrl(),
                event.getTextChannel().getIdLong(), event.getAuthor().getIdLong(), target, message);
        getScheduler().submitJob(new Job(-1, System.currentTimeMillis(), target.toEpochSecond() * 1000, "reminder", reminder.toDataObject()));
    }


    @Override
    public boolean requireGuild() {
        return true;
    }



}
