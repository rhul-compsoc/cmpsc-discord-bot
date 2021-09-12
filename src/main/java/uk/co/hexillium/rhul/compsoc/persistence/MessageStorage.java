package uk.co.hexillium.rhul.compsoc.persistence;

import com.zaxxer.hikari.HikariDataSource;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.TextChannel;
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


    private static final String insertChannel = "insert into channels (channel_snowflake, channel_name, channel_permissions, channel_description) values (?, ?, ?, ?) on conflict do nothing;";


    private static final String updateChannel = "update channels set channel_name = ? where channel_snowflake = ?;";

    MessageStorage(HikariDataSource source){
        this.source = source;
    }

    public void insertChannel(TextChannel channel){
        try (Connection conn = source.getConnection()){
            PreparedStatement ps = conn.prepareStatement(insertChannel);
            ps.setLong(1, channel.getIdLong());
            ps.setString(2, channel.getName());
            ps.setLong(3, 0);
            ps.setString(4, channel.getTopic());

            ps.executeUpdate();
        } catch (SQLException ex){
            logger.warn("Failed to insert Channel", ex);
        }
    }

    public void updateChannel(TextChannel tc){
        try (Connection conn = source.getConnection()){
            PreparedStatement ps = conn.prepareStatement(updateChannel);
            ps.setString(1, tc.getName());
            ps.setLong(2, tc.getIdLong());

            ps.executeUpdate();
        } catch (SQLException ex){
            logger.warn("Failed to update Channel", ex);
        }
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
        statement.setLong(4, !message.getType().isSystem() ? message.getAuthor().getIdLong() : -1);
        statement.setString(5, message.getContentRaw());
        statement.setString(6, message.getAttachments().size() > 0 ? message.getAttachments().get(0).getUrl() : null);
    }

}
