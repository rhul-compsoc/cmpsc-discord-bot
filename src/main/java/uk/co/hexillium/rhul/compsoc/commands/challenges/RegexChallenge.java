package uk.co.hexillium.rhul.compsoc.commands.challenges;

import com.google.re2j.Matcher;
import com.google.re2j.Pattern;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import uk.co.hexillium.rhul.compsoc.commands.Trivia;

import java.awt.image.BufferedImage;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public class RegexChallenge extends Challenge {


    static class RegexSettings {
        public static final int MAX_REPEATS = 5;
    }

    final static int MAX_QUESTION_RESPONSES = 3;
    final static char[] validChars = ("ABCDEFGH" + /* "I" + */ "JK" + /* "L" + */ "MN" + /* "O" + */ "PQRSTUVWXYZ23456789" /* + "\\.*$^(){}[]"*/).toCharArray();
    final static HashSet<Character> validCharSet = new HashSet<>();
    static {
        for (char c : validChars) {
            validCharSet.add(c);
        }
    }
    final static Pattern answerValidPattern = Pattern.compile("(?:\\w\\s?,?\\s?)+", Pattern.CASE_INSENSITIVE);
    final static Pattern answerCorrectPattern = Pattern.compile("(\\w)", Pattern.CASE_INSENSITIVE);
    final static char ZWS = '\u200B';


    private final String representation;
    private final List<String> responses;
    private final long correctResponses;
    private final RegexGroup root;
    public static char genRandomChar() {
        return validChars[ThreadLocalRandom.current().nextInt(validChars.length)];
    }

    public static void main(String[] args) {
    }

    public RegexChallenge(int depth, int hardness) {

        root = new RegexGroup(depth, hardness, 0);
        this.representation = root.getRepresentation();

        Pattern pattern = Pattern.compile(representation);
        Matcher matcher;

        List<String> question = new ArrayList<>();

        int numCorrect = ThreadLocalRandom.current().nextInt(1, MAX_QUESTION_RESPONSES + 1);
        int numIncorrect = 3 - numCorrect;

        for (int i = 0; i < numCorrect; i++) {
            String ans1 = root.generateAnswer(true);
            boolean matches = false;
            while (!matches){
                ans1 = root.generateAnswer(true);
                matcher = pattern.matcher(ans1);
                matches = matcher.matches();
            }
            question.add(ans1);
        }

        for (int i = 0; i < numIncorrect; i++) {
            String ans1 = root.generateAnswer(false);
            boolean matches = true;
            while (matches){
                ans1 = root.generateAnswer(false);
                matcher = pattern.matcher(ans1);
                matches = matcher.matches();
            }
            question.add(ans1);
        }

        BitSet set = new BitSet(MAX_QUESTION_RESPONSES);
        set.set(0, numCorrect, true);

        for (int i = 0; i < question.size(); i++){
            int selectionIndex = ThreadLocalRandom.current().nextInt(i, question.size());

            String temp = question.get(i);
            question.set(i, question.get(selectionIndex));
            question.set(selectionIndex, temp);

            boolean tempCorr = set.get(i);
            set.set(i, set.get(selectionIndex));
            set.set(selectionIndex, tempCorr);

        }

        long l = set.stream()
                .takeWhile(i -> i < Long.SIZE)
                .mapToLong(i -> 1L << i)
                .reduce(0, (f, b) -> f | b);

        this.correctResponses = l;
        this.responses = question;

        System.out.println(question);
        System.out.println(Long.toBinaryString(l));
    }

    @Override
    public BufferedImage getImage() {
        return null;
    }

    @Override
    public String getQuestion() {
        StringBuilder answers = new StringBuilder();
        for (int i = 0; i < this.responses.size(); i++){
            answers.append(validChars[i])
                    .append(": `")
                    .append(cleanOptionForPresenting(this.responses.get(i)))
                    .append("`\n");
        }
        return """
                Which of the strings below are matched by this regex?
                Partial points are awarded for mostly correct answers.  At least one option is matched by this regex.```regex
                %s```\
                %s"""
                .formatted(this.representation, answers.toString());
    }

    @Override
    public int getPoints(boolean correct) {
        return 0; //todo decide on points; zero whilst testing
    }

    @Override
    public boolean isValidAnswer(String answer) {
        // answers as a case-insensitive unordered list
        return answerValidPattern.matches(answer);
    }

    public static final char REGIONAL_BASE = '\uD83C';
    public static final char REGIONAL_A = '\uDDE6';

    @Override
    public Collection<ActionRow> getReactionRow() {
        var selectMenu = StringSelectMenu.create(Trivia.buttonPrefix[1] + "|sm");
        for (int i = 0; i < this.responses.size(); i++){
            char c = validChars[i];
            String content = this.responses.get(i);
            selectMenu.addOption(
                    c + ": " + content.substring(0, Math.min(content.length(),97)),
                    c + "", "Add " + c + " to the list of matches",
                    Emoji.fromUnicode(REGIONAL_BASE + "" + (char) ((short) REGIONAL_A + i)));
        }
        selectMenu.setMaxValues(this.responses.size());
        return List.of(ActionRow.of(selectMenu.build()));
    }

    @Override
    public boolean isCorrectAnswer(String answer) {
        return getCorrectCount(answer) == this.responses.size();
    }

    private int getCorrectCount(String answer){
        List<Character> letters = new ArrayList<>();
        Matcher m = answerCorrectPattern.matcher(answer);
        while (m.find()){
            letters.add(m.group().charAt(0));
        }
        int correctCount = 0;
        for (int i = 0; i < this.responses.size(); i++){
            boolean matches = (this.correctResponses & (1L << i)) > 0;
            boolean wasPresented = letters.contains(validChars[i]);
            if (matches ^ !wasPresented)
                correctCount++;
        }
        return correctCount;
    }

    private static String cleanOptionForPresenting(String str){
        var strbld = new StringBuilder(str);
        if (str.length() > 4)
           strbld.insert(str.length()-ThreadLocalRandom.current().nextInt(str.length()-2)-1, ZWS);
        return strbld.toString().replace("\u0000", "");
    }

    @Override
    public String getSolution() {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < this.responses.size(); i++){
            boolean matches = (this.correctResponses & (1L << i)) > 0;
            if (matches)
                builder.append(validChars[i]);
        }
        return builder.toString();
    }

    @Override
    public BufferedImage generateSolutionImage() {
        return null;
    }

    @Override
    public int minimumSolveTimeSeconds() {
        return 120;
    }

    @Override
    public int getSolveOperationCount() {
        return 0;
    }

    @Override
    public String getDebugInformation() {
        return root.getDebugString();
    }
}

