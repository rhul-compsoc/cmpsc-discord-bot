package uk.co.hexillium.rhul.compsoc.time;

import net.dv8tion.jda.api.JDA;
import uk.co.hexillium.rhul.compsoc.persistence.Database;

public class GenericJob extends Job{

    public GenericJob(long jobID, long initiatedEpoch, long targetEpoch, int jobType) {
        super(jobID, initiatedEpoch, targetEpoch, jobType);
    }

    public void run(JDA jda, Database database) {
        throw new RuntimeException("GenericJob does not have tasks.");
    }
}
