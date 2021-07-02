package uk.co.hexillium.rhul.compsoc.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.events.message.guild.react.GuildMessageReactionAddEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.Button;
import net.dv8tion.jda.api.interactions.components.ButtonInteraction;
import net.dv8tion.jda.api.utils.AttachmentOption;
import net.dv8tion.jda.api.utils.TimeUtil;
import net.dv8tion.jda.internal.utils.PermissionUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import uk.co.hexillium.rhul.compsoc.CommandDispatcher;
import uk.co.hexillium.rhul.compsoc.CommandEvent;
import uk.co.hexillium.rhul.compsoc.crypto.HMAC;
import uk.co.hexillium.rhul.compsoc.persistence.Database;
import uk.co.hexillium.rhul.compsoc.persistence.entities.TriviaScore;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.QuadCurve2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Trivia extends Command implements EventListener, ButtonHandler {

    private static final Logger LOGGER = LogManager.getLogger(Trivia.class);

    private static final String[] commands = {"t", "triv", "trivia", "leaderboard", "lb"};
    private static final String[] buttonPrefix = {"c:tr"}; //command:trivia -- these will come in the form of c:tr|1/0<HMAC>
    private static final List<String> buttonHandles;
    static {
        buttonHandles = new ArrayList<>();
        Collections.addAll(buttonHandles, buttonPrefix);
    }

    private HMAC hmac;

    private static final long channelID = 766050353174544384L;
    private long recentSentMessageID = -1L;
    private long recentMessageSpawnID = -1L;
    private long cooldown = 90_000; // the current question's timer - default: 1.5 mins
    private static final long minCooldown = 30_000; // the minimum coolown - used for quickly solved puzzles, and the base 0.5 min
    private static final long cooldownMult = 40_000; // 40 sec || 0.66667 mins
    private final ReentrantLock lock = new ReentrantLock(false);

    private static final Pattern truthyRegex = Pattern.compile("(1|true|t|yes)", Pattern.CASE_INSENSITIVE);
    private static final Pattern falsyRegex = Pattern.compile("(0|false|f|no)", Pattern.CASE_INSENSITIVE);

    private static final String[] trueReactions = {"✅"};
    private static final List<String> trueReacts;
    private static final String[] falseReactions = {"❌"};
    private static final List<String> falseReacts;

    static {
        trueReacts = new ArrayList<String>();
        Collections.addAll(trueReacts, trueReactions);
        falseReacts = new ArrayList<String>();
        Collections.addAll(falseReacts, falseReactions);
    }


    boolean canConnect(Member member, VoiceChannel vc) {
        boolean hasPerm = PermissionUtil.checkPermission(vc, member, Permission.VOICE_CONNECT);
        return hasPerm && (vc.getUserLimit() == 0 || PermissionUtil.checkPermission(vc, member, Permission.MANAGE_CHANNEL) || vc.getUserLimit() < vc.getMembers().size());
    }

    private Question question;

    private Question lastQuestion;
    private boolean lastQuestionSolved = false;
    private final Object lastQuestionSolverLock = new Object();

    public Trivia() {
        super("Trivia", "Answer questions to score points", "Answer questions to score points", commands, "fun");
    }

    @Override
    public void onLoad(JDA jda, CommandDispatcher manager) {
        jda.addEventListener(this);
    }

    private void askAlgebra(Question newQ, TextChannel tc) {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        try {
            ImageIO.write(newQ.getImage(), "png", os);
        } catch (IOException ex) {
            LOGGER.error("Failed to send image", ex);
            return;
        }
        tc.sendMessageEmbeds(askQuestion(newQ)).setActionRows(getBooleanActionRow()).addFile(os.toByteArray(), "image.png").queue(msg -> {
            lock.lock();
            try {
                makeSpace(tc);
                this.question = newQ;
                this.cooldown = minCooldown + (newQ.getValue() * cooldownMult);
                this.recentSentMessageID = msg.getIdLong();
            } finally {
                lock.unlock();
            }
        });
    }

    private ActionRow getBooleanActionRow(){
        // "a" -> answer, "t" -> true, "f" -> false;
        String trStr = (buttonPrefix[0] + "|" + "at");
        String faStr = (buttonPrefix[0] + "|" + "af");
        String trueSign = trStr + HMAC.toBase4096String(hmac.sign(trStr.getBytes(StandardCharsets.UTF_8), 0, channelID));
        String falseSign = faStr + HMAC.toBase4096String(hmac.sign(faStr.getBytes(StandardCharsets.UTF_8), 0, channelID));
        return ActionRow.of(Button.success(trueSign, "TRUE"), Button.danger(falseSign, "FALSE"));
    }

    @Override
    public void initButtonHandle(HMAC hmac, JDA jda) {
        this.hmac = hmac;
    }

    @Override
    public void handleButtonInteraction(ButtonInteraction interaction, String data, boolean userLockedHMAC) {
        char type = data.charAt(0);
        //leave cases for things like pagination and such here.
        switch (type){
            case 'a':
            // a for answer
                 if (recentSentMessageID != interaction.getMessageIdLong()){
                            interaction.reply("Error: answer button tagged on non-recent message. Please report this error.").setEphemeral(true).queue();
                            interaction.editComponents(Collections.emptyList()).queue();
                            return;
                        }
                 answerQuestion(data.substring(1, 2),  //either 't' or 'f'
                         interaction.getUser(), interaction.getTextChannel(),
                         err -> interaction.reply(err).setEphemeral(true).queue());
                 break;
        }

    }

    @Override
    public List<String> registerHandles() {
        return buttonHandles;
    }

    @Override
    public void onEvent(@NotNull GenericEvent genericEvent) {
        if (genericEvent instanceof GuildMessageReceivedEvent) {
            if (question != null && Duration.between(OffsetDateTime.now(), TimeUtil.getTimeCreated(recentMessageSpawnID)).abs().toMillis() < cooldown) {
                // it hasn't been long enough yet.
                return;
            }
            if (question == null && Duration.between(OffsetDateTime.now(), TimeUtil.getTimeCreated(recentMessageSpawnID)).abs().toMillis() < minCooldown) {
                // the current question _has_ been answered, but it hasn't been long enough to spawn a new one
                return;
            }
            GuildMessageReceivedEvent event = (GuildMessageReceivedEvent) genericEvent;
            if (event.getChannel().getIdLong() == channelID)
                return; //don't spawn from things happening in the channel - it's just annoying.
            if (event.getChannel().getIdLong() == 848237918471454720L)
                return; //don't spawn from things happening in the logs channel - it's just annoying.
            // we can spawn one in!
            // let's have a 2/15 chance of that happening
            if (randInclusive(1, 15) > 2) return;
            TextChannel target = event.getJDA().getTextChannelById(channelID);
            if (target == null) {
                LOGGER.error("Failed to find textchannel. Aborting");
                return;
            }
            Database.runLater(() -> {
                recentMessageSpawnID = event.getMessageIdLong();
                Question question = genQuestion();
                askAlgebra(question, target);
            });
        } else if (genericEvent instanceof GuildMessageReactionAddEvent) {
            if (question == null) return;
            if (question.getAnswer() instanceof Boolean) {
                GuildMessageReactionAddEvent event = (GuildMessageReactionAddEvent) genericEvent;
                if (event.getChannel().getIdLong() != channelID) return;
                if (recentMessageSpawnID != event.getMessageIdLong()) return;
                event.getReaction().removeReaction(event.getUser()).queue();
                if (!trueReacts.contains(event.getReactionEmote().getAsReactionCode()) &&
                        !falseReacts.contains(event.getReactionEmote().getAsReactionCode())) {
                    return;
                }
                lock.lock();
                try {
                    boolean correct = (Boolean) question.getAnswer();
                    boolean isRight = trueReacts.contains(event.getReactionEmote().getAsReactionCode()) == correct;
                    Database.TRIVIA_STORAGE.updateMemberScore(event.getUser().getIdLong(), isRight ? question.getValue() : -1 * question.getValue());
                    updateMessage(question, isRight, event.getUser(), recentSentMessageID, event.getChannel());
                    this.question = null;
                } finally {
                    lock.unlock();
                }
            }
        }
    }

    private void makeSpace(TextChannel channel) {
        if (question == null) return;
        channel.editMessageEmbedsById(recentSentMessageID, nobodyIsHere()).setActionRows(Collections.emptyList()).queue();
        question = null;
    }

    @Override
    public void handleCommand(CommandEvent event) {
        if (event.getTextChannel().getIdLong() != channelID && event.getAuthor().getIdLong() != 187979032904728576L) {
            event.reply("This isn't the right channel :/");
            return;
        }

        if (event.getCommand().equalsIgnoreCase("leaderboard") || event.getCommand().equalsIgnoreCase("lb")) {
            int position = 0;
            if (event.getArgs().length == 1) {
                try {
                    position = Integer.parseInt(event.getArgs()[0]) - 1;
                } catch (NumberFormatException ex) {
                }
            }

            int finalPosition1 = position;
            Database.runLater(() -> { //don't run on the WS thread
                //leaderboards
                int maxPos = Database.TRIVIA_STORAGE.fetchTotalDatabaseMembers() / 10;
                int finalPosition = Math.max(0, Math.min(maxPos, finalPosition1));
                List<TriviaScore> scores = Database.TRIVIA_STORAGE.fetchLeaderboard(finalPosition);
                TriviaScore userScore = Database.TRIVIA_STORAGE.fetchUserScore(event.getAuthor().getIdLong());
                event.reply(getScores(userScore, scores, finalPosition, (Database.TRIVIA_STORAGE.fetchTotalDatabaseMembers() / 10) + 1));
            });
            return;
        }
        if (event.getFullArg().isBlank() || event.getArgs()[0].equalsIgnoreCase("help")) {
            EmbedBuilder builder = new EmbedBuilder();
            builder.setTitle("Trivia/Boolean Algebra help");
            builder.setDescription("Answer questions that pop up in chat to score points.  The more difficult the question, the more points you'll earn.\n\n" +
                    "Answering the question incorrectly will deduct that many points - so don't guess (it also spoils it slightly for other people).\n\n" +
                    "If you get a question wrong, you can use `!t solve` for the bot to work it out, and show you how to solve it.\n\n" +
                    "You can find out who is doing well using the `!leaderboard` command.  You can look at specific pages using `!leaderboard <pagenum>`.  " +
                    "[You can see the Scoreboard here, too.](https://passport.cmpsc.uk/)");
            builder.addField("The Algebra Symbols:",
                    Arrays.stream(BooleanOP.values()).map(op -> op.name() + " `" + op.symbol + "`").collect(Collectors.joining("\n"))
                            + "\nNOT `¬`"
                    , false);
            event.reply(builder.build());
            return;
        }

        if (event.getArgs()[0].equalsIgnoreCase("solve")) {
            if (lastQuestion == null) {
                event.reply("Cannot find inactive question to solve");
                return;
            }
            synchronized (lastQuestionSolverLock) {
                if (lastQuestionSolved) {
                    event.reply("I've already solved this");
                    return;
                }
                lastQuestionSolved = true;
                Database.runLater(() -> {
                    BooleanAlgebra prev = lastQuestion.getBalg();
                    prev.findSolution();
                    prev.markSolution();
                    ByteArrayOutputStream os = new ByteArrayOutputStream();
                    try {
                        ImageIO.write(prev.genImage(true), "png", os);
                    } catch (IOException ex) {
                        LOGGER.error("Failed to send image", ex);
                        return;
                    }
                    event.getTextChannel().sendMessage("Here's the solution, with " + prev.solution.getOperationCount() + " steps : ")
                            .addFile(os.toByteArray(), "solution.png", AttachmentOption.SPOILER)
                            .queue();
                });
                return;
            }
        }

        if (event.getAuthor().getIdLong() == 187979032904728576L) {
            if (event.getFullArg().equalsIgnoreCase("debug")) {
                event.reply(String.format("recentSentMessageID: %d\n" +
                        "recentMessageSpawnID: %d\n" +
                        "cooldown: %d\n" +
                        "minCooldown: %d\n" +
                        "cooldownMult: %d\n" +
                        "Lock State: %s\n", recentSentMessageID, recentMessageSpawnID, cooldown, minCooldown, cooldownMult, lock.toString()));
            } else if (event.getArgs().length == 4) {
                if (event.getArgs()[0].equalsIgnoreCase("gen")) {
                    String type = event.getArgs()[1];
                    int depth = Integer.parseInt(event.getArgs()[2]);
                    int hardness = Integer.parseInt(event.getArgs()[3]);

                    BooleanAlgebra balg;
                    if (type.equalsIgnoreCase("bool")) {
                        balg = new BooleanAlgebra(depth, hardness, 0);
                    } else {
                        balg = new BooleanAlgebra(depth, hardness, 0, ThreadLocalRandom.current().nextBoolean());
                    }
                    LOGGER.info("Manually spawned a new boolean \"" + type + "\" problem with depth: " + depth + ", hardness:" + hardness + " operations: " + balg.getOperations() + "   and scores " + getScoreForBooleanAlgebra(balg));
                    Database.runLater(() -> { //don't run on WS thread
                        if (balg.getOperations() > 2000) {
                            event.reply("This is far too long to generate :( op:" + balg.getOperations());
                            return;
                        }
                        String quest = balg.toString();
                        if (quest.length() > 2048) {
                            quest = balg.toCompactString();
                        }
                        int score = getScoreForBooleanAlgebra(balg);
                        if (quest.length() < 2048) {
                            event.reply("Generating problem... (may take a while to create the image)\n"
                                    + "Op count: " + balg.getOperations() + ", scores: " + score);
                        } else {
                            event.reply("Generated message was too long.");
                            return;
                        }
                        try {
                            balg.findSolution();
                            balg.markSolution();
                            Question newQ = new Question(quest, balg.getValue(), 0, balg.genImage(true), balg);
                            //Question newQ = new Question(quest, balg.getValue(), score, balg.genImage(false));
                            recentMessageSpawnID = event.getMessageIdLong();
                            askAlgebra(newQ, event.getTextChannel());
                        } catch (Exception ex) {
                            LOGGER.error("Failed to generate problem ", ex);
                        }
//
                    });
                    return;

                }
            }
        }
        if (question == null) {
            event.reply("There is no active question.");
        }
        //try to give them benefit of the doubt
        if (question.getAnswer() instanceof Boolean) {
            String answer = event.getFullArg().toLowerCase(); // case isn't important here.
            boolean isTrue = truthyRegex.matcher(answer).matches();
            boolean isFalse = falsyRegex.matcher(answer).matches();
            if (!isTrue && !isFalse) {
//                error.accept("That was not a valid answer.");
                event.reply("That was not a valid answer.");
                return;
            }
            answerQuestion(answer, event.getUser(), event.getTextChannel(), event::reply);
            event.getMessage().delete().queue();;
        }
    }

    private synchronized void answerQuestion(String answer, User user, TextChannel channel, Consumer<String> error) {

        lock.lock();
        //we're going into question-got-answered mode
        try {
            if (this.question == null) {
                error.accept("You got beaten to it. Very close, because the only thing that saved this from duplicating was the locking mechanism.");
//                event.reply("You got beaten to it. Very close, because the only thing that saved this from duplicating was the locking mechanism.");
            } else {
                this.lastQuestion = question;
                this.lastQuestionSolved = false;
                boolean correct = (Boolean) question.getAnswer();
                boolean isRight = testAnswer(answer, correct);
                Database.TRIVIA_STORAGE.updateMemberScore(user.getIdLong(), isRight ? question.getValue() : -1 * question.getValue());
                updateMessage(question, isRight, user, recentSentMessageID, channel);
                this.question = null;
            }
        } finally {
            lock.unlock();
        }
    }

    private boolean testAnswer(String answer, boolean check) {
        return check ? truthyRegex.matcher(answer).matches() : falsyRegex.matcher(answer).matches();
    }

    private MessageEmbed nobodyIsHere() {
        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle("You missed it ):");
        eb.setDescription(question.getQuery().length() < 2048 ? question.getQuery() : "Description too long ):");
        eb.addField(question.getValue() + " points went lost.", "The correct answer is ||`" + question.getAnswer() + "`||", false);
        eb.setColor(0x505050);
        return eb.build();
    }

    private void updateMessage(Question question, boolean correct, User user, long messageID, TextChannel textChannel) {
        EmbedBuilder builder = new EmbedBuilder();
        builder.setColor(correct ? 0x20e035 : 0xe02035);
        builder.setTitle((correct ? "Correct! " : "Incorrect! "));
//        builder.setDescription(user.getAsMention() + "\n\n" + question.getQuery() + "\n\n" + "The correct answer is: `" + question.getAnswer() + "`");
        builder.setDescription(question.getQuery());
        String timeStr = " completed it in " + humanReadableFormat(Duration.between(Instant.now(), TimeUtil.getTimeCreated(messageID)).abs());
        builder.addField(String.format("You got %s%d point(s).", correct ? "" : "-", question.value), user.getAsMention() + timeStr + " - the correct answer is " +
                "||`" + question.getAnswer() + "`||", false);
        builder.setImage("attachment://image.png");
//        builder.setAuthor(user.getAsTag());
        textChannel.editMessageEmbedsById(messageID, builder.build()).setActionRows(Collections.emptyList())/*.override(true)*/.queue();
    }


    private MessageEmbed askQuestion(Question question) {
        EmbedBuilder builder = new EmbedBuilder();
        builder.setColor(0x3040c0);
        builder.setTitle("Question!");
        builder.setDescription(question.getQuery());
        builder.setFooter("You can score " + question.getValue() + " point(s) by running `!t <answer>` and getting the answer correct.");
        builder.setImage("attachment://image.png");
        return builder.build();
    }

    private static String humanReadableFormat(Duration duration) {
        long days = duration.toDays();
        long hrs = duration.toHours() - TimeUnit.DAYS.toHours(duration.toDays());
        long mins = duration.toMinutes() - TimeUnit.HOURS.toMinutes(duration.toHours());
        long secs = duration.getSeconds() - TimeUnit.MINUTES.toSeconds(duration.toMinutes());
        long millis = duration.getNano() / 1_000_000;
        StringBuilder strbld = new StringBuilder();
        int count = 0;
        if (millis > 0) {
            strbld.insert(0, millis + " ms" + (count > 0 ? (count > 1 ? ", " : " and ") : ""));
            count++;
        }
        if (secs > 0) {
            strbld.insert(0, secs + " secs" + (count > 0 ? (count > 1 ? ", " : " and ") : ""));
            count++;
        }
        if (mins > 0) {
            strbld.insert(0, mins + " mins" + (count > 0 ? (count > 1 ? ", " : " and ") : ""));
            count++;
        }
        if (hrs > 0) {
            strbld.insert(0, hrs + " hours" + (count > 0 ? (count > 1 ? ", " : " and ") : ""));
            count++;
        }
        if (days > 0) {
            strbld.insert(0, days + " days" + (count > 0 ? (count > 1 ? ", " : " and ") : ""));
            count++;
        }
        return strbld.toString();
    }

    private MessageEmbed getScores(TriviaScore selfScore, List<TriviaScore> scores, int page, int maxPages) {
        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("Leaderboard");
        StringBuilder strbld = new StringBuilder();
        strbld.append("Run `!leaderboard <pagenum>` to view the scores for that page");
        strbld.append("\n");
        strbld.append("You can also use the CompSoc passport to see the scores, [here](https://passport.cmpsc.uk/).");
        strbld.append("```\n");
        strbld.append("Rank | Score | Username ");
        strbld.append("\n");
        strbld.append("-------------------------------");
        strbld.append("\n");
        boolean seenSelf = selfScore == null;
        if (!seenSelf && selfScore.getPosition() <= scores.get(0).getPosition()) {
            if (!scores.contains(selfScore)) {
                strbld.append(String.format("% 4d | % 5d | You (%s)", selfScore.getPosition(), selfScore.getScore(), selfScore.getUserAsTag()));
                strbld.append("\n");
                strbld.append("-------------------------------");
                strbld.append("\n");
                seenSelf = true;
            }
        }
        for (TriviaScore tv : scores) {
            if (!seenSelf && tv.getMemberId() == selfScore.getMemberId()) {
                strbld.append(String.format("% 4d | % 5d | You (%s)", tv.getPosition(), tv.getScore(), tv.getUserAsTag()));
                strbld.append("\n");
                seenSelf = true;
            } else {
                strbld.append(String.format("% 4d | % 5d | %s", tv.getPosition(), tv.getScore(), tv.getUserAsTag()));
                strbld.append("\n");
            }
        }
        if (!seenSelf) {
            strbld.append("-------------------------------");
            strbld.append("\n");
            strbld.append(String.format("% 4d | % 5d | You (%s)", selfScore.getPosition(), selfScore.getScore(), selfScore.getUserAsTag()));
            strbld.append("\n");
        }
        strbld.append("```");
        embed.setDescription(strbld.toString());
        embed.setFooter("Page " + (page + 1) + "/" + maxPages);
        return embed.build();
    }

    private Question genQuestion() {
        //pick what type we are going for:
        return getBoolAlgebra();
//        int type = randInclusive(0, 4);
//        switch (type){
//            case 0: //boolean algebra
//                return getBoolAlgebra();
//            case 1: //maths question
//                return getMathsQuestion();
//            case 2: //multi-round maths question
//                return getMultiRoundMaths();
//            case 3: //base conversion
//                return getBaseQuestion();
//            case 4: //hash request
//                return getHashQuestion();
//        }
    }

    private Question getBoolAlgebra() {
        boolean invalid = true;
        BooleanAlgebra balg = null;
        int difficulty = 0, hardness = 0;
        while (invalid) {
            difficulty = randInclusive(1, 5);
            difficulty = randInclusive(1, difficulty);
            hardness = randInclusive(3, 7);
            hardness = randInclusive(2, hardness);
            balg = new BooleanAlgebra(difficulty, hardness, 0, ThreadLocalRandom.current().nextBoolean());
            if (balg.toString().length() < 2048 || balg.toCompactString().length() < 2048) invalid = false;
        }
        LOGGER.info("Automatically spawned a new boolean problem with depth: " + difficulty + ", hardness:" + hardness + " and scores " + getScoreForBooleanAlgebra(balg));
        return new Question(balg.toString().length() < 2048 ? balg.toString() : balg.toCompactString(), balg.getValue(), getScoreForBooleanAlgebra(balg), balg.genImage(false), balg);
    }

    private int getScoreForBooleanAlgebra(BooleanAlgebra balg) {
        return Math.max(1, (int) Math.sqrt(balg.getOperations() / 1.3) - 1);
    }

//    private Question getMathsQuestion(){
//
//    }
//
//    private Question getMultiRoundMaths(){
//
//    }
//
//    private Question getBaseQuestion(){
//
//    }
//
//    private Question getHashQuestion(){
//
//    }

    /*
    Numvember

    logic questions/ boolean algebra: (true and false) xor false
    simple maths questions: 458*71 (+-/* sqrt() pow() )
    BIDMAS questions

    Hash this string
    b64 this string

    number conversions: 65 -> binary

     */


    static int randInclusive(int lower, int upper) {
        return lower + ThreadLocalRandom.current().nextInt((upper - lower) + 1);
    }

}

