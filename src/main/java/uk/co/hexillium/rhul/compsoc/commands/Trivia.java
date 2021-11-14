package uk.co.hexillium.rhul.compsoc.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.events.message.guild.react.GuildMessageReactionAddEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.Button;
import net.dv8tion.jda.api.interactions.components.ButtonStyle;
import net.dv8tion.jda.api.utils.AttachmentOption;
import net.dv8tion.jda.api.utils.TimeUtil;
import net.dv8tion.jda.internal.utils.PermissionUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import uk.co.hexillium.rhul.compsoc.CommandDispatcher;
import uk.co.hexillium.rhul.compsoc.CommandEvent;
import uk.co.hexillium.rhul.compsoc.commands.challenges.*;
import uk.co.hexillium.rhul.compsoc.persistence.Database;
import uk.co.hexillium.rhul.compsoc.persistence.entities.TriviaScore;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Trivia extends Command implements EventListener, ComponentInteractionHandler, SlashCommandHandler {

    private static final Logger LOGGER = LogManager.getLogger(Trivia.class);

    private static final String[] commands = {"t", "triv", "trivia", "leaderboard", "lb", "solve"};
    private static final String[] buttonPrefix = {"c:tr"}; //command:trivia -- these will come in the form of c:tr|1/0<HMAC>
    private static final List<String> buttonHandles;

    static {
        buttonHandles = new ArrayList<>();
        Collections.addAll(buttonHandles, buttonPrefix);
    }

//    private HMAC hmac;

    private static final long channelID = 766050353174544384L;
    private long recentSentMessageID = -1L;
    private long recentMessageSpawnID = -1L;
    private long cooldown = 90_000; // the current question's timer - default: 1.5 mins
    private static final long minCooldown = 30_000; // the minimum coolown - used for quickly solved puzzles, and the base 0.5 min
    private static final long cooldownMult = 40_000; // 40 sec || 0.66667 mins
    private final ReentrantLock lock = new ReentrantLock(false);

    private Challenge currentQuestion;

    private Challenge lastQuestion;
    private boolean lastQuestionSolved = false;
    private final Object lastQuestionSolverLock = new Object();

    public Trivia() {
        super("Trivia", "Answer questions to score points", "Answer questions to score points", commands, "fun");
    }

    @Override
    public void onLoad(JDA jda, CommandDispatcher manager) {
        jda.addEventListener(this);
    }

    private void postChallenge(Challenge newQ, TextChannel tc) {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        try {
            ImageIO.write(newQ.getImage(), "png", os);
        } catch (IOException ex) {
            LOGGER.error("Failed to send image", ex);
            return;
        }
        tc.sendMessageEmbeds(askQuestion(newQ)).setActionRows(getBooleanActionRow(newQ)).addFile(os.toByteArray(), "image.png").queue(msg -> {
            lock.lock();
            try {
                makeSpace(tc);
                this.currentQuestion = newQ;
                this.cooldown = currentQuestion.minimumSolveTimeSeconds() * 1000L;
                this.recentSentMessageID = msg.getIdLong();
            } finally {
                lock.unlock();
            }
        });
    }

    private ActionRow getPaginationActionRow(int maxPos, int currentPos){
        String prefix = buttonPrefix[0] + "|p";
        Button first = Button.of(ButtonStyle.PRIMARY,
                prefix + "f|" + "0",
                Emoji.fromMarkdown("⏮"));
        Button previous = Button.of(ButtonStyle.PRIMARY,
                prefix + "p|" + Math.max(currentPos-1, 0),
                Emoji.fromMarkdown("◀"));
        Button next = Button.of(ButtonStyle.PRIMARY,
                prefix + "n|" + Math.min(currentPos + 1, maxPos),
                Emoji.fromMarkdown("▶"));
        Button last = Button.of(ButtonStyle.PRIMARY,
                prefix + "l|" + maxPos,
                Emoji.fromMarkdown("⏭"));

        return ActionRow.of(
                currentPos == 0 ? first.asDisabled() : first,
                currentPos == 0 ? previous.asDisabled() : previous,
                currentPos == maxPos ? next.asDisabled() : next,
                currentPos == maxPos ? last.asDisabled() : last
        );
    }

    private Collection<ActionRow> getBooleanActionRow(Challenge challenge) {
        if (!(challenge instanceof BooleanAlgebra)){
            return Collections.emptyList();
        }
        // "a" -> answer, "t" -> true, "f" -> false;
        String trStr = (buttonPrefix[0] + "|" + "at");
        String faStr = (buttonPrefix[0] + "|" + "af");
        return Collections.singletonList(ActionRow.of(Button.success(trStr, "TRUE"), Button.danger(faStr, "FALSE")));
    }

    @Override
    public void initComponentInteractionHandle(JDA jda) {
    }

    @Override
    public void handleButtonInteraction(ButtonClickEvent interaction, String data) {
        char type = data.charAt(0);
        //leave cases for things like pagination and such here.
        switch (type) {
            case 'a':
                // a for answer
                if (recentSentMessageID != interaction.getMessageIdLong()) {
                    interaction.deferEdit().queue();
                    interaction.getHook().editOriginalComponents(Collections.emptyList()).queue();
                    interaction.getHook().sendMessage("Error: answer button tagged on non-recent message. Please report this error.").setEphemeral(true).queue();
                    return;
                }
                answerQuestion(data.substring(1, 2),  //either 't' or 'f'
                        interaction.getUser(), interaction.getTextChannel(),
                        err -> interaction.reply(err).setEphemeral(true).queue());
                break;
            case 'p':
                // p for page XYZ|p|pagenum
                int pageNum = Integer.parseInt(data.split("\\|", 2)[1]);
                int maxPos = (int) (Math.ceil(Database.TRIVIA_STORAGE.fetchTotalDatabaseMembers() / 10.0) - 1);
                interaction.editMessageEmbeds(sendScoreboardInfo(pageNum, interaction.getUser().getIdLong(), maxPos))
                        .setActionRows(getPaginationActionRow(maxPos, pageNum))
                        .queue();

        }

    }

    @Override
    public List<String> registerHandles() {
        return buttonHandles;
    }

    @Override
    public void onEvent(@NotNull GenericEvent genericEvent) {
        if (genericEvent instanceof GuildMessageReceivedEvent) {

            GuildMessageReceivedEvent event = (GuildMessageReceivedEvent) genericEvent;
            if (event.getChannel().getIdLong() == channelID){
                if (event.getMember() == null) return;
                if (event.getMember().getIdLong() == 187979032904728576L){
                    if (event.getMessage().getContentRaw().startsWith("!spawn")){
                        String[] args = event.getMessage().getContentRaw().split("\\s+");
                        LOGGER.info("Manually generating question with " + Arrays.toString(args));
                        Database.runLater(() -> {
                            recentMessageSpawnID = event.getMessageIdLong();
                            Challenge question = getChallengeForId(Integer.parseInt(args[1]));
                            postChallenge(question, event.getJDA().getTextChannelById(channelID));
                        });
                    }
                }

                return; //don't spawn from things happening in the channel - it's just annoying.
            }
            if (currentQuestion != null && Duration.between(OffsetDateTime.now(), TimeUtil.getTimeCreated(recentMessageSpawnID)).abs().toMillis() < cooldown) {
                // it hasn't been long enough yet.
                return;
            }
            if (currentQuestion == null && Duration.between(OffsetDateTime.now(), TimeUtil.getTimeCreated(recentMessageSpawnID)).abs().toMillis() < minCooldown) {
                // the current question _has_ been answered, but it hasn't been long enough to spawn a new one
                return;
            }

            if (event.getChannel().getIdLong() == 848237918471454720L)
                return; //don't spawn from things happening in the logs channel - it's just annoying.
            if (event.isWebhookMessage()){
                return; //don't spawn from things happening as a result of webhooks - it's just annoying
            }

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
//                Challenge question = genQuestion();
                Challenge question = getBoolAlgebra();
                postChallenge(question, target);
            });
        }
    }

    private Challenge getChallengeForId(int id){
        switch (id) {
            case 0:
                return getBoolAlgebra();
            case 1:
                return genBFSChallenge();
            case 2:
                return genDFSChallenge();
            case 3:
                return genMinSpanTree();
            case 4:
                return genDijkstraChallenge();
        }
        return getBoolAlgebra();
    }


    private void makeSpace(TextChannel channel) {
        if (currentQuestion == null) return;
        channel.editMessageEmbedsById(recentSentMessageID, nobodyIsHere()).setActionRows(Collections.emptyList()).queue();
        currentQuestion = null;
    }

    @Override
    public void handleCommand(CommandEvent event) {
        if (event.getTextChannel().getIdLong() != channelID && event.getAuthor().getIdLong() != 187979032904728576L) {
            event.reply("This isn't the right channel :/");
            return;
        }

        if (event.getCommand().equalsIgnoreCase("leaderboard") || event.getCommand().equalsIgnoreCase("lb")) {
            int scorePage = 0;
            if (event.getArgs().length == 1) {
                try {
                    scorePage = Integer.parseInt(event.getArgs()[0]) - 1;
                } catch (NumberFormatException ex) {
                }
            }

            int scorePageFinal = scorePage;
            Database.runLater(() -> { //don't run on the WS thread
                int maxPos = (int) (Math.ceil(Database.TRIVIA_STORAGE.fetchTotalDatabaseMembers() / 10.0) - 1);
                int finalPosition = Math.max(0, Math.min(maxPos, scorePageFinal));
                event.getTextChannel()
                        .sendMessageEmbeds(sendScoreboardInfo(finalPosition, event.getAuthor().getIdLong(), maxPos))
                        .setActionRows(getPaginationActionRow(maxPos, finalPosition))
                        .queue();
            });
            return;
        }
        if (event.getFullArg().isBlank() /*|| event.getArgs()[0].equalsIgnoreCase("help")*/ ) {
            EmbedBuilder builder = new EmbedBuilder();
            builder.setTitle("Trivia help");
            builder.setDescription("Answer questions that pop up in chat to score points.  The more difficult the question, the more points you'll earn.\n\n" +
                    "Answering the question incorrectly will deduct that many points - so don't guess (it also spoils it slightly for other people).\n\n" +
                    "If you get a question wrong, you can use `!solve` for the bot to work it out, and show you how to solve it.\n\n" +
                    "You can find out who is doing well using the `!leaderboard` command.  You can look at specific pages using `!leaderboard <pagenum>`.  " +
                    "[You can see the Scoreboard here, too.](https://passport.cmpsc.uk/)");
//            builder.addField("The Algebra Symbols:",
//                    Arrays.stream(BooleanOP.values()).map(op -> op.name() + " `" + op.symbol + "`").collect(Collectors.joining("\n"))
//                            + "\nNOT `¬`"
//                    , false);
            event.reply(builder.build());
            return;
        }

        if (event.getCommand().equalsIgnoreCase("solve")) {
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
                    Challenge prev = lastQuestion;
                    BufferedImage img = prev.generateSolutionImage();
                    ByteArrayOutputStream os = new ByteArrayOutputStream();
                    EmbedBuilder builder = new EmbedBuilder();
                    builder.setTitle("Solution");
                    builder.setDescription(prev.getQuestion());
                    builder.addField("Answer", prev.getSolution(), false);
                    builder.addField("Operations", "" + prev.getSolveOperationCount(), false);
                    if (img != null) {
                        try {
                            ImageIO.write(img, "png", os);
                        } catch (IOException ex) {
                            LOGGER.error("Failed to send image", ex);
                            return;
                        }
                        event.getTextChannel().sendFile(os.toByteArray(), "solution.png", AttachmentOption.SPOILER)
                                .setEmbeds(builder.build())
                                .queue();
                    } else {
                        event.getTextChannel().sendMessageEmbeds(builder.build())
                                .queue();
                    }
                });
                return;
            }
        }