abstract class RegexNode {
    RegexModifier modifier;

    String generateAnswer(boolean correct){
        StringBuilder bld = new StringBuilder();
        int num = this.modifier.genValidRandomRepeats();
        boolean genCorrectly = true;
        if (!correct) {
            int incorrect = this.modifier.genInvalidRandomRepeats();
            if (incorrect < 0 || ThreadLocalRandom.current().nextBoolean()) {
                genCorrectly = false;
            } else {
                num = incorrect;
            }
        }

        //if genCorrectly is false, then pick excatly one of the children to generate incorrectly rather than all of them
        int j = -1;
        if (!genCorrectly){
            j = Trivia.randInclusive(0, num);
        }
        for (int i = 0; i < num; i++) {
            j--;
            bld.append(generateSingleAnswer(j != 0));
        }
        if (!correct){
            bld.append("\u0000");
        }
        return bld.toString();
    }

    abstract String getRepresentation();

    abstract String getDebugString();

    abstract String generateSingleAnswer(boolean correct);
}

class RegexGroup extends RegexNode {
    RegexNode[] nodes;

    RegexGroup(int remainingDepth, int difficulty, int currentDepth) {
        int size = 1 + ThreadLocalRandom.current().nextInt(difficulty + Math.max(0, 5 - currentDepth));
        nodes = new RegexNode[size];
        if (currentDepth == 0) {
            this.modifier = RegexModifier.NONE();
        } else {
            this.modifier = RegexModifier.getRandom(true);
        }
        for (int i = 0; i < size; i++) {
            boolean isNestedGroup = (ThreadLocalRandom.current().nextInt(1 + difficulty * Math.max(remainingDepth, 0)) > ThreadLocalRandom.current().nextInt(5));
            if (isNestedGroup) {
                if (ThreadLocalRandom.current().nextInt(3) > 0) { //2 in 3
                    nodes[i] = new RegexGroup(remainingDepth - 1, difficulty, currentDepth + 1);
                } else {
                    nodes[i] = new RegexOrCluster(remainingDepth - 1, difficulty, currentDepth + 1);
                }
            } else {
                if (ThreadLocalRandom.current().nextInt(3) > 0) { //2 in 3
                    nodes[i] = new RegexLiteral();
                } else {
                    nodes[i] = new RegexCharacterClass(difficulty);
                }
            }
        }
    }