class Solution {
    final int operationCount;
    final boolean value;
    final boolean shortCut;
    int shortCutIndex;

    public Solution(int operationCount, boolean value, boolean shortCut, int shortCutIndex) {
        this.operationCount = operationCount;
        this.value = value;
        this.shortCut = shortCut;
        this.shortCutIndex = shortCutIndex;
    }

    public int getOperationCount() {
        return operationCount;
    }

    public boolean getValue() {
        return value;
    }
}

class BooleanAlgebra {


    //    BooleanAlgebra left;
//    BooleanAlgebra right;
    BooleanAlgebra[] nodes;
    Solution solution = null;
    BooleanOP stage;

    boolean value;

    boolean notted;

    BooleanAlgebra(int depth, int hardness, int currentLevel) {
        this.notted = ThreadLocalRandom.current().nextInt(10) >= 9;
        if (depth < 0) {
            this.value = ThreadLocalRandom.current().nextBoolean();
            return;
        }
//        this.left = new BooleanAlgebra(depth - (ThreadLocalRandom.current().nextInt(2) +1));
//        this.right = new BooleanAlgebra(depth - (ThreadLocalRandom.current().nextInt(2) +1));
        int x = ThreadLocalRandom.current().nextInt(2, Math.max(3, hardness + 2));
        nodes = new BooleanAlgebra[x];
        for (int i = 0; i < x; i++) {
            nodes[i] = new BooleanAlgebra(depth - (ThreadLocalRandom.current().nextInt(Math.max(2, hardness / 2)) + 1),
                    hardness - ThreadLocalRandom.current().nextInt(0, 2), currentLevel + 1);
        }
        if (depth > 3 && currentLevel < 2) {
            stage = BooleanOP.XOR;
        } else {
            stage = BooleanOP.getRand();
        }
    }

