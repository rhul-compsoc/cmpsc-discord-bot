package uk.co.hexillium.rhul.compsoc.commands;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import uk.co.hexillium.rhul.compsoc.CommandEvent;
import uk.co.hexillium.rhul.compsoc.persistence.Database;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class DBExec extends Command {


    private static final String[] commands = {"dbexec"};

    private static final Logger logger = LogManager.getLogger(DBExec.class);

    public DBExec() {
        super("Import", "Manually run SQL queries", "help stub", commands, "debug");
    }


    @Override
    public void handleCommand(CommandEvent event) {

        if (event.getUser().getIdLong() != 187979032904728576L){
            return;
        }

        if (event.getFullArg().isBlank()){
            event.reactFailure();
            event.reply("Cannot execute blank statement.");
            return;
        }

        try (Connection conn = Database.getInstance().getSource().getConnection()){
            CallableStatement cs = conn.prepareCall(event.getFullArg());
            cs.execute();
            ResultSet set = cs.getResultSet();
            if (set == null) {
                event.reactSuccess();
                event.reply("Successfully completed, " + cs.getUpdateCount());
                return;
            }
            List<String> results = new ArrayList<>();


        } catch (SQLException exception){
            event.reactFailure();
            event.reply("```" + exception.getMessage() + "``` The full stacktrace can be found in the logs.");
            logger.error("Failed to execute custom SQL. ", exception);
        }
    }
}
