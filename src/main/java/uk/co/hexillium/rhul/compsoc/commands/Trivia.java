package uk.co.hexillium.rhul.compsoc.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.TimeUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYItemRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.time.Day;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.title.LegendTitle;
import uk.co.hexillium.rhul.compsoc.CommandDispatcher;
import uk.co.hexillium.rhul.compsoc.CommandEvent;
import uk.co.hexillium.rhul.compsoc.commands.challenges.*;
import uk.co.hexillium.rhul.compsoc.commands.handlers.ComponentInteractionHandler;
import uk.co.hexillium.rhul.compsoc.commands.handlers.SlashCommandHandler;
import uk.co.hexillium.rhul.compsoc.persistence.Database;
import uk.co.hexillium.rhul.compsoc.persistence.entities.ScoreHistory;
import uk.co.hexillium.rhul.compsoc.persistence.entities.TriviaScore;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.awt.Font;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

public class Trivia extends Command implements EventListener, ComponentInteractionHandler, SlashCommandHandler {

    private static final int CURRENT_SEASON_NUMBER = 1;
    private static final Color CHART_BACKGROUND_COLOR = Color.decode("#1e2124");
    private static final Color CHART_FONT_COLOR = Color.WHITE;
    private static final Font CHART_TITLE_FONT = new Font("Tahoma", Font.BOLD, 25);
    private static final Font CHART_LABEL_FONT = new Font("Tahoma", Font.PLAIN, 20);
    private static final BasicStroke CHART_STROKE_WIDTH = new BasicStroke(3f);

    private static final Logger LOGGER = LogManager.getLogger(Trivia.class);

    private static final String[] commands = {"t", "triv", "trivia", "leaderboard", "lb", "solve"};
    private static final String[] buttonPrefix = {"c:tr"}; //command:trivia
    private static final List<String> buttonHandles;
    private static final long channelID = 766050353174544384L;

    //    private HMAC hmac;
    private static final long minCooldown = 30_000; // the minimum coolown - used for quickly solved puzzles, and the base 0.5 min
    private static final long cooldownMult = 40_000; // 40 sec || 0.66667 mins

    static {
        buttonHandles = new ArrayList<>();
        Collections.addAll(buttonHandles, buttonPrefix);
    }

    private final ReentrantLock lock = new ReentrantLock(false);
    private final Object lastQuestionSolverLock = new Object();
    private long recentSentMessageID = -1L;
    private long recentMessageSpawnID = -1L;
    private long cooldown = 90_000; // the current question's timer - default: 1.5 mins
    private Challenge currentQuestion;
    private Challenge lastQuestion;
    private boolean lastQuestionSolved = false;

    private final ExecutorService graphDrawThread = Executors.newSingleThreadExecutor();