    BooleanAlgebra(int depth, int hardness, int currentLevel, boolean requiredValue) {

        // pick if we not this or not
        notted = ThreadLocalRandom.current().nextInt(10) >= 8; //80% I think. Maybe not.

        this.value = requiredValue ^ notted;
        if (depth < 0) {
            return;
        }
        //first, let's determine what type of gate we want
        if (depth > 4 && currentLevel < 2) {
            stage = BooleanOP.XOR;
        } else {
            stage = BooleanOP.getRand();
        }
        //determine how many children this node should have
        int x = ThreadLocalRandom.current().nextInt(2, Math.max(3, hardness + 2));
        nodes = new BooleanAlgebra[x];

        //generate all except the first term, depending on the type of gate
        if (stage == BooleanOP.AND || stage == BooleanOP.OR) {
            for (int i = 1; i < x; i++) {
                nodes[i] = new BooleanAlgebra(depth - (ThreadLocalRandom.current().nextInt(Math.max(2, hardness / 2)) + 1),
                        hardness - ThreadLocalRandom.current().nextInt(0, 2), currentLevel + 1, stage == BooleanOP.AND);
            }
        } else {
            for (int i = 1; i < x; i++) {
                nodes[i] = new BooleanAlgebra(depth - (ThreadLocalRandom.current().nextInt(Math.max(2, hardness / 2)) + 1),
                        hardness - ThreadLocalRandom.current().nextInt(0, 2), currentLevel + 1, i % 2 == 0);
            }
        }

        if (stage == BooleanOP.AND || stage == BooleanOP.OR) {
            // generate the first element, this will be the deciding factor.
            nodes[0] = new BooleanAlgebra(depth - (ThreadLocalRandom.current().nextInt(Math.max(2, hardness / 2)) + 1),
                    hardness - ThreadLocalRandom.current().nextInt(0, 2), currentLevel + 1, requiredValue ^ notted);
        } else {
            // generate the first element, this will be the deciding factor.
            nodes[0] = new BooleanAlgebra(depth - (ThreadLocalRandom.current().nextInt(Math.max(2, hardness / 2)) + 1),
                    hardness - ThreadLocalRandom.current().nextInt(0, 2), currentLevel + 1, (requiredValue ^ x % 2 == 0) ^ notted);
        }


        //put it somewhere randomly in this thing...
        int swapIndex = ThreadLocalRandom.current().nextInt(x);
        BooleanAlgebra temp = nodes[swapIndex];
        nodes[swapIndex] = nodes[0];
        nodes[0] = temp;
    }

