package uk.co.hexillium.rhul.compsoc.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.utils.TimeUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import uk.co.hexillium.rhul.compsoc.CommandDispatcher;
import uk.co.hexillium.rhul.compsoc.CommandEvent;
import uk.co.hexillium.rhul.compsoc.persistence.Database;
import uk.co.hexillium.rhul.compsoc.persistence.entities.TriviaScore;

import java.awt.*;
import java.awt.geom.QuadCurve2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Trivia extends Command implements EventListener{

    private static final Logger LOGGER = LogManager.getLogger(Trivia.class);

    private static final String[] commands = {"t", "triv", "trivia", "leaderboard", "lb"};

    private static final long channelID = 766050353174544384L;
    private long recentSentMessageID = -1L;
    private long recentMessageSpawnID = -1L;
    private static final long cooldown = 90_000; // 1.5 mins
    private static final long timeout = 60_000; // 1 min
    private final ReentrantLock lock = new ReentrantLock(false);

    private static final Pattern truthyRegex = Pattern.compile("(1|true|t|yes)", Pattern.CASE_INSENSITIVE);
    private static final Pattern falsyRegex = Pattern.compile("(0|false|f|no)", Pattern.CASE_INSENSITIVE);


    private Question question;

    public Trivia() {
        super("Trivia", "Answer questions to score points", "Answer questions to score points", commands , "fun");
    }

    @Override
    public void onLoad(JDA jda, CommandDispatcher manager) {
        jda.addEventListener(this);
    }

    @Override
    public void onEvent(@NotNull GenericEvent genericEvent) {
        if (genericEvent instanceof GuildMessageReceivedEvent){
            if (Duration.between(OffsetDateTime.now(), TimeUtil.getTimeCreated(recentMessageSpawnID)).abs().toMillis() < cooldown) {
                // it hasn't been long enough yet.
                return;
            }
            GuildMessageReceivedEvent event = (GuildMessageReceivedEvent) genericEvent;
            if (event.getChannel().getIdLong() == channelID) return; //don't spawn from things happening in the channel - it's just annoying.
            // we can spawn one in!
            // let's do a 2/15 chance of that happening
            if (randInclusive(1, 15) > 2) return;
            TextChannel target = event.getJDA().getTextChannelById(channelID);
            if (target == null){
                LOGGER.error("Failed to find textchannel. Aborting");
                return;
            }
            Question question = genQuestion();
            target.sendMessage(askQuestion(question)).queue(m -> {
                lock.lock();
                try {
                    makeSpace(target);
                    this.question = question;
                    this.recentMessageSpawnID = event.getMessageIdLong();
                    recentSentMessageID = m.getIdLong();
                } finally {
                    lock.unlock();
                }
            });


        }
    }

    private void makeSpace(TextChannel channel){
        if (question == null) return;
        channel.editMessageById(recentSentMessageID, nobodyIsHere()).queue();
        question = null;
    }

    @Override
    public void handleCommand(CommandEvent event) {
        if (event.getTextChannel().getIdLong() != channelID){
            event.reply("This isn't the right channel :/");
            return;
        }
        if (event.getCommand().equalsIgnoreCase("leaderboard") || event.getCommand().equalsIgnoreCase("lb")){
            int position = 0;
            if (event.getArgs().length == 1){
                try {
                    position = Integer.parseInt(event.getArgs()[0]) - 1;
                } catch (NumberFormatException ex){
                }
            }
            //leaderboards
            int maxPos = Database.TRIVIA_STORAGE.fetchTotalDatabaseMembers() / 10;
            int finalPosition = Math.max(0, Math.min(maxPos, position));

            Database.runLater(()-> {
                List<TriviaScore> scores = Database.TRIVIA_STORAGE.fetchLeaderboard(finalPosition);
                event.reply(getScores(scores, finalPosition, (Database.TRIVIA_STORAGE.fetchTotalDatabaseMembers() / 10)+1));
            });
            return;
        }
        if (event.getFullArg().isBlank() || event.getArgs()[0].equalsIgnoreCase("help")){
            EmbedBuilder builder = new EmbedBuilder();
            builder.setTitle("Trivia/Boolean Algebra help");
            builder.setDescription("Answer questions that pop up in chat to score points.  The more difficult the question, the more points you'll earn.\n\n" +
                    "Answering the question incorrectly will deduct that many points - so don't guess (it also spoils it slightly for other people).\n\n" +
                    "You can find out who is doing well using the `!leaderboard` command.  You can look at specific pages using `!leaderboard <pagenum>`.");
            builder.addField("The Algebra Symbols:",
                    Arrays.stream(BooleanOP.values()).map(op -> op.name() + " `" + op.symbol + "`").collect(Collectors.joining("\n"))
                    + "\nNOT `¬`"
                    , false);
            event.reply(builder.build());
            return;
        }

        if (event.getAuthor().getIdLong() == 187979032904728576L){
            if (event.getArgs().length == 4){
                if (event.getArgs()[0].equalsIgnoreCase("gen")){
                    String type = event.getArgs()[1];
                    int depth = Integer.parseInt(event.getArgs()[2]);
                    int hardness = Integer.parseInt(event.getArgs()[3]);

                    BooleanAlgebra balg = new BooleanAlgebra(depth, hardness, 0);
                    LOGGER.info("Manually spawned a new boolean problem with depth: " + depth + ", hardness:" + hardness + " and scores " + getScoreForBooleanAlgebra(balg));
                    Question newQ = new Question(balg.toString(), balg.getValue(), getScoreForBooleanAlgebra(balg));
                    if (newQ.getQuery().length() >= 2048){
                        event.reply("Generated message was too long.");
                        return;
                    }
                    event.getChannel().sendMessage(askQuestion(newQ)).queue(msg -> {
                        lock.lock();
                        try {
                            makeSpace(event.getTextChannel());
                            this.question = newQ;
                            this.recentSentMessageID = msg.getIdLong();
                        } finally {
                            lock.unlock();
                        }
                    });
                    return;

                }
            }
        }
        if (question == null){
            event.reply("There is no active question.");
        }
        //try to give them benefit of the doubt
        if (question.getAnswer() instanceof Boolean){
            String answer = event.getFullArg().toLowerCase(); // case isn't important here.
            boolean isTrue = truthyRegex.matcher(answer).matches();
            boolean isFalse = falsyRegex.matcher(answer).matches();
            if (!isTrue && !isFalse){
                event.reply("That was not a valid answer.");
                return;
            }
            lock.lock();
            try {
                boolean correct = (Boolean) question.getAnswer();
                boolean isRight = testAnswer(answer, correct);
                Database.TRIVIA_STORAGE.updateMemberScore(event.getUser().getIdLong(), isRight ? question.getValue() : -1 * question.getValue());
                updateMessage(question, isRight, event.getUser(), recentSentMessageID, event.getTextChannel());
                this.question = null;
            } finally {
                lock.unlock();
            }
            event.getMessage().delete().queue();
        }
    }

    private boolean testAnswer(String answer, boolean check){
        return check ? truthyRegex.matcher(answer).matches() : falsyRegex.matcher(answer).matches();
    }

    private MessageEmbed nobodyIsHere(){
        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle("You missed it ):");
        eb.setDescription(question.getQuery().length() < 2048 ? question.getQuery() : "Description too long ):");
        eb.addField(question.getValue() + " points went lost.", "The correct answer is ||`" + question.getAnswer() + "`||", false);
        eb.setColor(0x505050);
        return eb.build();
    }

    private void updateMessage(Question question, boolean correct, User user, long messageID, TextChannel textChannel){
        EmbedBuilder builder = new EmbedBuilder();
        builder.setColor(correct ? 0x20e035 : 0xe02035);
        builder.setTitle((correct ? "Correct! " : "Incorrect! "));
//        builder.setDescription(user.getAsMention() + "\n\n" + question.getQuery() + "\n\n" + "The correct answer is: `" + question.getAnswer() + "`");
        builder.setDescription(question.getQuery());
        builder.addField(String.format("You got %s%d point(s).", correct ? "" : "-", question.value), user.getAsMention() + " - the correct answer is " +
                "||`" + question.getAnswer() + "`||", false);
//        builder.setAuthor(user.getAsTag());
        textChannel.editMessageById(messageID, builder.build()).override(true).queue();
    }

    private MessageEmbed ask(){
        Question question = genQuestion();
        return askQuestion(question);
    }

    private MessageEmbed askQuestion(Question question){
        EmbedBuilder builder = new EmbedBuilder();
        builder.setColor(0x3040c0);
        builder.setTitle("Question!");
        builder.setDescription(question.getQuery());
        builder.setFooter("You can score " + question.getValue() + " point(s) by running `!t <answer>` and getting the answer correct.");
        return builder.build();
    }

    private MessageEmbed getScores(List<TriviaScore> scores, int page, int maxPages){
        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("Leaderboard");
        StringBuilder strbld = new StringBuilder();
        strbld.append("Run `!leaderboard <pagenum>` to view the scores for that page");
        strbld.append("```\n");
        strbld.append("Rank | Score | Username ");
        strbld.append("\n");
        strbld.append("-------------------------------");
        strbld.append("\n");
        for (TriviaScore tv : scores){
            strbld.append(String.format("% 4d | % 5d | %s", tv.getPosition(), tv.getScore(), tv.getUserAsTag()));
            strbld.append("\n");
        }
        strbld.append("```");
        embed.setDescription(strbld.toString());
        embed.setFooter("Page " + (page+1) + "/" + maxPages);
        return embed.build();
    }

    private Question genQuestion(){
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

    private Question getBoolAlgebra(){
        boolean invalid = true;
        BooleanAlgebra balg = null;
        int difficulty = 0, hardness = 0;
        while (invalid) {
            difficulty = randInclusive(1, 5);
            difficulty = randInclusive(1, difficulty);
            hardness = randInclusive(3, 7);
            hardness = randInclusive(2, hardness);
            balg = new BooleanAlgebra(difficulty, hardness, 0);
            if (balg.toString().length() < 2048) invalid = false;
        }
        LOGGER.info("Automatically spawned a new boolean problem with depth: " + difficulty + ", hardness:" + hardness + " and scores " + getScoreForBooleanAlgebra(balg));
        return new Question(balg.toString(), balg.getValue(), getScoreForBooleanAlgebra(balg));
    }

    private int getScoreForBooleanAlgebra(BooleanAlgebra balg){
         return Math.max(1, (int) Math.sqrt(balg.getOperations()/1.3) - 1);
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



    static int randInclusive(int lower, int upper){
        return lower + ThreadLocalRandom.current().nextInt((upper - lower) + 1);
    }

}

class BooleanAlgebra {


    //    BooleanAlgebra left;
//    BooleanAlgebra right;
    BooleanAlgebra[] nodes;
    BooleanOP stage;

    boolean value;

    boolean notted;

    BooleanAlgebra(int depth, int hardness, int currentLevel){
        this.notted = ThreadLocalRandom.current().nextInt(10) >= 9;
        if (depth < 0){
            this.value = ThreadLocalRandom.current().nextBoolean();
            return;
        }
//        this.left = new BooleanAlgebra(depth - (ThreadLocalRandom.current().nextInt(2) +1));
//        this.right = new BooleanAlgebra(depth - (ThreadLocalRandom.current().nextInt(2) +1));
        int x = ThreadLocalRandom.current().nextInt(2, Math.max(3, hardness + 2));
        nodes = new BooleanAlgebra[x];
        for (int i = 0; i < x; i++){
            nodes[i] = new BooleanAlgebra(depth - (ThreadLocalRandom.current().nextInt(Math.max(2, hardness/2)) + 1),
                    hardness - ThreadLocalRandom.current().nextInt(0, 2), currentLevel);
        }
        if (depth > 3 && currentLevel == 0){
            stage = BooleanOP.XOR;
        } else {
            stage = BooleanOP.getRand();
        }
    }

    public BufferedImage genImage(){
        if (nodes == null){
            BufferedImage bim = new BufferedImage(100, 25, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = (Graphics2D) bim.getGraphics();
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, bim.getWidth(), bim.getHeight());
            graphics.setColor(Color.BLACK);
            Font f = new Font("Courier New", Font.PLAIN, 15);
            graphics.setFont(f);
            String str = String.valueOf(value).toUpperCase();
            Rectangle2D layout = graphics.getFontMetrics(f).getStringBounds(str, graphics);
            int y = (int) (0 + ((bim.getHeight() - layout.getHeight()) / 2) + graphics.getFontMetrics(f).getAscent());
            graphics.drawString(str, 0,  y);
            if (notted){
                graphics.drawLine(0,  y - graphics.getFontMetrics(f).getAscent() + 1, (int) layout.getWidth(), y - graphics.getFontMetrics(f).getAscent() + 1);
            }

            graphics.drawLine((int) layout.getWidth(), 12, 100, 12);

//            graphics.setColor(Color.RED);
//            graphics.drawRect(0, 0, 99, 24);
            graphics.dispose();
            return bim;
        }
        List<BufferedImage> images = new ArrayList<>();
        for (BooleanAlgebra balg : nodes){
            images.add(balg.genImage());
        }
        int totalHeight = images.stream().map(BufferedImage::getHeight).mapToInt(Integer::intValue).sum();
        int maxWidth = images.stream().map(BufferedImage::getWidth).mapToInt(Integer::intValue).max().orElse(0);
        int width = 200;
        BufferedImage bim = new BufferedImage(maxWidth + width, totalHeight, BufferedImage.TYPE_INT_RGB);
        width = (int) (0.8 * width);
        Graphics2D g2 = (Graphics2D) bim.getGraphics();
        g2.setColor(Color.WHITE);
        g2.fillRect(0, 0, bim.getWidth(), bim.getHeight());
        g2.setColor(Color.BLACK);
        int yOffset = 0;
        for (BufferedImage imgs : images){
            g2.drawImage(imgs, maxWidth - imgs.getWidth(), yOffset, null);
            yOffset += imgs.getHeight();
        }
        stage.drawOp(g2, maxWidth, width , 0 + 2, totalHeight - 2);
        g2.setColor(Color.BLACK);
        g2.drawLine(maxWidth + width, totalHeight/2, bim.getWidth(), totalHeight/2);
        if (notted){
            g2.fillOval(maxWidth + width, totalHeight/2 - 4, 9, 9);
            g2.setColor(Color.WHITE);
            g2.fillOval(maxWidth + (width+1), totalHeight/2 - 3, 7, 7);
        }
        g2.dispose();
        return bim;
    }

    int getOperations(){
        int operations = 0;
        if (nodes != null){
            operations += nodes.length;
            for (BooleanAlgebra b : nodes){
                operations += b.getOperations();
            }
        }
        return operations;
    }


    boolean getValue(){
        if (nodes == null){
            return notted ^ value; // false won't change the value, and true will always flip it, so use XOR
        }
        boolean[] bools = new boolean[nodes.length];
        for (int i = 0; i < nodes.length; i++){
            bools[i] = nodes[i].getValue();
        }
        return notted ^ stage.run(bools);
    }

    @Override
    public String toString(){
        if (nodes == null){
            return (notted ? "¬" : "") + String.valueOf(value).toUpperCase();
        } else {
            StringBuilder strbld = new StringBuilder();
            if (notted) strbld.append("¬");
            strbld.append("(");
            strbld.append(nodes[0].toString());
            for (int i = 1; i < nodes.length; i++){
                strbld.append(" ").append(stage.symbol).append(" ");
                strbld.append(nodes[i].toString());
            }
            strbld.append(")");
            return strbld.toString();
        }
    }
}

enum BooleanOP {
    AND("∧", (a, b) -> a & b){
        @Override
        void drawOp(Graphics2D g2, int startX, int widthX, int startY, int heightY) {
            g2.setColor(Color.BLACK);
            //vertical line for the and
            g2.drawLine(startX, startY, startX, startY + heightY - 1);

            //curved end
            g2.drawArc(startX - widthX, startY, 2 * widthX, heightY - 1, 270, 180);
        }
    },
    OR("∨", (a, b) -> a | b) {
        @Override
        void drawOp(Graphics2D g2, int startX, int widthX, int startY, int heightY) {

            QuadCurve2D curve = new QuadCurve2D.Double(startX, startY, startX + (2*widthX/3d), startY + (heightY/10d), startX + widthX - 1, startY + heightY/2d);
            g2.draw(curve);
            curve = new QuadCurve2D.Double(startX, startY + heightY, startX + (2*widthX/3d), (startY+heightY) - (heightY/10d), startX + widthX - 1, (startY+heightY) - heightY/2d);
            g2.draw(curve);
            curve = new QuadCurve2D.Double(startX, startY, startX + widthX/2.5d, startY+(heightY/2d), startX, (startY+heightY));
            g2.draw(curve);
        }
    },
    XOR("⊕", (a, b) -> a ^ b) {
        @Override
        void drawOp(Graphics2D g2, int startX, int widthX, int startY, int heightY) {

            QuadCurve2D curve = new QuadCurve2D.Double(startX+5, startY, startX+5 + (2*widthX/3d), startY + (heightY/10d), startX + widthX - 1, startY + heightY/2d);
            g2.draw(curve);
            curve = new QuadCurve2D.Double(startX+5, startY + heightY, startX + (2*widthX/3d), (startY+heightY) - (heightY/10d), startX + widthX - 1, (startY+heightY) - heightY/2d);
            g2.draw(curve);
            curve = new QuadCurve2D.Double(startX+5, startY, startX+5 + widthX/2.5d, startY+(heightY/2d), startX+5, (startY+heightY));
            g2.draw(curve);
            curve = new QuadCurve2D.Double(startX, startY, startX + widthX/2.5d, startY+(heightY/2d), startX, (startY+heightY));
            g2.draw(curve);
        }
    },
//    IMPLIES("->", (a, b) -> !a | b)
    ;
    String symbol;
    BooleanStep func;
    BooleanOP(String symbol, BooleanStep func){
        this.symbol = symbol;
        this.func = func;
    }
    static BooleanOP getRand(){
        return values()[ThreadLocalRandom.current().nextInt(values().length)];
    }
    boolean run(boolean[] bools){
        boolean a = bools[0];
        for (int i = 1; i < bools.length; i++){
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
    int value;


    public Question(String query, Object answer, int value) {
        this.query = query;
        this.answer = answer;
        this.value = value;
    }

    public String getQuery() {
        return query;
    }

    public Object getAnswer() {
        return answer;
    }

    public int getValue(){
        return value;
    }
}
