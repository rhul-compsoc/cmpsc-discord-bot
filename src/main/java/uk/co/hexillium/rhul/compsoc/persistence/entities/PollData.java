package uk.co.hexillium.rhul.compsoc.persistence.entities;

import org.apache.logging.log4j.util.Strings;
import uk.co.hexillium.rhul.compsoc.commands.Poll;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PollData {

    int id;
    String[] options;
    OffsetDateTime started;
    boolean finished;
    OffsetDateTime expires;
    int max_options;

    String name;
    String description;

    long channelId;
    long guildId;
    long messageId;

    transient PollSelection[] selections;
    transient int[] votes;

    public PollData(int id, String[] options, OffsetDateTime started, boolean finished, OffsetDateTime expires,
                    int max_options, PollSelection[] selections, String name, String description,
                    long channelId, long guildId, long messageId) {
        this.id = id;
        this.options = options;
        this.started = started;
        this.finished = finished;
        this.expires = expires;
        this.max_options = max_options;
        this.selections = selections;
        this.name = name;
        this.description = description;
        this.channelId = channelId;
        this.guildId = guildId;
        this.messageId = messageId;
    }

    public int getId() {
        return id;
    }

    public String[] getOptions() {
        return options;
    }

    public OffsetDateTime getStarted() {
        return started;
    }

    public boolean isFinished() {
        return finished;
    }

    public OffsetDateTime getExpires() {
        return expires;
    }

    public int getMax_options() {
        return max_options;
    }

    public PollSelection[] getSelections() {
        return selections;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getOption(int i){
        return options[i];
    }

    public long getChannelId() {
        return channelId;
    }

    public long getGuildId() {
        return guildId;
    }

    public long getMessageId() {
        return messageId;
    }

    public int[] calculateVoteCounts(){
        if (votes != null){
            return votes;
        }
        if (selections == null){
            return new int[0];
        }
        int[] counts = new int[options.length];
        for (PollSelection member : selections){
//            for (int i : member.getChoices()){
//                counts[i]++;
//            }
            int choices = member.getChoices();
            for (int i = 0; i <= options.length; i++){
                if ((choices & (1 << i)) == (1<<i)){
                    counts[i]++;
                }
            }
        }
        votes = counts;
        return counts;
    }

    public void setSelections(PollSelection[] selections) {
        this.selections = selections;
    }

    public String getVotesAsGraph(){
        int[] votes = calculateVoteCounts();
        if (votes == null || votes.length == 0){
            return "(unfetched)\n";
        }
        List<String> lines = new ArrayList<>();
        //we need to know what the maximum number is
        int highest = Arrays.stream(votes).max().orElse(0);
        if (highest == 0){
            for (int i = 0; i < votes.length; i++) {
                lines.add(String.format("%d) %5.1f%% %s  %d", i,  0.0, "" , 0));
            }
            return Strings.join(lines, '\n');
        }
        //do we even need to? we need to know the percentage, though
        double total = Arrays.stream(votes).sum();
        double[] percentages = Arrays.stream(votes).mapToDouble(i -> i / total).toArray();
        double scale = (total/highest) * 1.5;
        for (int i = 0, percentagesLength = percentages.length; i < percentagesLength; i++) {
            double percentage = percentages[i] * 100;
            lines.add(String.format("%d) %5.1f%% %s  %d", i, percentage, Poll.generateProgressBar(percentage * scale), votes[i]));
        }
        return Strings.join(lines, '\n');
    }

    public String getStatsAsString(){
        int[] votes = calculateVoteCounts();

        if (votes == null || selections == null){
            return "No stats yet";
        }
        int total = Arrays.stream(votes).sum();
        int participants = (int) Arrays.stream(selections).filter(selection -> selection.getChoices() > 0).count();
        double averageVotes = total / (double) participants;

        return String.format("Total votes: %d" +
                "%nNumber of participants: %d" +
                "%nAverage votes per participant: %.2f",
                total, participants, averageVotes);
    }
    //maybe add interesting stats like "most of the single votes were for A" or "A, B and E were often voted for together"
}
