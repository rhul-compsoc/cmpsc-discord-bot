package uk.co.hexillium.rhul.compsoc.persistence.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import net.dv8tion.jda.api.utils.data.DataObject;

public class Job {

    long jobID;
    long initiatedEpoch;
    String jobType;
    long targetEpoch;
    DataObject data;
    volatile boolean completed = false;

    public Job(long jobID, long initiatedEpoch, long targetEpoch, String jobType, DataObject data) {
        this.jobID = jobID;
        this.initiatedEpoch = initiatedEpoch;
        this.jobType = jobType;
        this.targetEpoch = targetEpoch;
        this.data = data;
    }

    public void setJobID(long jobID) {
        this.jobID = jobID;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    public long getJobID() {
        return jobID;
    }

    public String getJobType() {
        return jobType;
    }

    public long getTargetEpoch() {
        return targetEpoch;
    }

    public long getInitiatedEpoch() {
        return initiatedEpoch;
    }

    public String getSerialisedData(){
        return data.toString();
    }

    @JsonIgnore
    public DataObject getData(){
        return data;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Job job = (Job) o;

        return jobID == job.jobID;
    }

    @Override
    public int hashCode() {
        return (int) (jobID ^ (jobID >>> 32));
    }

    @Override
    public String toString() {
        return "Job{" +
                "jobID=" + jobID +
                ", initiatedEpoch=" + initiatedEpoch +
                ", jobType='" + jobType + '\'' +
                ", targetEpoch=" + targetEpoch +
                ", data=`" + data.toString() + "`" +
                ", completed=" + completed +
                '}';
    }

    //    @Override
//    public String toString() {
//        return getData().toString();
//    }
}