//        if (event.getAuthor().getIdLong() == 187979032904728576L) {
//            if (event.getFullArg().equalsIgnoreCase("debug")) {
//                event.reply(String.format("recentSentMessageID: %d\n" +
//                        "recentMessageSpawnID: %d\n" +
//                        "cooldown: %d\n" +
//                        "minCooldown: %d\n" +
//                        "cooldownMult: %d\n" +
//                        "Lock State: %s\n", recentSentMessageID, recentMessageSpawnID, cooldown, minCooldown, cooldownMult, lock.toString()));
//            } else if (event.getArgs().length == 4) {
//                if (event.getArgs()[0].equalsIgnoreCase("gen")) {
//                    String type = event.getArgs()[1];
//                    int depth = Integer.parseInt(event.getArgs()[2]);
//                    int hardness = Integer.parseInt(event.getArgs()[3]);
//
//                    BooleanAlgebra balg;
//                    if (type.equalsIgnoreCase("bool")) {
//                        balg = new BooleanAlgebra(depth, hardness, 0);
//                    } else {
//                        balg = new BooleanAlgebra(depth, hardness, 0, ThreadLocalRandom.current().nextBoolean());
//                    }
//                    LOGGER.info("Manually spawned a new boolean \"" + type + "\" problem with depth: " + depth + ", hardness:" + hardness + " operations: " + balg.getOperations() + "   and scores " + getScoreForBooleanAlgebra(balg));
//                    Database.runLater(() -> { //don't run on WS thread
//                        if (balg.getOperations() > 2000) {
//                            event.reply("This is far too long to generate :( op:" + balg.getOperations());
//                            return;
//                        }
//                        String quest = balg.toString();
//                        if (quest.length() > 2048) {
//                            quest = balg.toCompactString();
//                        }
//                        int score = getScoreForBooleanAlgebra(balg);
//                        if (quest.length() < 2048) {
//                            event.reply("Generating problem... (may take a while to create the image)\n"
//                                    + "Op count: " + balg.getOperations() + ", scores: " + score);
//                        } else {
//                            event.reply("Generated message was too long.");
//                            return;
//                        }
//                        try {
//                            balg.findSolution();
//                            balg.markSolution();
//                            Question newQ = new Question(quest, balg.getValue(), 0, balg.genImage(true), balg);
//                            //Question newQ = new Question(quest, balg.getValue(), score, balg.genImage(false));
//                            recentMessageSpawnID = event.getMessageIdLong();
//                            askAlgebra(newQ, event.getTextChannel());
//                        } catch (Exception ex) {
//                            LOGGER.error("Failed to generate problem ", ex);
//                        }
////
//                    });
//                    return;
//
//                }
//            }
//        }
        if (currentQuestion == null) {
            event.reply("There is no active question.");
        }

        String answer = event.getFullArg().toUpperCase(Locale.ROOT); // case isn't important here.
        if (currentQuestion.isValidAnswer(answer)) {
            event.reply("That was not a valid answer.");
            return;
        }
        answerQuestion(answer, event.getUser(), event.getTextChannel(), event::reply);
        event.getMessage().delete().queue();
    }

    @CheckReturnValue
    @Nonnull
    private MessageEmbed sendScoreboardInfo(int finalPosition, long targetID, int maxPages) {
        //leaderboards
        List<TriviaScore> scores = Database.TRIVIA_STORAGE.fetchLeaderboard(finalPosition);
        TriviaScore userScore = Database.TRIVIA_STORAGE.fetchUserScore(targetID);
        return getScores(userScore, scores, finalPosition, maxPages + 1);
    }

    private synchronized void answerQuestion(String answer, User user, TextChannel channel, Consumer<String> error) {

        lock.lock();
        //we're going into question-got-answered mode
        try {
            if (this.currentQuestion == null) {
                error.accept("Someone beat you to it.  Better luck next time.");
//                event.reply("You got beaten to it. Very close, because the only thing that saved this from duplicating was the locking mechanism.");
            } else {
                this.lastQuestion = currentQuestion;
                this.lastQuestionSolved = false;
                boolean isRight = currentQuestion.isCorrectAnswer(answer);
                Database.TRIVIA_STORAGE.updateMemberScore(user.getIdLong(), currentQuestion.getPoints(isRight));
                updateMessage(currentQuestion, isRight, user, recentSentMessageID, channel);
                this.currentQuestion = null;
            }
        } finally {
            lock.unlock();
        }
    }


    private MessageEmbed nobodyIsHere() {
        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle("You missed it ):");
        eb.setDescription(currentQuestion.getQuestion());
        eb.addField(currentQuestion.getPoints(true) + " points went lost.", "The correct answer is ||`" + currentQuestion.getSolution() + "`||", false);
        eb.setColor(0x505050);
        return eb.build();
    }

    private void updateMessage(Challenge question, boolean correct, User user, long messageID, TextChannel textChannel) {
        EmbedBuilder builder = new EmbedBuilder();
        builder.setColor(correct ? 0x20e035 : 0xe02035);
        builder.setTitle((correct ? "Correct! " : "Incorrect! "));
//        builder.setDescription(user.getAsMention() + "\n\n" + question.getQuery() + "\n\n" + "The correct answer is: `" + question.getAnswer() + "`");
        builder.setDescription(question.getQuestion());
        String timeStr = " completed it in " + humanReadableFormat(Duration.between(Instant.now(), TimeUtil.getTimeCreated(messageID)).abs());
        builder.addField(String.format("You got %d point%s.", question.getPoints(correct), Math.abs(question.getPoints(correct))>1 ? "s" : ""), user.getAsMention() + timeStr + " - the correct answer is " +
                "||`" + question.getSolution() + "`||", false);
        builder.setImage("attachment://image.png");
//        builder.setAuthor(user.getAsTag());
        textChannel.editMessageEmbedsById(messageID, builder.build()).setActionRows(Collections.emptyList())/*.override(true)*/.queue();
    }


    private MessageEmbed askQuestion(Challenge question) {
        EmbedBuilder builder = new EmbedBuilder();
        builder.setColor(0x3040c0);
        builder.setTitle("Question!");
        builder.setDescription(question.getQuestion());
        builder.setFooter("You can score " + question.getPoints(true) + " point(s) by running `!t <answer>` and getting the answer correct.");
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

    private Challenge genQuestion() {
        //pick what type we are going for:
        int type = randInclusive(0, 4);
        switch (type){
            case 0:
                return getBoolAlgebra();
            case 1:
                return genBFSChallenge();
            case 2:
                return genDFSChallenge();
            case 3:
                return genMinSpanTree();
            case 4:
                return genDijkstraChallenge();

        }
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

    private Challenge getBoolAlgebra() {
        boolean invalid = true;
        BooleanAlgebra balg = null;
        int difficulty = 0, hardness = 0;
        while (invalid) {
            difficulty = randInclusive(1, 5);
            difficulty = randInclusive(1, difficulty);
            hardness = randInclusive(3, 7);
            hardness = randInclusive(2, hardness);
            balg = new BooleanAlgebra(difficulty, hardness);
            if (balg.getQuestion().length() < 2048) invalid = false;
        }
        LOGGER.info("Automatically spawned a new boolean problem with depth: " + difficulty + ", hardness:" + hardness + " and scores " + balg.getPoints(true));
        return balg;
    }

    private Challenge genBFSChallenge(){
        int nodes = randInclusive(7, 14);
        BFSChallenge challenge = new BFSChallenge(nodes);
        LOGGER.info("Spawning new BFS Challenge - nodes: " + nodes + " with score " + challenge.getPoints(true) + "/" + challenge.getPoints(false) + " opcount:" + challenge.getSolveOperationCount());
        return challenge;
    }

    private Challenge genDFSChallenge(){
        int nodes = randInclusive(7, 14);
        DFSChallenge challenge = new DFSChallenge(nodes);
        LOGGER.info("Spawning new DFS Challenge - nodes: " + nodes + " with score " + challenge.getPoints(true) + "/" + challenge.getPoints(false) + " opcount:" + challenge.getSolveOperationCount());
        return challenge;
    }

    private Challenge genMinSpanTree(){
        int nodes = randInclusive(10, 16);
        MinimumSpanningTreeChallenge challenge = new MinimumSpanningTreeChallenge(nodes);
        LOGGER.info("Spawning new SpanTree Challenge - nodes: " + nodes + " with score " + challenge.getPoints(true) + "/" + challenge.getPoints(false) + " opcount:" + challenge.getSolveOperationCount());
        return challenge;
    }

    private Challenge genDijkstraChallenge(){
        int nodes = randInclusive(7, 16);
        DijkstraChallenge challenge = new DijkstraChallenge(nodes);
        LOGGER.info("Spawning new Dijkstra Challenge - nodes: " + nodes + " with score " + challenge.getPoints(true) + "/" + challenge.getPoints(false) + " opcount:" + challenge.getSolveOperationCount());
        return challenge;
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

    @Override
    public List<CommandData> registerGlobalCommands() {
        List<CommandData> commands = new ArrayList<>();
        commands.add(
                new CommandData("leaderboard", "Show the leaderboard")
                        .addOption(OptionType.INTEGER, "page", "Page number", false)
        );
        return commands;
    }

    @Override
    public void handleSlashCommand(SlashCommandEvent event) {
        if (event.getName().equals("leaderboard")) {
            if (event.getChannel().getIdLong() != channelID) {
                event.reply("This isn't the correct channel for this!  Please use <#" + channelID + "> to view the leaderboard.")
                        .setEphemeral(true).queue();
                return;
            }
            //send the normal message.
            OptionMapping opt = event.getOption("page");
            int pageNumber = opt == null ? 0 : (int) opt.getAsLong();
            Database.runLater(() -> {
                int maxPos = (int) (Math.ceil(Database.TRIVIA_STORAGE.fetchTotalDatabaseMembers() / 10.0) - 1);
                int finalPosition = Math.max(0, Math.min(maxPos, pageNumber - 1));
                event.replyEmbeds(sendScoreboardInfo(finalPosition, event.getUser().getIdLong(), maxPos))
                        .addActionRows(getPaginationActionRow(maxPos, finalPosition))
                        .setEphemeral(false)
                        .queue();
            });
        }
    }
}
