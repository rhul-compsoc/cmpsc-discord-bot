package uk.co.hexillium.rhul.compsoc.commands.challenges;


import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.geom.QuadCurve2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

class BooleanAlgebraSolution {
    final int operationCount;
    final boolean value;
    final boolean shortCut;
    int shortCutIndex;

    public BooleanAlgebraSolution(int operationCount, boolean value, boolean shortCut, int shortCutIndex) {
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

public class BooleanAlgebra extends Challenge{


    private static final Pattern truthyRegex = Pattern.compile("(1|true|t|yes)", Pattern.CASE_INSENSITIVE);
    private static final Pattern falsyRegex = Pattern.compile("(0|false|f|no)", Pattern.CASE_INSENSITIVE);
    private int points = 0;
    boolean solved = false;

    BooleanAlgebraPart head;

    public BooleanAlgebra(int depth, int hardness){
        this.head = new BooleanAlgebraPart(depth, hardness, 0, ThreadLocalRandom.current().nextBoolean());
        calculatePoints();
    }

    @Override
    public int getSolveOperationCount() {
        return this.head.booleanAlgebraSolution.getOperationCount();
    }

    @Override
    public BufferedImage generateSolutionImage() {
        this.head.findSolution();
        this.head.markSolution();
        return this.head.genImage(true);
    }

    @Override
    public BufferedImage getImage() {
        return this.head.genImage(false);
    }

    @Override
    public int minimumSolveTimeSeconds() {
        return 30 + 40 * points;
    }

    @Override
    public boolean isValidAnswer(String answer) {
        boolean isTrue = truthyRegex.matcher(answer).matches();
        boolean isFalse = falsyRegex.matcher(answer).matches();

        return isTrue ^ isFalse;
    }

    @Override
    public int getPoints(boolean correct) {
        return (correct ? 1 : -1) * points;
    }

    public void calculatePoints(){
        this.points = Math.max(1, (int) Math.sqrt(this.head.getOperations() / 1.3) - 1);
    }


    @Override
    public String getSolution() {
        return Boolean.toString(this.head.getValue());
    }


    private boolean convertAnswer(String answer){
        return truthyRegex.matcher(answer).matches();
    }

    @Override
    public boolean isCorrectAnswer(String answer) {
        return convertAnswer(answer) == this.head.getValue();
    }


    @Override
    public String getQuestion(){
        String quest = this.head.toString();
        if (quest.length() <= 4096){
            return quest;
        }
        return this.head.toCompactString();
    }

}

class BooleanAlgebraPart {


    //    BooleanAlgebra left;
//    BooleanAlgebra right;
    BooleanAlgebraPart[] nodes;
    BooleanAlgebraSolution booleanAlgebraSolution = null;
    BooleanOP stage;

    boolean value;

    boolean notted;

    BooleanAlgebraPart(int depth, int hardness, int currentLevel) {
        this.notted = ThreadLocalRandom.current().nextInt(10) >= 9;
        if (depth < 0) {
            this.value = ThreadLocalRandom.current().nextBoolean();
            return;
        }
//        this.left = new BooleanAlgebra(depth - (ThreadLocalRandom.current().nextInt(2) +1));
//        this.right = new BooleanAlgebra(depth - (ThreadLocalRandom.current().nextInt(2) +1));
        int x = ThreadLocalRandom.current().nextInt(2, Math.max(3, hardness + 2));
        nodes = new BooleanAlgebraPart[x];
        for (int i = 0; i < x; i++) {
            nodes[i] = new BooleanAlgebraPart(depth - (ThreadLocalRandom.current().nextInt(Math.max(2, hardness / 2)) + 1),
                    hardness - ThreadLocalRandom.current().nextInt(0, 2), currentLevel + 1);
        }
        if (depth > 3 && currentLevel < 2) {
            stage = BooleanOP.XOR;
        } else {
            stage = BooleanOP.getRand();
        }
    }

    BooleanAlgebraPart(int depth, int hardness, int currentLevel, boolean requiredValue) {

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
        nodes = new BooleanAlgebraPart[x];

        //generate all except the first term, depending on the type of gate
        if (stage == BooleanOP.AND || stage == BooleanOP.OR) {
            for (int i = 1; i < x; i++) {
                nodes[i] = new BooleanAlgebraPart(depth - (ThreadLocalRandom.current().nextInt(Math.max(2, hardness / 2)) + 1),
                        hardness - ThreadLocalRandom.current().nextInt(0, 2), currentLevel + 1, stage == BooleanOP.AND);
            }
        } else {
            for (int i = 1; i < x; i++) {
                nodes[i] = new BooleanAlgebraPart(depth - (ThreadLocalRandom.current().nextInt(Math.max(2, hardness / 2)) + 1),
                        hardness - ThreadLocalRandom.current().nextInt(0, 2), currentLevel + 1, i % 2 == 0);
            }
        }

        if (stage == BooleanOP.AND || stage == BooleanOP.OR) {
            // generate the first element, this will be the deciding factor.
            nodes[0] = new BooleanAlgebraPart(depth - (ThreadLocalRandom.current().nextInt(Math.max(2, hardness / 2)) + 1),
                    hardness - ThreadLocalRandom.current().nextInt(0, 2), currentLevel + 1, requiredValue ^ notted);
        } else {
            // generate the first element, this will be the deciding factor.
            nodes[0] = new BooleanAlgebraPart(depth - (ThreadLocalRandom.current().nextInt(Math.max(2, hardness / 2)) + 1),
                    hardness - ThreadLocalRandom.current().nextInt(0, 2), currentLevel + 1, (requiredValue ^ x % 2 == 0) ^ notted);
        }


        //put it somewhere randomly in this thing...
        int swapIndex = ThreadLocalRandom.current().nextInt(x);
        BooleanAlgebraPart temp = nodes[swapIndex];
        nodes[swapIndex] = nodes[0];
        nodes[0] = temp;
    }

    public void markSolution() {
        markSolution(null, 0);
    }

    private void markSolution(BooleanAlgebraPart parent, int childIndex) {
        //the presence of the solution in the parent implies it is needed
        if (this.nodes == null) {
            if (parent == null) { //if this is the top-level, then leave it alone
                return;
            }
            if (parent.booleanAlgebraSolution == null) {
                this.booleanAlgebraSolution = null;
                return;
            }
            int shortCut = parent.booleanAlgebraSolution.shortCutIndex;
            boolean markThis = shortCut < 0 || shortCut == childIndex;
            if (!markThis) this.booleanAlgebraSolution = null;
            return;
        }
        if (parent != null && parent.booleanAlgebraSolution == null) {
            this.booleanAlgebraSolution = null;
            BooleanAlgebraPart[] booleanAlgebras = this.nodes;
            for (int i = 0; i < booleanAlgebras.length; i++) {
                booleanAlgebras[i].markSolution(this, i);
            }
            return;
        }
        if (parent != null) {
            int shortCut = parent.booleanAlgebraSolution.shortCutIndex;
            boolean markThis = shortCut < 0 || shortCut == childIndex;
            if (!markThis) this.booleanAlgebraSolution = null;
        }
        for (int i = 0; i < this.nodes.length; i++) {
            nodes[i].markSolution(this, i);
        }
    }

    public void findSolution() {
        this.solve();
    }



    private BooleanAlgebraSolution solve() {
        if (nodes == null || nodes.length == 0) {
            BooleanAlgebraSolution s = new BooleanAlgebraSolution((this.notted ? 2 : 1), this.value ^ this.notted, false, -1);
            this.booleanAlgebraSolution = s;
            return s;
        }
        List<BooleanAlgebraSolution> solveChildren = new ArrayList<>(nodes.length);
        for (int i = 0; i < nodes.length; i++) {
            BooleanAlgebraPart balg = nodes[i];
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
                        BooleanAlgebraSolution solveChild = solveChildren.get(i);
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
                    operations = solveChildren.stream().map(BooleanAlgebraSolution::getOperationCount).mapToInt(i -> i).sum() + solveChildren.size();
                }
                break;
            case AND:
                if (!value ^ this.notted) { //need to find one false
                    boolean seen = false;
                    int best = 0;
                    int index = 0, bestIndex = 0;
                    for (int i = 0; i < solveChildren.size(); i++) {
                        BooleanAlgebraSolution booleanAlgebraSolution = solveChildren.get(i);
                        if (!booleanAlgebraSolution.getValue()) {
                            int i1 = booleanAlgebraSolution.getOperationCount();
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
                    operations = solveChildren.stream().map(BooleanAlgebraSolution::getOperationCount).mapToInt(i -> i).sum() + solveChildren.size();
                }
                break;
            case XOR:
                operations = solveChildren.stream().map(BooleanAlgebraSolution::getOperationCount).mapToInt(i -> i).sum() + solveChildren.size();
        }
        BooleanAlgebraSolution s = new BooleanAlgebraSolution(operations, value, shortCutIndex >= 0, shortCutIndex);
        this.booleanAlgebraSolution = s;
        return s;
    }


    public BufferedImage genImage(boolean drawSolution) {
        if (nodes == null) {
            BufferedImage bim = new BufferedImage(100, 25, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = (Graphics2D) bim.getGraphics();
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, bim.getWidth(), bim.getHeight());
            if (drawSolution) {
                if (this.booleanAlgebraSolution == null) {
                    graphics.setColor(new Color(175, 175, 175));
                } else {
                    graphics.setColor(this.booleanAlgebraSolution.getValue() ? new Color(40, 170, 40) : new Color(170, 40, 40));
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
        for (BooleanAlgebraPart balg : nodes) {
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
            if (this.booleanAlgebraSolution == null) {
                g2.setColor(new Color(175, 175, 175));
            } else {
                g2.setColor(this.booleanAlgebraSolution.getValue() ? new Color(40, 170, 40) : new Color(170, 40, 40));
            }
        } else {
            g2.setColor(Color.BLACK);
        }
        float strokeWidth = 0;
        if (drawSolution) {
            if (this.booleanAlgebraSolution == null) {
                strokeWidth = width < 100 ? 1 : width / 200f;
            } else {
                strokeWidth = width < 100 ? 1 : width / 50f;
            }
        } else {
            strokeWidth = width < 100 ? 1 : width / 100f;
        }
        g2.setStroke(new BasicStroke(strokeWidth));
        stage.drawOp(g2, maxWidth, width, 0 + 2, totalHeight - 2);
//        g2.setColor(Color.BLACK);
        g2.drawLine(maxWidth + width, (totalHeight / 2) + 1, bim.getWidth(), (totalHeight / 2) + 1);
        if (notted) {
            int notSize = (int) (2 * Math.max(5d, width / 10d));
            g2.fillOval(maxWidth + width, totalHeight / 2 - (notSize / 2), notSize, notSize);
            g2.setColor(Color.WHITE);
            g2.fillOval(
                    (int) (maxWidth + (width + (strokeWidth/2)) + 1),
                    (int) ((totalHeight / 2) - (notSize / 2) + (strokeWidth/2) + 1),
                    (int) ((notSize) - (strokeWidth + 1)),
                    (int) ((notSize) - (strokeWidth + 1)));
        }
        g2.dispose();
        return bim;
    }

    int getOperations() {
        int operations = 0;
        if (nodes != null) {
            operations += nodes.length;
            for (BooleanAlgebraPart b : nodes) {
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
            //vertical line for the and
            g2.drawLine(startX, startY, startX, startY + heightY - 1);
            //extensions
            g2.drawLine(startX, startY, startX + widthX / 3, startY);
            g2.drawLine(startX, startY + heightY - 1, startX + widthX / 3, startY + heightY - 1);
            //curved end
            g2.drawArc((startX - (2 * widthX / 3)), startY, (2 * widthX) - (widthX / 3), heightY - 1, 270, 180);

            g2.setStroke(new BasicStroke(1));
        }
    },
    OR("∨", (a, b) -> a | b) {
        @Override
        void drawOp(Graphics2D g2, int startX, int widthX, int startY, int heightY) {
            QuadCurve2D curve = new QuadCurve2D.Double(startX, startY, startX + (2 * widthX / 3d), startY + (heightY / 10d), startX + widthX - 1, startY + heightY / 2d);
            g2.draw(curve);
            curve = new QuadCurve2D.Double(startX, startY + heightY, startX + (2 * widthX / 3d), (startY + heightY) - (heightY / 10d), startX + widthX - 1, (startY + heightY) - heightY / 2d);
            g2.draw(curve);
            curve = new QuadCurve2D.Double(startX, startY, startX + widthX / 2.5d, startY + (heightY / 2d), startX, (startY + heightY));
            g2.draw(curve);
            g2.setStroke(new BasicStroke(1));

        }
    },
    XOR("⊕", (a, b) -> a ^ b) {
        @Override
        void drawOp(Graphics2D g2, int startX, int widthX, int startY, int heightY) {
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

