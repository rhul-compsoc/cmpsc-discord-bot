package uk.co.hexillium.rhul.compsoc;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class HelperUtil {


    public static long getHowManySecondsAgo(OffsetDateTime time){
        return Instant.now().atOffset(time.getOffset()).toEpochSecond() - time.toEpochSecond();
    }

    /**
     * Finds the human readable time between two times.
     * @param timeOne the time that comes chronologically first
     * @param timeTwo the time that comes chronologically last
     * @return a human-readable delta
     */
    public static String getDeltaTime(OffsetDateTime timeOne, OffsetDateTime timeTwo) {
        long time = timeTwo.toEpochSecond() - timeOne.toEpochSecond();

        long secs = time % 60;
        time -= secs;
        time /= 60;

        long mins = time % 60;
        time -= mins;
        time /= 60;

        long hours = time % 24;
        time -= hours;
        time /= 24;

        long days = time % 30;
        time -= days;
        time /= 30;

        long months = time % 12;
        time -= months;
        time /= 12;

        long years = time;


        List<String> times = new ArrayList<String>();
        if (years > 0){
            times.add(years + " year" + (years > 1 ? "s" : ""));
        }
        if (months > 0){
            times.add(months + " month" + (months > 1 ? "s" : ""));
        }
        if (days > 0){
            times.add(days + " day" + (days > 1 ? "s" : ""));
        }
        if (hours > 0){
            times.add(hours + " hour" + (hours > 1 ? "s" : ""));
        }
        if (mins > 0){
            times.add(mins + " min" + (mins > 1 ? "s" : ""));
        }
        if (secs > 0){
            times.add(secs + " sec" + (secs > 1 ? "s" : ""));
        }
        StringBuilder strbld = new StringBuilder();
        for (int i = 0; i < times.size(); i++){
            strbld.append(times.get(i));
            if (times.size() - 2 == i){
                strbld.append(" and ");
            } else if (times.size() - 1 != i){
                strbld.append(", ");
            }
        }
        return strbld.toString();
    }

    static DateFormat format = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
    public static String getDateAndTime(){
        return getDateAndTimeAtMillis(System.currentTimeMillis());
    }
    public static String getDateAndTimeAtMillis(long millis){
        return format.format(new Date(millis));
    }
}