    String generateSingleAnswer(boolean correct){
        StringBuilder bld = new StringBuilder();
        if (correct) {
            for (RegexNode node : nodes) {
                bld.append(node.generateAnswer(correct));
            }
        } else {
            int incorrect = ThreadLocalRandom.current().nextInt(nodes.length);
            for (int i = 0; i < nodes.length; i++) {
                RegexNode node = nodes[i];
                bld.append(node.generateAnswer(i != incorrect));
            }
        }
        return bld.toString();
    }

    @Override
    String getRepresentation() {
        StringBuilder bld = new StringBuilder();
        if (this.modifier.type != RegexModifier.RegexModifierType.NONE) {
            bld.append("(");
        }
        for (RegexNode node : nodes) {
            bld.append(node.getRepresentation());
        }
        if (this.modifier.type != RegexModifier.RegexModifierType.NONE) {
            bld.append(")");
            bld.append(modifier.getRepr());
        }
        return bld.toString();
    }

    String getDebugString() {
        return "RegexGroup:{modifier=" + modifier.getDebugString() +
                ",nodes=" + Arrays.stream(nodes).map(RegexNode::getDebugString).collect(Collectors.toList()) + "}";
    }
}

class RegexOrCluster extends RegexNode {

    RegexGroup[] groups;

    RegexOrCluster(int remainingDepth, int difficulty, int currentDepth) {
        int size = 2;//+ ThreadLocalRandom.current().nextInt(difficulty + 1);
        groups = new RegexGroup[size];
        this.modifier = RegexModifier.getRandom(true);
        for (int i = 0; i < size; i++) {
            groups[i] = new RegexGroup(remainingDepth - 1, difficulty, currentDepth + 1);
        }
    }

    @Override
    String generateSingleAnswer(boolean correct) {
        //delegate this off to a random child to process
        return groups[ThreadLocalRandom.current().nextInt(groups.length)].generateAnswer(correct);
    }

    @Override
    String getRepresentation() {
        StringBuilder bld = new StringBuilder();
        boolean displayModifier = this.modifier.type != RegexModifier.RegexModifierType.NONE;
        if (this.groups.length > 1) {
            bld.append("(");
        }
        for (RegexNode group : groups) {
            bld.append(group.getRepresentation());
            bld.append("|");
        }
        bld.deleteCharAt(bld.length() - 1); //remove the trailing pipe
        if (this.groups.length > 1) {
            bld.append(")");
        }
        if (displayModifier) {
            bld.append(this.modifier.getRepr());
        }
        return bld.toString();
    }

    String getDebugString() {
        return "RegexOrCluster:{modifier=" + modifier.getDebugString() + ",groups=" + Arrays.stream(groups).map(RegexNode::getDebugString).toList() + "}";
    }
}

class RegexCharacterClass extends RegexNode {
    char[] charactersDisplay;
    char[] validChars;
    char[] invalidChars;
    boolean notted;

