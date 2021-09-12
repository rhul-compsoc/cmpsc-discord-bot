package uk.co.hexillium.rhul.compsoc.persistence.entities;

import java.time.OffsetDateTime;

public class PollSelection {

    int poll_id;
    long member_id;
    int choices; //the zero-based index of the selected options bitset
    OffsetDateTime time;

    public PollSelection(int poll_id, long member_id, int choices, OffsetDateTime time) {
        this.poll_id = poll_id;
        this.member_id = member_id;
        this.choices = choices;
        this.time = time;
    }

    public int getPollId() {
        return poll_id;
    }

    public long getMemberId() {
        return member_id;
    }

    public int getChoices() {
        return choices;
    }

    public OffsetDateTime getTime() {
        return time;
    }
}