    public Trivia() {
        super("Trivia", "Answer questions to score points", "Answer questions to score points", commands, "fun");
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

    static int randInclusive(int lower, int upper) {
        return lower + ThreadLocalRandom.current().nextInt((upper - lower) + 1);
    }

    @Override
    public void onLoad(JDA jda, CommandDispatcher manager) {
        jda.addEventListener(this);
    }

    private void postChallenge(Challenge newQ, GuildMessageChannel tc) {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        try {
            ImageIO.write(newQ.getImage(), "png", os);
        } catch (IOException ex) {
            LOGGER.error("Failed to send image", ex);
            return;
        }
        tc.sendMessageEmbeds(askQuestion(newQ)).setComponents(getBooleanActionRow(newQ)).addFiles(FileUpload.fromData(os.toByteArray(), "image.png")).queue(msg -> {
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

    private ActionRow getPaginationActionRow(int maxPos, int currentPos, int season) {
        String prefix = buttonPrefix[0] + "|l|" + season + "|";
        Button first = Button.of(ButtonStyle.PRIMARY,
                prefix + "f|" + "0",
                Emoji.fromUnicode("⏮"));
        Button previous = Button.of(ButtonStyle.PRIMARY,
                prefix + "p|" + Math.max(currentPos - 1, 0),
                Emoji.fromUnicode("◀"));
        Button next = Button.of(ButtonStyle.PRIMARY,
                prefix + "n|" + Math.min(currentPos + 1, maxPos),
                Emoji.fromUnicode("▶"));
        Button last = Button.of(ButtonStyle.PRIMARY,
                prefix + "l|" + maxPos,
                Emoji.fromUnicode("⏭"));

        return ActionRow.of(
                currentPos == 0 ? first.asDisabled() : first,
                currentPos == 0 ? previous.asDisabled() : previous,
                currentPos == maxPos ? next.asDisabled() : next,
                currentPos == maxPos ? last.asDisabled() : last
        );
    }

    private Collection<ActionRow> getBooleanActionRow(Challenge challenge) {
        if (!(challenge instanceof BooleanAlgebra)) {
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
    public void handleButtonInteraction(ButtonInteractionEvent interaction, String data) {
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
                        interaction.getUser(), interaction.getChannel().asGuildMessageChannel(),
                        err -> interaction.reply(err).setEphemeral(true).queue());
                break;
            case 'p': {
                // this is the _old_ pagination button.
                // any new pagination buttons should be handled by the new pagination system.
                // p for page XYZ|p|pagenum
                int pageNum = Integer.parseInt(data.split("\\|", 2)[1]);
                int maxPos = (int) (Math.ceil(Database.TRIVIA_STORAGE.fetchTotalDatabaseMembers(0) / 10.0) - 1);
                interaction.editMessageEmbeds(sendScoreboardInfo(pageNum, interaction.getUser().getIdLong(), maxPos, 0))
                        .setComponents(getPaginationActionRow(maxPos, pageNum, 0))
                        .queue();
                break;
            }
            case 'l': {
                //this is the new pagination button.
                // l for leaderboard I guess
                //l|0|n|1
                String[] inputs = data.split("\\|", 4);
                int seasonNum = Integer.parseInt(inputs[1]);
                int pageNum = Integer.parseInt(inputs[3]);
                int maxPos = (int) (Math.ceil(Database.TRIVIA_STORAGE.fetchTotalDatabaseMembers(seasonNum) / 10.0) - 1);
                interaction.editMessageEmbeds(sendScoreboardInfo(pageNum, interaction.getUser().getIdLong(), maxPos, seasonNum))
                        .setComponents(getPaginationActionRow(maxPos, pageNum, seasonNum))
                        .queue();
                break;
            }
        }

    }

    @Override
    public List<String> registerHandles() {
        return buttonHandles;
    }

    @Override
    public void onEvent(@NotNull GenericEvent genericEvent) {
        if (genericEvent instanceof MessageReceivedEvent event) {
            if (!event.isFromGuild()) return;
            if (event.getChannel().getIdLong() == channelID) {
                if (event.getMember() == null) return;
                if (event.getMember().getIdLong() == 187979032904728576L) {
                    if (event.getMessage().getContentRaw().startsWith("!spawn")) {
                        String[] args = event.getMessage().getContentRaw().split("\\s+");
                        LOGGER.info("Manually generating question with " + Arrays.toString(args));
                        Database.runLater(() -> {
                            recentMessageSpawnID = event.getMessageIdLong();
                            Challenge question = getChallengeForId(Integer.parseInt(args[1]));
                            postChallenge(question, event.getJDA().getTextChannelById(channelID));
                        });
                    } else if (event.getMessage().getContentRaw().startsWith("!drawallscores")){
                        Database.runLater(() -> {
                            ScoreHistory[] scores = Database.TRIVIA_STORAGE.fetchScoreHistoryForAllUsers(CURRENT_SEASON_NUMBER, event.getMessage().getGuild().getIdLong());
                            BufferedImage image = drawChart(scores, 1500, 1000);
                            ByteArrayOutputStream os = new ByteArrayOutputStream();
                            try {
                                ImageIO.write(image, "png", os);
                            } catch (IOException ex) {
                                LOGGER.error("Failed to send image", ex);
                                return;
                            }
                            event.getChannel().sendFiles(FileUpload.fromData(os.toByteArray(), "image.png")).queue();
                        });
                    }
                }

                return; //don't spawn from things happening in the channel - it's just annoying.
            }
            if (currentQuestion != null && Duration.between(OffsetDateTime.now(), TimeUtil.getTimeCreated(recentMessageSpawnID)).abs().toMillis() < currentQuestion.minimumSolveTimeSeconds() * 1000L) {
                // it hasn't been long enough yet.
                return;
            }
            if (currentQuestion == null && Duration.between(OffsetDateTime.now(), TimeUtil.getTimeCreated(recentMessageSpawnID)).abs().toMillis() < minCooldown) {
                // the current question _has_ been answered, but it hasn't been long enough to spawn a new one
                return;
            }

            if (event.getChannel().getIdLong() == 848237918471454720L)
                return; //don't spawn from things happening in the logs channel - it's just annoying.
            if (event.isWebhookMessage()) {
                return; //don't spawn from things happening as a result of webhooks - it's just annoying
            }

            // we can spawn one in!
            // let's have a 2/22 chance of that happening
            if (randInclusive(1, 22) > 2) return;
            GuildMessageChannel target = event.getJDA().getTextChannelById(channelID);
            if (target == null) {
                LOGGER.error("Failed to find textchannel. Aborting");
                return;
            }
            Database.runLater(() -> {
                recentMessageSpawnID = event.getMessageIdLong();
                Challenge question = genQuestion();
//                Challenge question = getBoolAlgebra();
                postChallenge(question, target);
            });
        }
    }

    private Challenge getChallengeForId(int id) {
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

    private void makeSpace(GuildMessageChannel channel) {
        if (currentQuestion == null) return;
        channel.editMessageEmbedsById(recentSentMessageID, nobodyIsHere()).setComponents(Collections.emptyList()).queue();
        currentQuestion = null;
    }

    @Override
    public void handleCommand(CommandEvent event) {
        if (event.getGuildMessageChannel().getIdLong() != channelID && event.getAuthor().getIdLong() != 187979032904728576L) {
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
                int maxPos = (int) (Math.ceil(Database.TRIVIA_STORAGE.fetchTotalDatabaseMembers(CURRENT_SEASON_NUMBER) / 10.0) - 1);
                int finalPosition = Math.max(0, Math.min(maxPos, scorePageFinal));
                event.getGuildMessageChannel()
                        .sendMessageEmbeds(sendScoreboardInfo(finalPosition, event.getAuthor().getIdLong(), maxPos, CURRENT_SEASON_NUMBER))
                        .setComponents(getPaginationActionRow(maxPos, finalPosition, CURRENT_SEASON_NUMBER))
                        .queue();
            });
            return;
        }

        if (event.getCommand().equalsIgnoreCase("solve")) {
            if (event.getFullArg().equalsIgnoreCase("debug")) {
                event.reply(String.format("Type: %s,\n" +
                                "Timeout: %d,\n" +
                                "Question: %s,\n" +
                                "Other Debug info:\n%s",
                        currentQuestion.getClass().getName(),
                        currentQuestion.minimumSolveTimeSeconds(),
                        currentQuestion.getQuestion(),
                        currentQuestion.getDebugInformation()

                ));
                return;
            }
            if (event.getFullArg().equalsIgnoreCase("debugprev")) {
                event.reply(String.format("Type: %s,\n" +
                                "Timeout: %d,\n" +
                                "Question: %s,\n" +
                                "Other Debug info:\n%s",
                        lastQuestion.getClass().getName(),
                        lastQuestion.minimumSolveTimeSeconds(),
                        lastQuestion.getQuestion(),
                        lastQuestion.getDebugInformation()

                ));
                return;
            }
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
                        event.getGuildMessageChannel().sendFiles(FileUpload.fromData(os.toByteArray(), "SPOILER_solution.png"))
                                .setEmbeds(builder.build())
                                .queue();
                    } else {
                        event.getGuildMessageChannel().sendMessageEmbeds(builder.build())
                                .queue();
                    }
                });
            }
            return;
        }