    RegexCharacterClass(int difficulty) {
        int length = 3 + ThreadLocalRandom.current().nextInt(difficulty * 3);
        char[] valid = new char[length];
        char[] possibleChars = new char[RegexChallenge.validChars.length];
        System.arraycopy(RegexChallenge.validChars, 0, possibleChars, 0, RegexChallenge.validChars.length);
        for (int i = possibleChars.length - 1; i >= 0; i--) {
            char temp = possibleChars[i];
            int swapIndex = ThreadLocalRandom.current().nextInt(i + 1);
            possibleChars[i] = possibleChars[swapIndex];
            possibleChars[swapIndex] = temp;
        }
        System.arraycopy(possibleChars, 0, valid, 0, length);
        notted = ThreadLocalRandom.current().nextInt(3) > 0;

        HashSet<Character> inversion = null;
        inversion = new HashSet<>(RegexChallenge.validCharSet);
        charactersDisplay = new char[valid.length];
        for (int i = 0; i < valid.length; i++) {
            char c = valid[i];
            inversion.remove(c);
            charactersDisplay[i] = c;
        }
        if (notted) {
            validChars = new char[inversion.size()];
            int i = 0;
            for (char c : inversion) {
                validChars[i] = c;
                i++;
            }
            this.invalidChars = new char[charactersDisplay.length];
            System.arraycopy(charactersDisplay, 0, this.invalidChars, 0, charactersDisplay.length);
        } else {
            validChars = charactersDisplay;
            this.invalidChars = new char[inversion.size()];
            int it = 0;
            for (Character c : inversion) {
                this.invalidChars[it++] = c;
            }
        }
        this.modifier = RegexModifier.getRandom(true);
    }

    String generateSingleAnswer(boolean correct) {
        if (correct) {
            return String.valueOf(validChars[ThreadLocalRandom.current().nextInt(validChars.length)]);
        } else {
            return String.valueOf(invalidChars[ThreadLocalRandom.current().nextInt(invalidChars.length)]);
        }
    }

    @Override
    String getRepresentation() {
        StringBuilder bld = new StringBuilder();
        bld.append("[");
        if (notted) {
            bld.append("^");
        }
        for (char c : charactersDisplay) {
            if (!Character.isLetterOrDigit(c)) {
                bld.append("\\").append(c);
            } else {
                bld.append(c);
            }
        }
        bld.append("]");
        bld.append(this.modifier.getRepr());
        return bld.toString();
    }

    String getDebugString() {
        return "RegexCharClass:{modifier=" + modifier.getDebugString() +
                ",display=" + Arrays.toString(charactersDisplay) +
                ",valid=" + Arrays.toString(validChars) +
                ",notted=" + notted + "}";
    }
}

class RegexLiteral extends RegexNode {
    char character;

    public RegexLiteral(char c) {
        character = c;
        this.modifier = RegexModifier.getRandom(true);
    }

    public RegexLiteral() {
        this(RegexChallenge.genRandomChar());
    }

    @Override
    public String getRepresentation() {
        if (!Character.isLetterOrDigit(character)) {
            return "\\" + character + this.modifier.getRepr();
        } else {
            return character + this.modifier.getRepr();
        }
    }

    @Override
    String generateSingleAnswer(boolean correct) {
        if (!correct){
            char next = RegexChallenge.genRandomChar();
            while (next == character){
                next = RegexChallenge.genRandomChar();
            }
            return String.valueOf(next);
        }
        return String.valueOf(character);
    }

    private char genAnotherSubstituteChar() {
        int randNum = ThreadLocalRandom.current().nextInt(RegexChallenge.validChars.length - 1);
        char randChar = RegexChallenge.validChars[randNum];
        if (randChar == this.character) {
            randChar = RegexChallenge.validChars[RegexChallenge.validChars.length - 1];
        }
        return randChar;
    }

    public char getChar() {
        return character;
    }

    String getDebugString() {
        return "RegexLiteral:{modifier=" + modifier.getDebugString() + ",char='" + character + "'}";
    }
}

class RegexModifier {

    public enum RegexModifierType {
        NONE("", 1, 1),
        ONE_OR_MORE("+", 1, RegexChallenge.RegexSettings.MAX_REPEATS),
        ZERO_OR_MORE("*", 0, RegexChallenge.RegexSettings.MAX_REPEATS),
        MANY("{n,m}", 0, 0),
        OPTIONAL("?", 0, 1);

