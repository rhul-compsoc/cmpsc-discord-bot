package uk.co.hexillium.rhul.compsoc.time;

import net.dv8tion.jda.api.JDA;
import uk.co.hexillium.rhul.compsoc.persistence.Database;

import java.util.List;

public class JobScheduler {

    //executor service
    //keep scraping it
    //build entities based off of it
    //run them when it gets time to
    //also have a submit option

    private Database database;
    private JDA jda;

    public JobScheduler(Database database, JDA jda){
        this.database = database;
        this.jda = jda;
    }

    private List<GenericJob> pollDB(){
        return Database.JOB_STORAGE.getNonFinishedJobsBeforeTime(System.currentTimeMillis() + 1000 * 60 * 20);
    }




}
