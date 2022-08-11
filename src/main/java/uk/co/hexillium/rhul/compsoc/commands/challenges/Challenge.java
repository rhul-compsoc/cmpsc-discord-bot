package uk.co.hexillium.rhul.compsoc.commands.challenges;

import java.awt.image.BufferedImage;

public abstract class Challenge {

    public abstract BufferedImage getImage();
    public abstract String getQuestion();
    public abstract int getPoints(boolean correct);
    public abstract boolean isValidAnswer(String answer);
    public abstract boolean isCorrectAnswer(String answer);
    public abstract String getSolution();
    public abstract BufferedImage generateSolutionImage();
    public abstract int minimumSolveTimeSeconds();
    public abstract int getSolveOperationCount();
    public abstract String getDebugInformation();
}
