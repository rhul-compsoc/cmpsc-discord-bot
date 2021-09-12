package uk.co.hexillium.rhul.compsoc.time;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TimeUtils {

    public static final Pattern periodPattern = Pattern.compile(
            "([0-9]+)[ \\-.,]*?" +
                    "(" +
                    "mo|mnth|month|months" +
                    "|w|wk|wks|weeks" +
                    "|h|hrs|hours" +
                    "|d|day|days" +
                    "|m|min|mins|minutes" +
                    "|s|sec|secs|seconds" +
                    ")");

    public static final Pattern timePattern = Pattern.compile(
            "(([0-9]+)[ \\-.,]*?" +
                    "(" +
                    "mo|mnth|month|months" +
                    "|w|wk|wks|weeks" +
                    "|h|hrs|hours" +
                    "|d|day|days" +
                    "|m|min|mins|minutes" +
                    "|s|sec|secs|seconds" +
                    "))+"
    );

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

    public static OffsetDateTime parseTarget(String period){
        if(period == null) return null;
        period = period.toLowerCase(Locale.ENGLISH);
        Matcher matcher = periodPattern.matcher(period);
        //Instant instant=Instant.now();
        OffsetDateTime offset = OffsetDateTime.now();
        while(matcher.find()){
            int num = Integer.parseInt(matcher.group(1));
            String typ = matcher.group(2);
            switch (typ) {
                case "s": case "sec": case "secs": case "seconds":
                    //instant=instant.plus(Duration.ofSeconds(num));
                    offset = offset.plusSeconds(num);
                    break;
                case "m": case "min": case "mins": case "minutes":
                    //instant=instant.plus(Duration.ofMinutes(num));
                    offset = offset.plusMinutes(num);
                    break;
                case "h": case "hrs": case "hours":
                    //instant=instant.plus(Duration.ofHours(num));
                    offset = offset.plusHours(num);
                    break;
                case "d": case "day": case "days":
                    //instant=instant.plus(Duration.ofDays(num));
                    offset = offset.plusDays(num);
                    break;
                case "w": case "wk": case "wks": case "weeks":
                    //instant=instant.plus(Period.ofWeeks(num));
                    offset = offset.plusWeeks(num);
                    break;
                case "mo": case "mnth": case "month": case "months":
                    //instant=instant.plus(Period.ofMonths(num));
                    offset = offset.plusMonths(num);
                    break;
            }
        }
        return offset;
        //return instant.toEpochMilli();
    }

}