    public void markSolution() {
        markSolution(null, 0);
    }

    private void markSolution(BooleanAlgebra parent, int childIndex) {
        //the presence of the solution in the parent implies it is needed
        if (this.nodes == null) {
            if (parent == null) { //if this is the top-level, then leave it alone
                return;
            }
            if (parent.solution == null) {
                this.solution = null;
                return;
            }
            int shortCut = parent.solution.shortCutIndex;
            boolean markThis = shortCut < 0 || shortCut == childIndex;
            if (!markThis) this.solution = null;
            return;
        }
        if (parent != null && parent.solution == null) {
            this.solution = null;
            BooleanAlgebra[] booleanAlgebras = this.nodes;
            for (int i = 0; i < booleanAlgebras.length; i++) {
                booleanAlgebras[i].markSolution(this, i);
            }
            return;
        }
        if (parent != null) {
            int shortCut = parent.solution.shortCutIndex;
            boolean markThis = shortCut < 0 || shortCut == childIndex;
            if (!markThis) this.solution = null;
        }
        for (int i = 0; i < this.nodes.length; i++) {
            nodes[i].markSolution(this, i);
        }
    }

    public void findSolution() {
        this.solve();
    }

    private Solution solve() {
        if (nodes == null || nodes.length == 0) {
            Solution s = new Solution((this.notted ? 2 : 1), this.value ^ this.notted, false, -1);
            this.solution = s;
            return s;
        }
        List<Solution> solveChildren = new ArrayList<>(nodes.length);
        for (int i = 0; i < nodes.length; i++) {
            BooleanAlgebra balg = nodes[i];
            solveChildren.add(balg.solve());
        }
        int operations = 0;
        boolean value = this.getValue();
        int shortCutIndex = -1;
        switch (this.stage) {
            case OR:
                if (value ^ this.notted) { //then we need to find a single true
                    boolean seen = false;
                    int best = 0;
                    int index = 0, bestIndex = 0;
                    for (int i = 0; i < solveChildren.size(); i++) {
                        Solution solveChild = solveChildren.get(i);
                        if (solveChild.getValue()) {
                            int i1 = solveChild.getOperationCount();
                            if (!seen || i1 < best) {
                                seen = true;
                                best = i1;
                                bestIndex = index;
                            }
                        }
                        index++;
                    }
                    operations = seen ? best + 1 : 0;
                    shortCutIndex = bestIndex;
                } else {
                    operations = solveChildren.stream().map(Solution::getOperationCount).mapToInt(i -> i).sum() + solveChildren.size();
                }
                break;
            case AND:
                if (!value ^ this.notted) { //need to find one false
                    boolean seen = false;
                    int best = 0;
                    int index = 0, bestIndex = 0;
                    for (int i = 0; i < solveChildren.size(); i++) {
                        Solution solution = solveChildren.get(i);
                        if (!solution.getValue()) {
                            int i1 = solution.getOperationCount();
                            if (!seen || i1 < best) {
                                seen = true;
                                best = i1;
                                bestIndex = index;
                            }
                        }
                        index++;
                    }
                    operations = seen ? best + 1 : 0;
                    shortCutIndex = bestIndex;
                } else {
                    operations = solveChildren.stream().map(Solution::getOperationCount).mapToInt(i -> i).sum() + solveChildren.size();
                }
                break;
            case XOR:
                operations = solveChildren.stream().map(Solution::getOperationCount).mapToInt(i -> i).sum() + solveChildren.size();
        }
        Solution s = new Solution(operations, value, shortCutIndex >= 0, shortCutIndex);
        this.solution = s;
        return s;
    }