        final String repr;
        final int min;
        final int max;

        RegexModifierType(String repr, int defaultMin, int defaultMax) {
            this.repr = repr;
            this.min = defaultMin;
            this.max = defaultMax;
        }
    }

    RegexModifierType type;
    int min, max;
    String repr;

    private RegexModifier(RegexModifierType type, int min, int max, String repr) {
        this.type = type;
        this.min = min;
        this.max = max;
        this.repr = repr;
    }

    private RegexModifier(RegexModifierType type) {
        this(type, type.min, type.max, type.repr);
    }

    public static RegexModifier NONE() {
        return new RegexModifier(RegexModifierType.NONE);
    }

    public static RegexModifier MANY(int min, int max) {
        StringBuilder repr = new StringBuilder();
        repr.append("{");
        boolean upperBound = max < RegexChallenge.RegexSettings.MAX_REPEATS;
        boolean lowerBound = min > 0;
        //if (lowerBound || !upperBound || min == max) {
        repr.append(min);
        //}
        if (min != max) {
            repr.append(",");
            if (upperBound) {
                repr.append(max);
            }
        }
        repr.append("}");
        return new RegexModifier(RegexModifierType.MANY, min, max, repr.toString());
    }

    public static RegexModifier OPTIONAL() {
        return new RegexModifier(RegexModifierType.OPTIONAL);
    }

    public static RegexModifier ONE_OR_MORE() {
        return new RegexModifier(RegexModifierType.ONE_OR_MORE);
    }

    public static RegexModifier ZERO_OR_MORE() {
        return new RegexModifier(RegexModifierType.ZERO_OR_MORE);
    }

    public static RegexModifier getRandom(boolean includeNone) {
        int choice = ThreadLocalRandom.current().nextInt(includeNone ? 5 : 4);
        switch (choice) {
            case 0:
                int min = ThreadLocalRandom.current().nextInt(RegexChallenge.RegexSettings.MAX_REPEATS + 1);
                min = ThreadLocalRandom.current().nextInt(Math.max(min, 1)); //run it again, to weight it lower
                int max = ThreadLocalRandom.current().nextInt(min, RegexChallenge.RegexSettings.MAX_REPEATS + 1);
                return MANY(min, max);
            case 1:
                return OPTIONAL();
            case 2:
                return ONE_OR_MORE();
            case 3:
                return ZERO_OR_MORE();
            case 4:
                return NONE();
        }
        return NONE();
    }

    public int genValidRandomRepeats() {
        return min + ThreadLocalRandom.current().nextInt((max - min) + 1);
    }

    public int genInvalidRandomRepeats() {
        switch (type) {
            case ZERO_OR_MORE:
                return -1; //this is a signal to the caller that this is an impossible task
            case OPTIONAL:
                return ThreadLocalRandom.current().nextInt(2, RegexChallenge.RegexSettings.MAX_REPEATS + 1);
            case NONE:
                if (ThreadLocalRandom.current().nextInt(0, RegexChallenge.RegexSettings.MAX_REPEATS) == 0) {
                    return 0;
                } else {
                    return ThreadLocalRandom.current().nextInt(2, RegexChallenge.RegexSettings.MAX_REPEATS + 1);
                }
            case ONE_OR_MORE:
                return 0;
        }
        int minSlots = min;
        int maxSlots = RegexChallenge.RegexSettings.MAX_REPEATS - max;

        if (minSlots == 0 && maxSlots == 0) {
            return -1;
        }
        boolean pickMin = false;

        if (minSlots == 0 ^ maxSlots == 0) {
            pickMin = minSlots > 0;
        } else {
            pickMin = ThreadLocalRandom.current().nextBoolean();
        }
        if (pickMin) {
            return ThreadLocalRandom.current().nextInt(minSlots + 1);
        } else {
            return ThreadLocalRandom.current().nextInt(maxSlots + 1) + max;
        }
    }

    public String getRepr() {
        return repr;
    }

    String getDebugString() {
        return "RegexModifier:{type=" + this.type.name() + ",min=" + min + ",max=" + max + "}";
    }

}


