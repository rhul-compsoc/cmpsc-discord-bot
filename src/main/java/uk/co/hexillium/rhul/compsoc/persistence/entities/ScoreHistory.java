package uk.co.hexillium.rhul.compsoc.persistence.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.jfree.data.time.Day;

import java.time.OffsetDateTime;

public class ScoreHistory {

    long userId, guildId;
    int seasonId;
    String username;

    OffsetDateTime[] times;
    int[] scores;

    public ScoreHistory(long userId, String username, long guildId, int seasonId, OffsetDateTime[] times, int[] scores) {
        this.userId = userId;
        this.username = username;
        this.guildId = guildId;
        this.seasonId = seasonId;
        this.times = times;
        this.scores = scores;
    }

    public long getUserId() {
        return userId;
    }

    public String getUsername(){
        return username;
    }

    public long getGuildId() {
        return guildId;
    }

    public int getSeasonId() {
        return seasonId;
    }

    public OffsetDateTime[] getTimes() {
        return times;
    }

    public int[] getScores() {
        return scores;
    }

    @JsonIgnore
    public Day[] getDays(){
        Day[] days = new Day[times.length];
        for (int i = 0; i < times.length; i++) {
            OffsetDateTime time = times[i];
            days[i] = new Day(time.getDayOfMonth(), time.getMonthValue(), time.getYear());
        }
        return days;
    }
}
