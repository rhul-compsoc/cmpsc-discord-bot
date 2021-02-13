package uk.co.hexillium.rhul.compsoc.persistence.entities;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;

public class TriviaScore {

    private String userAsTag;
    private long memberID;
    private int score;
    private int position;

    @JsonCreator
    public TriviaScore(String userAsTag, int score, int position) {
        this.userAsTag = userAsTag;
        this.score = score;
        this.position = position;
    }

    public TriviaScore(long memberID, String userAsTag, int score, int position) {
        this(userAsTag, score, position);
        this.memberID = memberID;
    }

    @JsonIgnore
    public long getMemberId(){
        return memberID;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TriviaScore that = (TriviaScore) o;

        if (memberID != that.memberID) return false;
        if (score != that.score) return false;
        if (position != that.position) return false;
        return userAsTag.equals(that.userAsTag);
    }

    @Override
    public int hashCode() {
        int result = userAsTag.hashCode();
        result = 31 * result + (int) (memberID ^ (memberID >>> 32));
        result = 31 * result + score;
        result = 31 * result + position;
        return result;
    }
}
