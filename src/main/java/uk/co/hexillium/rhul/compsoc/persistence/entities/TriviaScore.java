package uk.co.hexillium.rhul.compsoc.persistence.entities;

public class TriviaScore {

    private String userAsTag;
    private int score;
    private int position;

    public TriviaScore(String userAsTag, int score, int position) {
        this.userAsTag = userAsTag;
        this.score = score;
        this.position = position;
    }

    public String getUserAsTag() {
        return userAsTag;
    }

    public int getScore() {
        return score;
    }

    public int getPosition() {
        return position;
    }
}