    public BufferedImage genImage(boolean drawSolution) {
        if (nodes == null) {
            BufferedImage bim = new BufferedImage(100, 25, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = (Graphics2D) bim.getGraphics();
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, bim.getWidth(), bim.getHeight());
            if (drawSolution) {
                if (this.solution == null) {
                    graphics.setColor(new Color(175, 175, 175));
                } else {
                    graphics.setColor(this.solution.getValue() ? new Color(40, 170, 40) : new Color(170, 40, 40));
                }
            } else {
                graphics.setColor(Color.BLACK);
            }
            Font f = new Font("Courier New", Font.PLAIN, 15);
            graphics.setFont(f);
            String str = String.valueOf(value).toUpperCase();
            Rectangle2D layout = graphics.getFontMetrics(f).getStringBounds(str, graphics);
            int y = (int) (0 + ((bim.getHeight() - layout.getHeight()) / 2) + graphics.getFontMetrics(f).getAscent());
            graphics.drawString(str, 0, y);
            if (notted) {
                graphics.drawLine(0, y - graphics.getFontMetrics(f).getAscent() + 1, (int) layout.getWidth(), y - graphics.getFontMetrics(f).getAscent() + 1);
            }

            graphics.drawLine((int) layout.getWidth(), 12, 100, 12);

//            graphics.setColor(Color.RED);
//            graphics.drawRect(0, 0, 99, 24);
            graphics.dispose();
            return bim;
        }
        List<BufferedImage> images = new ArrayList<>();
        for (BooleanAlgebra balg : nodes) {
            images.add(balg.genImage(drawSolution));
        }
        int totalHeight = images.stream().map(BufferedImage::getHeight).mapToInt(Integer::intValue).sum();
        int maxWidth = images.stream().map(BufferedImage::getWidth).mapToInt(Integer::intValue).max().orElse(0);
        int width = totalHeight;
        BufferedImage bim = new BufferedImage(maxWidth + width, totalHeight, BufferedImage.TYPE_INT_ARGB);
        width = (int) (0.8 * width);
        Graphics2D g2 = (Graphics2D) bim.getGraphics();
        g2.setColor(Color.WHITE);
        g2.fillRect(0, 0, bim.getWidth(), bim.getHeight());
        g2.setColor(Color.BLACK);
        int yOffset = 0;
        int num = 0;
        for (BufferedImage imgs : images) {
            g2.setColor(Color.BLACK);
            g2.drawImage(imgs, maxWidth - imgs.getWidth(), yOffset, null);
            if (yOffset > 0) {
//                g2.drawLine(0, yOffset, imgs.getWidth(), yOffset);
            }
            g2.setColor(num == 0 ? new Color(50, 0, 0, 50) : new Color(0, 0, 50, 50));
            num = num == 0 ? 1 : 0;
            //g2.fillRect(maxWidth - imgs.getWidth(), yOffset, imgs.getWidth(), imgs.getHeight());
            g2.clearRect(0, yOffset, (maxWidth - imgs.getWidth()), imgs.getHeight());
            yOffset += imgs.getHeight();
        }
        if (drawSolution) {
            if (this.solution == null) {
                g2.setColor(new Color(175, 175, 175));
            } else {
                g2.setColor(this.solution.getValue() ? new Color(40, 170, 40) : new Color(170, 40, 40));
            }
        } else {
            g2.setColor(Color.BLACK);
        }
        if (drawSolution) {
            if (this.solution == null) {
                g2.setStroke(new BasicStroke(width < 100 ? 1 : width / 200f));
            } else {
                g2.setStroke(new BasicStroke(width < 100 ? 1 : width / 50f));
            }
        } else {
            g2.setStroke(new BasicStroke(width < 100 ? 1 : width / 100f));
        }
        stage.drawOp(g2, maxWidth, width, 0 + 2, totalHeight - 2);
//        g2.setColor(Color.BLACK);
        g2.drawLine(maxWidth + width, (totalHeight / 2) + 1, bim.getWidth(), (totalHeight / 2) + 1);
        if (notted) {
            int notSize = (int) (2 * Math.max(5d, width / 10d));
            g2.fillOval(maxWidth + width, totalHeight / 2 - (notSize / 2), notSize, notSize);
            g2.setColor(Color.WHITE);
            g2.fillOval(maxWidth + (width + 1), (totalHeight / 2) - (notSize / 2) + 1, (notSize) - 2, (notSize) - 2);
        }
        g2.dispose();
        return bim;
    }

