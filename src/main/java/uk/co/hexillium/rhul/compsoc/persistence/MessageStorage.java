package uk.co.hexillium.rhul.compsoc.persistence;

import com.zaxxer.hikari.HikariDataSource;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

public class MessageStorage {

    private HikariDataSource source;
    private static final Logger logger = LogManager.getLogger(MessageStorage.class);

    private static final String insertMessage =
            "insert into messages(channel_id, message_id, modified_timestamp, author_id, message_content, attachment_url) VALUES " +
                    "(?, ?, ?, ?, ?, ?) on conflict do nothing;";

    private static final String deleteMessage =
            "update messages set deleted = true where message_id = ? and modified_timestamp = (select max(modified_timestamp) from messages where message_id = ?)";


    MessageStorage(HikariDataSource source){
        this.source = source;
    }

    public void insertMessage(Message message){
        try (Connection connection = source.getConnection();
             PreparedStatement statement = connection.prepareStatement(insertMessage)){

            addData(statement, message);

            statement.executeUpdate();

        } catch (SQLException ex) {
            logger.warn("Failed to insert message.", ex);
        }
    }



    public void insertBulkMessage(List<Message> messages){
        try (Connection connection = source.getConnection();
             PreparedStatement statement = connection.prepareStatement(insertMessage)){
            for (Message message : messages) {
                addData(statement, message);
                statement.addBatch();
            }

            statement.executeBatch();

        } catch (SQLException ex) {
            logger.warn("Failed to insert messages.", ex);
        }
    }



    public void deleteMessage(long messageID){
        try (Connection connection = source.getConnection();
             PreparedStatement statement = connection.prepareStatement(deleteMessage)){

            statement.setLong(1, messageID);
            statement.setLong(2, messageID);

            statement.executeUpdate();

        } catch (SQLException ex) {
            logger.warn("Failed to mark deleted message.", ex);
        }
    }


    private void addData(PreparedStatement statement, Message message) throws SQLException{
        statement.setLong(1, message.getChannel().getIdLong());
        statement.setLong(2, message.getIdLong());
        statement.setLong(3, Timestamp.valueOf(LocalDateTime.ofInstant(message.getTimeEdited() != null ?
                        message.getTimeEdited().toInstant() : message.getTimeCreated().toInstant(),
                ZoneOffset.UTC)).getTime());
        statement.setLong(4, message.getType() == MessageType.DEFAULT ? message.getAuthor().getIdLong() : -1);
        statement.setString(5, message.getContentRaw());
        statement.setString(6, message.getAttachments().size() > 0 ? message.getAttachments().get(0).getUrl() : null);
    }

}