        if (event.getFullArg().isBlank() /*|| event.getArgs()[0].equalsIgnoreCase("help")*/) {
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

        if (currentQuestion == null) {
            event.reply("There is no active question.");
        }

        String answer = event.getFullArg().toUpperCase(Locale.ROOT); // case isn't important here.
        if (!currentQuestion.isValidAnswer(answer)) {
            event.reply("That was not a valid answer.");
            return;
        }
        answerQuestion(answer, event.getUser(), event.getGuildMessageChannel(), event::reply);
        event.getMessage().delete().queue();
    }

    @CheckReturnValue
    @Nonnull
    private MessageEmbed sendScoreboardInfo(int finalPosition, long targetID, int maxPages, int seasonNum) {
        //leaderboards
        List<TriviaScore> scores = Database.TRIVIA_STORAGE.fetchLeaderboard(finalPosition, seasonNum);
        TriviaScore userScore = Database.TRIVIA_STORAGE.fetchUserScore(targetID, seasonNum);
        return getScores(userScore, scores, finalPosition, maxPages + 1, seasonNum);
    }

    private synchronized void answerQuestion(String answer, User user, GuildMessageChannel channel, Consumer<String> error) {

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
                Database.TRIVIA_STORAGE.updateMemberScore(user.getIdLong(), channel.getGuild().getIdLong(), currentQuestion.getPoints(isRight), CURRENT_SEASON_NUMBER);
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

    private void updateMessage(Challenge question, boolean correct, User user, long messageID, GuildMessageChannel textChannel) {
        EmbedBuilder builder = new EmbedBuilder();
        builder.setColor(correct ? 0x20e035 : 0xe02035);
        builder.setTitle((correct ? "Correct! " : "Incorrect! "));
//        builder.setDescription(user.getAsMention() + "\n\n" + question.getQuery() + "\n\n" + "The correct answer is: `" + question.getAnswer() + "`");
        builder.setDescription(question.getQuestion());
        String timeStr = " completed it in " + humanReadableFormat(Duration.between(Instant.now(), TimeUtil.getTimeCreated(messageID)).abs());
        builder.addField(String.format("You got %d point%s.", question.getPoints(correct), Math.abs(question.getPoints(correct)) > 1 ? "s" : ""), user.getAsMention() + timeStr + " - the correct answer is " +
                "||`" + question.getSolution() + "`||", false);
        builder.setImage("attachment://image.png");
//        builder.setAuthor(user.getAsTag());
        textChannel.editMessageEmbedsById(messageID, builder.build()).setComponents(Collections.emptyList())/*.override(true)*/.queue();
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

    private MessageEmbed getScores(TriviaScore selfScore, List<TriviaScore> scores, int page, int maxPages, int seasonNumber) {
        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("Leaderboard For Season " + seasonNumber);
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
        embed.addField(EmbedBuilder.ZERO_WIDTH_SPACE, "You can use `/leaderboard` to access different season's leaderboards.", false);
        embed.setFooter("Page " + (page + 1) + "/" + maxPages);
        return embed.build();
    }

    private Challenge genQuestion() {
        //pick what type we are going for:
        int type = randInclusive(0, 4);
        switch (type) {
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

    private Challenge genBFSChallenge() {
        int nodes = randInclusive(7, 14);
        BFSChallenge challenge = new BFSChallenge(nodes);
        LOGGER.info("Spawning new BFS Challenge - nodes: " + nodes + " with score " + challenge.getPoints(true) + "/" + challenge.getPoints(false) + " opcount:" + challenge.getSolveOperationCount());
        return challenge;
    }

    private Challenge genDFSChallenge() {
        int nodes = randInclusive(7, 14);
        DFSChallenge challenge = new DFSChallenge(nodes);
        LOGGER.info("Spawning new DFS Challenge - nodes: " + nodes + " with score " + challenge.getPoints(true) + "/" + challenge.getPoints(false) + " opcount:" + challenge.getSolveOperationCount());
        return challenge;
    }

    private Challenge genMinSpanTree() {
        int nodes = randInclusive(10, 16);
        MinimumSpanningTreeChallenge challenge = new MinimumSpanningTreeChallenge(nodes);
        LOGGER.info("Spawning new SpanTree Challenge - nodes: " + nodes + " with score " + challenge.getPoints(true) + "/" + challenge.getPoints(false) + " opcount:" + challenge.getSolveOperationCount());
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

    private Challenge genDijkstraChallenge() {
        int nodes = randInclusive(7, 16);
        DijkstraChallenge challenge = new DijkstraChallenge(nodes);
        LOGGER.info("Spawning new Dijkstra Challenge - nodes: " + nodes + " with score " + challenge.getPoints(true) + "/" + challenge.getPoints(false) + " opcount:" + challenge.getSolveOperationCount());
        return challenge;
    }

    @Override
    public List<CommandData> registerGlobalCommands() {
        List<CommandData> commands = new ArrayList<>();
        commands.add(
                Commands.slash("leaderboard", "Show the leaderboard")
                        .addOption(OptionType.INTEGER, "page", "Page number", false)
                        .addOption(OptionType.INTEGER, "season", "Season number", false));
        commands.add(
                Commands.slash("scorehistory", "Show your score history")
                        .addOption(OptionType.USER, "primaryuser", "The primary user to graph", true)
                        .addOption(OptionType.INTEGER, "season", "The season to track. Defaults to the current season.", false)
                        .addOption(OptionType.USER, "otheruser-0", "Another user to compare to", false)
                        .addOption(OptionType.USER, "otheruser-1", "Another user to compare to", false)
                        .addOption(OptionType.USER, "otheruser-2", "Another user to compare to", false)
                        .addOption(OptionType.USER, "otheruser-3", "Another user to compare to", false)
                        .addOption(OptionType.USER, "otheruser-4", "Another user to compare to", false)
        );
        return commands;
    }

    @Override
    public void handleSlashCommand(SlashCommandInteractionEvent event) {
        if (event.getName().equals("leaderboard")) {
            if (event.getChannel().getIdLong() != channelID) {
                event.reply("This isn't the correct channel for this!  Please use <#" + channelID + "> to view the leaderboard.")
                        .setEphemeral(true).queue();
                return;
            }
            //send the normal message.
            OptionMapping opt = event.getOption("page");
            int pageNumber = opt == null ? 0 : (int) opt.getAsLong();

            OptionMapping seasonOpt = event.getOption("season");
            int season = seasonOpt == null ? CURRENT_SEASON_NUMBER : (int) seasonOpt.getAsLong();
            season = Math.max(season, 0);
            season = Math.min(season, CURRENT_SEASON_NUMBER);
            final int effectiveSeason = season;
            Database.runLater(() -> {
                int maxPos = (int) (Math.ceil(Database.TRIVIA_STORAGE.fetchTotalDatabaseMembers(effectiveSeason) / 10.0) - 1);
                int finalPosition = Math.max(0, Math.min(maxPos, pageNumber - 1));
                event.replyEmbeds(sendScoreboardInfo(finalPosition, event.getUser().getIdLong(), maxPos, effectiveSeason))
                        .addComponents(getPaginationActionRow(maxPos, finalPosition, effectiveSeason))
                        .setEphemeral(false)
                        .queue();
            });
        } else if (event.getName().equals("scorehistory")) {
            if (event.getChannel().getIdLong() != channelID) {
                event.reply("This isn't the correct channel for this!  Please use <#" + channelID + "> to view score histories.")
                        .setEphemeral(true).queue();
                return;
            }
            //noinspection ConstantConditions    - we know this is in a guild as we check the channel id above
            long guildId = event.getGuild().getIdLong();
            OptionMapping seasonOpt = event.getOption("season");
            int season = seasonOpt == null ? CURRENT_SEASON_NUMBER : (int) seasonOpt.getAsLong();
//
//            OptionMapping usersOpt = event.getOption("otheruser");
//            long[] users = usersOpt == null ? new long[]{event.getUser().getIdLong()} : new long[]{event.getUser().getIdLong(), usersOpt.getAsLong()};

            long[] users = event.getOptions().stream().filter(om -> om.getType() == OptionType.USER)
                    .mapToLong(OptionMapping::getAsLong)
                    .toArray();

            event.deferReply(false).queue();

            Database.runLater(() -> {
                ScoreHistory[] history = Database.TRIVIA_STORAGE.fetchScoreHistory(season, guildId, users);
                if (history.length == 0){
                    event.getHook().sendMessage("Nothing to process, either there was an error, or no history exists for your selection.").queue();
                    return;
                }
                graphDrawThread.submit(() -> {
                    try {
                        if (event.getHook().isExpired()) return;
                        BufferedImage image = drawChart(history, 1200, 700);
                        postChart(event.getHook(), image);
                    } catch (Exception ex){
                        LOGGER.error("Failed drawing graph ", ex);
                        event.getHook().sendMessage("Failed to draw graph: " + ex.getMessage()).queue();
                    }
                });
            });
            // extract users + season and validate.
            // ACK message
            // call to database to fetch data
            // async draw chart
            // update message with the chart
        }
    }

    private void postChart(InteractionHook hook, BufferedImage image){
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "png", os);
        } catch (IOException ex) {
            LOGGER.error("Failed to send image", ex);
            return;
        }
        hook.sendFiles(FileUpload.fromData(os.toByteArray(), "chart.png")).queue();
    }

    private BufferedImage drawChart(ScoreHistory[] scores, int width, int height){
        List<TimeSeries> timeData = new ArrayList<>();
        for (ScoreHistory history : scores){
            TimeSeries series = new TimeSeries(history.getUsername());
            Day[] days = history.getDays();
            int[] userScores = history.getScores();
            for (int i = 0; i < days.length; i++) {
                series.add(days[i], userScores[i]);
            }
            timeData.add(series);
        }
        TimeSeriesCollection timeSeries = new TimeSeriesCollection();
        timeData.forEach(timeSeries::addSeries);

        JFreeChart chart = ChartFactory.createTimeSeriesChart("Score History", "Day", "Points", timeSeries);

        chart.getTitle().setPaint(CHART_FONT_COLOR);
        chart.setBackgroundPaint(CHART_BACKGROUND_COLOR);

        LegendTitle legend = chart.getLegend();
        legend.setItemPaint(CHART_FONT_COLOR);
        legend.setBackgroundPaint(CHART_BACKGROUND_COLOR);
        legend.setItemFont(CHART_LABEL_FONT);

        XYPlot plot = (XYPlot) chart.getPlot();
        plot.setBackgroundPaint(CHART_BACKGROUND_COLOR);

        ValueAxis xAxis = plot.getDomainAxis();
        xAxis.setLabelPaint(CHART_FONT_COLOR);
        xAxis.setLabelFont(CHART_TITLE_FONT);
        xAxis.setTickLabelPaint(CHART_FONT_COLOR);
        xAxis.setTickLabelFont(CHART_LABEL_FONT);

        ValueAxis yAxis = plot.getRangeAxis();
        yAxis.setLabelPaint(CHART_FONT_COLOR);
        yAxis.setLabelFont(CHART_TITLE_FONT);
        yAxis.setTickLabelPaint(CHART_FONT_COLOR);
        yAxis.setTickLabelFont(CHART_LABEL_FONT);

        XYItemRenderer r = plot.getRenderer();
        if (r instanceof XYLineAndShapeRenderer) {
            XYLineAndShapeRenderer renderer = (XYLineAndShapeRenderer) r;
            renderer.setDefaultShapesFilled(true);
            renderer.setDrawSeriesLineAsPath(true);
            renderer.setDefaultStroke(CHART_STROKE_WIDTH);
            for (int i = 0; i < timeData.size(); i++){
                renderer.setSeriesStroke(i, CHART_STROKE_WIDTH);
            }

        }
        return chart.createBufferedImage(width, height);
    }
}

