package uk.co.hexillium.rhul.compsoc.persistence;

import com.zaxxer.hikari.HikariDataSource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import uk.co.hexillium.rhul.compsoc.time.GenericJob;
import uk.co.hexillium.rhul.compsoc.time.Job;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class JobStorage {

    private final static Logger LOGGER = LogManager.getLogger(JobStorage.class);

    private final static String ADD_JOB =
            "select job_id, initiated_epoch, target_epoch, job_type, triggered " +
                    " from job_schedule " +
                        " where target_epoch < ?;";
    private final static String GET_JOBS_BEFORE_TIME =
            "insert into job_schedule(job_id, initiated_epoch, target_epoch, job_type)  values (?, ?, ?); ";
    private final static String FINISH_JOB =
            "update job_schedule set triggered = TRUE where job_id = ?;";

    private HikariDataSource source;

    public JobStorage(HikariDataSource source){
        this.source = source;
    }

    public void finishJobById(long jobID){
        try (Connection connection = source.getConnection();
             PreparedStatement statement = connection.prepareStatement(FINISH_JOB)){

            statement.setLong(1, jobID);
            statement.executeUpdate();

        } catch (SQLException ex){
            LOGGER.error("Failed to finish jobs", ex);
        }
    }

    public void finishJob(Job job){
        finishJobById(job.getJobID());
    }

    public List<GenericJob> getNonFinishedJobsBeforeTime(long epoch){
        List<GenericJob> jobs = new ArrayList<>();
        try (Connection connection = source.getConnection();
            PreparedStatement statement = connection.prepareStatement(GET_JOBS_BEFORE_TIME)){

            statement.setLong(1, epoch);

            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    jobs.add(
                            new GenericJob(
                                    set.getLong("job_id"),
                                    set.getInt("initiated_epoch"),
                                    set.getLong("target_epoch"),
                                    set.getInt("job_type")

                            )
                    );
                }
            } catch (SQLException ex){
                LOGGER.error("Failed to fetch scheduled jobs", ex);
            }
            return jobs;

        } catch (SQLException ex){
            LOGGER.error("Failed to fetch scheduled jobs", ex);
        }
        return jobs;
    }

}