    int getOperations() {
        int operations = 0;
        if (nodes != null) {
            operations += nodes.length;
            for (BooleanAlgebra b : nodes) {
                operations += b.getOperations();
            }
        }
        return operations;
    }


    boolean getValue() {
        if (nodes == null) {
            return notted ^ value; // false won't change the value, and true will always flip it, so use XOR
        }
        boolean[] bools = new boolean[nodes.length];
        for (int i = 0; i < nodes.length; i++) {
            bools[i] = nodes[i].getValue();
        }
        return notted ^ stage.run(bools);
    }

    @Override
    public String toString() {
        if (nodes == null) {
            return (notted ? "¬" : "") + String.valueOf(value).toUpperCase();
        } else {
            StringBuilder strbld = new StringBuilder();
            if (notted) strbld.append("¬");
            strbld.append("(");
            strbld.append(nodes[0].toString());
            for (int i = 1; i < nodes.length; i++) {
                strbld.append(" ").append(stage.symbol).append(" ");
                strbld.append(nodes[i].toString());
            }
            strbld.append(")");
            return strbld.toString();
        }
    }

    public String toCompactString() {
        if (nodes == null) {
            return (notted ? "¬" : "") + String.valueOf(value).toUpperCase().charAt(0);
        } else {
            StringBuilder strbld = new StringBuilder();
            if (notted) strbld.append("¬");
            strbld.append("(");
            strbld.append(nodes[0].toCompactString());
            for (int i = 1; i < nodes.length; i++) {
                strbld.append(stage.symbol);
                strbld.append(nodes[i].toCompactString());
            }
            strbld.append(")");
            return strbld.toString();
        }
    }
}

