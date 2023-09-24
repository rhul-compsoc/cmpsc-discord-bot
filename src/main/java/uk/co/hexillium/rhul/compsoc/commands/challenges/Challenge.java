package uk.co.hexillium.rhul.compsoc.commands.challenges;

import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.utils.FileUpload;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

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
    public Collection<ActionRow> getReactionRow(){
        return Collections.emptyList();
    }
    public int pointsForAnswer(String answer){
        return getPoints(isCorrectAnswer(answer));
    }
    public final List<? extends FileUpload> getFileUploads(){
        List<FileUpload> files = List.of();
        BufferedImage im = getImage();
        if (im != null){
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            try {
                ImageIO.write(im, "png", os);
            } catch (IOException ex) {
                return List.of();
            }
            files = List.of(FileUpload.fromData(os.toByteArray(), "image.png"));
        }
        return files;
    }
}
