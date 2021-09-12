package uk.co.hexillium.rhul.compsoc.persistence;

import com.zaxxer.hikari.HikariDataSource;
import net.dv8tion.jda.api.utils.data.DataObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import uk.co.hexillium.rhul.compsoc.persistence.entities.Job;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class JobStorage {

    private final static Logger LOGGER = LogManager.getLogger(JobStorage.class);

    private final static String GET_JOBS_BEFORE_TIME =
            "select job_id, initiated_epoch, target_epoch, job_type, triggered, job_data " +
                    " from job_schedule " +
                    " where triggered = false and target_epoch < ?;";
    private final static String ADD_JOB =
            "insert into job_schedule(initiated_epoch, target_epoch, job_type, job_data)  values (?, ?, ?, ?::jsonb) returning job_id; ";
    private final static String FINISH_JOB =
            "update job_schedule set triggered = TRUE where job_id = ?;";

    private HikariDataSource source;

    public JobStorage(HikariDataSource source) {
        this.source = source;
    }

    public void finishJobById(long jobID) {
        try (Connection connection = source.getConnection();
             PreparedStatement statement = connection.prepareStatement(FINISH_JOB)) {

            statement.setLong(1, jobID);
            statement.executeUpdate();

        } catch (SQLException ex) {
            LOGGER.error("Failed to finish jobs", ex);
        }
    }

    public int addJob(Job job) {
        try (Connection connection = source.getConnection();
             PreparedStatement statement = connection.prepareStatement(ADD_JOB)) {

            statement.setLong(1, job.getInitiatedEpoch());
            statement.setLong(2, job.getTargetEpoch());
            statement.setString(3, job.getJobType());
            statement.setString(4, job.getSerialisedData());

            statement.execute();

            ResultSet set = statement.getResultSet();
            set.next();
            return set.getInt("job_id");

        } catch (SQLException ex) {
            LOGGER.error("Failed to insert job " + job, ex);
        }
        return -1;
    }

    public void finishJob(Job job) {
        finishJobById(job.getJobID());
    }

    public List<Job> getNonFinishedJobsBeforeTime(long epoch) {
        List<Job> jobs = new ArrayList<>();
        try (Connection connection = source.getConnection();
             PreparedStatement statement = connection.prepareStatement(GET_JOBS_BEFORE_TIME)) {

            statement.setLong(1, epoch);

            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    jobs.add(
                            new Job(
                                    set.getLong("job_id"),
                                    set.getLong("initiated_epoch"),
                                    set.getLong("target_epoch"),
                                    set.getString("job_type"),
                                    DataObject.fromJson(set.getString("job_data"))
                            )
                    );
                }
            } catch (SQLException ex) {
                LOGGER.error("Failed to fetch scheduled jobs", ex);
            }
            return jobs;

        } catch (SQLException ex) {
            LOGGER.error("Failed to fetch scheduled jobs", ex);
        }
        return jobs;
    }

}
