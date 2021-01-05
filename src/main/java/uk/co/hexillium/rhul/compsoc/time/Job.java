package uk.co.hexillium.rhul.compsoc.time;

import net.dv8tion.jda.api.JDA;
import uk.co.hexillium.rhul.compsoc.persistence.Database;

public abstract class Job {

    long jobID;
    long initiatedEpoch;
    int jobType;
    long targetEpoch;

    public Job(long jobID, long initiatedEpoch, long targetEpoch, int jobType) {
        this.jobID = jobID;
        this.initiatedEpoch = initiatedEpoch;
        this.jobType = jobType;
        this.targetEpoch = targetEpoch;
    }

    protected abstract void run(JDA jda, Database database);

    public long getJobID() {
        return jobID;
    }

    public int getJobType() {
        return jobType;
    }

    public long getTargetEpoch() {
        return targetEpoch;
    }

    public long getInitiatedEpoch() {
        return initiatedEpoch;
    }
}