enum BooleanOP {
    AND("∧", (a, b) -> a & b) {
        @Override
        void drawOp(Graphics2D g2, int startX, int widthX, int startY, int heightY) {
//            g2.setStroke(new BasicStroke(widthX < 100 ? 1 : widthX / 100f));

//            g2.setColor(Color.BLACK);
            //vertical line for the and
            g2.drawLine(startX, startY, startX, startY + heightY - 1);
            //extensions
            g2.drawLine(startX, startY, startX + widthX / 3, startY);
            g2.drawLine(startX, startY + heightY - 1, startX + widthX / 3, startY + heightY - 1);
            //curved end
            g2.drawArc((startX - (2 * widthX / 3)), startY, (2 * widthX) - (widthX / 3), heightY - 1, 270, 180);

            g2.setStroke(new BasicStroke(1));


//            int centrex = (int) (startX + (0.5 * widthX));
//            int centrey = (int) (startY + (0.5 * heightY));
//            g2.setFont(g2.getFont().deriveFont(widthX / 10f));
//            g2.drawString("AND", centrex, centrey);
        }
    },
    OR("∨", (a, b) -> a | b) {
        @Override
        void drawOp(Graphics2D g2, int startX, int widthX, int startY, int heightY) {
//            g2.setStroke(new BasicStroke(widthX < 100 ? 1 : widthX / 100f));

            QuadCurve2D curve = new QuadCurve2D.Double(startX, startY, startX + (2 * widthX / 3d), startY + (heightY / 10d), startX + widthX - 1, startY + heightY / 2d);
            g2.draw(curve);
            curve = new QuadCurve2D.Double(startX, startY + heightY, startX + (2 * widthX / 3d), (startY + heightY) - (heightY / 10d), startX + widthX - 1, (startY + heightY) - heightY / 2d);
            g2.draw(curve);
            curve = new QuadCurve2D.Double(startX, startY, startX + widthX / 2.5d, startY + (heightY / 2d), startX, (startY + heightY));
            g2.draw(curve);
            g2.setStroke(new BasicStroke(1));


//            int centrex = (int) (startX + (0.5 * widthX));
//            int centrey = (int) (startY + (0.5 * heightY));
//            g2.setFont(g2.getFont().deriveFont(widthX / 10f));
//            g2.drawString("OR", centrex, centrey);
        }
    },
    XOR("⊕", (a, b) -> a ^ b) {
        @Override
        void drawOp(Graphics2D g2, int startX, int widthX, int startY, int heightY) {
//            g2.setStroke(new BasicStroke(widthX < 100 ? 1 : widthX / 100f));

            QuadCurve2D curve = new QuadCurve2D.Double(startX + (widthX / 10d), startY, startX + 5 + (2 * widthX / 3d), startY + (heightY / 10d), startX + widthX - 1, startY + heightY / 2d);
            g2.draw(curve);
            curve = new QuadCurve2D.Double(startX + (widthX / 10d), startY + heightY, startX + (2 * widthX / 3d), (startY + heightY) - (heightY / 10d), startX + widthX - 1, (startY + heightY) - heightY / 2d);
            g2.draw(curve);
            curve = new QuadCurve2D.Double(startX + (widthX / 10d), startY, startX + (widthX / 10d) + widthX / 2.5d, startY + (heightY / 2d), startX + (widthX / 10d), (startY + heightY));
            g2.draw(curve);

            //the extra line that an OR doesn't have
            curve = new QuadCurve2D.Double(startX, startY, startX + widthX / 2.5d, startY + (heightY / 2d), startX, (startY + heightY));
            g2.draw(curve);
            g2.setStroke(new BasicStroke(1));
//
//            int centrex = (int) (startX + (0.5 * widthX));
//            int centrey = (int) (startY + (0.5 * heightY));
//            g2.setFont(g2.getFont().deriveFont(widthX / 10f));
//            g2.drawString("XOR", centrex, centrey);
        }
    },
//    IMPLIES("->", (a, b) -> !a | b)
    ;
    String symbol;
    BooleanStep func;

