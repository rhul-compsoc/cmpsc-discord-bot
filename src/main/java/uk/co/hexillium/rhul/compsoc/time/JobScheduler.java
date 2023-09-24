package uk.co.hexillium.rhul.compsoc.time;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.utils.TimeFormat;
import net.dv8tion.jda.api.utils.data.DataObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import uk.co.hexillium.rhul.compsoc.persistence.Database;
import uk.co.hexillium.rhul.compsoc.persistence.entities.Job;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;

public class JobScheduler {

    //executor service
    //keep scraping it
    //build entities based off of it
    //run them when it gets time to
    //also have a submit option

    /**
     * How often jobs should be registered into the scheduler
     */
    static private final long MILLISECONDS_TO_REGISTER_JOBS = 2 * 60 * 1000; // 1 mins
    /**
     * How far in advance jobs should be registered into the scheduler
     */
    static private final long MILLISECONDS_ADVANCE_REGISTER_JOBS = 2 * 60 * 1000; // 3 mins
    /**
     * How often the database should be polled for new jobs
     */
    static private final long MILLISECONDS_TO_FETCH_JOBS = 6 * 60 * 1000; // 10 mins
    /**
     * How far in advance new jobs in the database can be to still be pulled into the queue
     */
    static private final long MILLISECONDS_ADVANCE_FETCH_JOBS = 25 * 60 * 1000; // 25 mins

    private final Database database;
    private final JDA jda;
    private final HashMap<String, Consumer<DataObject>> triggerMap;
    //    private final ArrayDeque<Job> upcomingJobs;
    private final PriorityQueue<Job> upcomingJobs;
    private final ScheduledExecutorService scheduler;
    private final ReentrantReadWriteLock queueLock;
    private final HashSet<Job> scheduledJobs;

    private long recentDBPoll = 0, recentQueuePush = 0;

    private static final Logger logger = LogManager.getLogger(JobScheduler.class);


    private boolean initialised = false;


    public JobScheduler(Database database, JDA jda) {
        this.database = database;
        this.jda = jda;
        this.triggerMap = new HashMap<>();
        this.scheduledJobs = new HashSet<>();
        upcomingJobs = new PriorityQueue<>((a, b) -> (int) Math.signum(a.getTargetEpoch() - b.getTargetEpoch()));
        scheduler = Executors.newSingleThreadScheduledExecutor();
        queueLock = new ReentrantReadWriteLock();
    }

    public synchronized void initialise() {
        if (initialised) return;
        scheduler.scheduleAtFixedRate(this::enqueueNewJobs, 0, MILLISECONDS_TO_FETCH_JOBS, TimeUnit.MILLISECONDS);
        scheduler.scheduleAtFixedRate(this::registerUpcomingJobs, 30 * 1000, MILLISECONDS_TO_REGISTER_JOBS, TimeUnit.MILLISECONDS);
        initialised = true;
    }

    private List<Job> pollDB() {
        return Database.JOB_STORAGE.getNonFinishedJobsBeforeTime(System.currentTimeMillis() + MILLISECONDS_ADVANCE_FETCH_JOBS);
        // this might cause issues with DST
    }

    private void enqueueNewJobs() {
        this.recentDBPoll = System.currentTimeMillis();
        logger.debug("Running enqueueNewJobs() scheduled task");
        queueLock.writeLock().lock();
        try {
            List<Job> newJobs = pollDB();
            newJobs.removeAll(scheduledJobs);
            newJobs.removeAll(upcomingJobs);
            upcomingJobs.addAll(newJobs);
        } finally {
            queueLock.writeLock().unlock();
        }
    }

    private void registerJob(Job job) {
        scheduledJobs.add(job);
        scheduler.schedule(() -> {
                    if (job.isCompleted()){
                        logger.debug("Completed job preventing duplicate execution " + job.getJobID());
                        return;
                    }
                    logger.debug("Job first execution " + job.getJobID());
                    job.setCompleted(true);
                    triggerMap.get(job.getJobType()).accept(job.getData());
                    Database.JOB_STORAGE.finishJobById(job.getJobID());
                    scheduledJobs.remove(job);
                },
                Math.max(0, job.getTargetEpoch() - System.currentTimeMillis()),
                TimeUnit.MILLISECONDS);
    }



    private void registerUpcomingJobs() {
        this.recentQueuePush = System.currentTimeMillis();
        logger.debug("Running registerUpcomingJobs() scheduled task");
        queueLock.readLock().lock();
        try {

            while (!upcomingJobs.isEmpty()
                    && upcomingJobs.peek().getTargetEpoch()<= System.currentTimeMillis() + MILLISECONDS_ADVANCE_REGISTER_JOBS){
                registerJob(upcomingJobs.poll());  //foolish IntelliJ.
            }
        } finally {
            queueLock.readLock().unlock();
        }
    }

    public void submitJob(Job job) {
        Database.runLater(() -> {
            int id = Database.JOB_STORAGE.addJob(job);
            job.setJobID(id);
            if ((job.getTargetEpoch() - System.currentTimeMillis()) < MILLISECONDS_ADVANCE_REGISTER_JOBS) {
                registerJob(job);
            } else if ((job.getTargetEpoch() - System.currentTimeMillis()) < MILLISECONDS_ADVANCE_FETCH_JOBS){
                queueLock.writeLock().lock();
                try {
                    upcomingJobs.add(job);
                } finally {
                    queueLock.writeLock().unlock();
                }
            }
        });
    }

    public void registerHandle(String type, Consumer<DataObject> onTrigger) {
        if (this.triggerMap.get(type) != null) {
            throw new IllegalArgumentException("Trigger names must be unique.");
        }
        triggerMap.put(type, onTrigger);
    }

    public String getDebugInfo(){
        return "Upcoming head: " + upcomingJobs.peek() + ",\n" +
                "Upcoming size: " + upcomingJobs.size() + " items,\n" +
                "Trigger keys: " + triggerMap.keySet() + "\n" +
                "RecentDBPoll: " + TimeFormat.DATE_TIME_LONG.format(recentDBPoll) + "\n" +
                "RecentQueuePush: " + TimeFormat.DATE_TIME_LONG.format(recentQueuePush) + "\n" +
                "Lock state: " + queueLock.toString();
    }

}