    BooleanOP(String symbol, BooleanStep func) {
        this.symbol = symbol;
        this.func = func;
    }

    static BooleanOP getRand() {
        return values()[ThreadLocalRandom.current().nextInt(values().length)];
    }

    boolean run(boolean[] bools) {
        boolean a = bools[0];
        for (int i = 1; i < bools.length; i++) {
            a = func.run(a, bools[i]);
        }
        return a;
    }

    abstract void drawOp(Graphics2D g2, int startX, int widthX, int startY, int heightY);
}


@FunctionalInterface
interface BooleanStep {
    boolean run(boolean a, boolean b);
}

class Question {
    String query;
    Object answer;
    BufferedImage image;
    int value;

    BooleanAlgebra balg;

    public Question(String query, Object answer, int value, BufferedImage img, BooleanAlgebra balg) {
        this.balg = balg;
        this.query = query;
        this.answer = answer;
        this.value = value;
        this.image = img;
    }

    public BooleanAlgebra getBalg() {
        return balg;
    }

    public String getQuery() {
        return query;
    }

    public Object getAnswer() {
        return answer;
    }

    public int getValue() {
        return value;
    }

    public void setImage(BufferedImage image) {
        this.image = image;
    }

    public BufferedImage getImage() {
        return image;
    }
}
