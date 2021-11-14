package uk.co.hexillium.rhul.compsoc.persistence;

import com.zaxxer.hikari.HikariDataSource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import uk.co.hexillium.rhul.compsoc.persistence.entities.MembershipDetail;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Collection;

public class SUTransactionStorage {

    private static final Logger LOGGER = LogManager.getLogger(SUTransactionStorage.class);
    private static final String INSERT_MEMBER =
            "insert into su_data (transaction_id, purchaser, u18, card_number, shop_name, qty, purchase_date, type) values (?, ?, ?, ?, ?, ?, ?, ?) " +
                    " on conflict (transaction_id) do nothing;";
    private static final String INSERT_ROLE_MAPPING = "insert into purchase_roles (role_snowflake, purchase_type, valid_from, valid_until) VALUES (?, ?, ?, ?);";
    private static final String ANALYSE_MAPPINGS = "select pr.role_snowflake, sud.card_number, sud.purchaser, sv.student_discord_snowflake, coalesce(mi.nickname, mi.username || '#' || mi.discrim, '(non-cached user)') as name from purchase_roles pr\n" +
            "    inner join su_data sud on pr.purchase_type = sud.type\n" +
            "    left join student_verification sv on sud.card_number = sv.student_id\n" +
            "    left join member_information mi on sv.student_discord_snowflake = mi.member_id and mi.guild_id = ?\n" +
            "    where coalesce(pr.valid_from <= now(), true) and coalesce(pr.valid_until > now(), true);\n" +
            ";";
    private HikariDataSource source;

    private static class MappingAnalysis {
        /*pr.role_snowflake, sud.card_number, sud.purchaser, sv.student_discord_snowflake, coalesce(mi.nickname, mi.username || '#' || mi.discrim, '(non-cached user)') as name*/

        long roleSnowflake;
        String cardNumber;
        String purchaser;
        long discordMemberSnowflake;
        String name;
        long guildID;

        private MappingAnalysis(long roleSnowflake, String cardNumber, String purchaser, long discordMemberSnowflake, String name, long guildID) {
            this.roleSnowflake = roleSnowflake;
            this.cardNumber = cardNumber;
            this.purchaser = purchaser;
            this.discordMemberSnowflake = discordMemberSnowflake;
            this.name = name;
            this.guildID = guildID;
        }

        public long getRoleSnowflake() {
            return roleSnowflake;
        }

        public String getCardNumber() {
            return cardNumber;
        }

        public String getPurchaser() {
            return purchaser;
        }

        public long getDiscordMemberSnowflake() {
            return discordMemberSnowflake;
        }

        public String getName() {
            return name;
        }

        public long getGuildID() {
            return guildID;
        }
    }


    public SUTransactionStorage(HikariDataSource source) {
        this.source = source;
    }


    public void insertSUData(Collection<MembershipDetail> details) {
        try (Connection connection = source.getConnection();
             PreparedStatement ps = connection.prepareStatement(INSERT_MEMBER)) {

            for (MembershipDetail detail : details) {
                //transaction_id, purchaser, u18, card_number, shop_name, qty, purchase_date, type
                ps.setInt(1, detail.getTransaction_id());
                ps.setString(2, detail.getPurchaser());
                ps.setString(3, detail.getTextbox6());
                ps.setString(4, detail.getCard_number());
                ps.setString(5, detail.getShop_name());
                ps.setInt(6, detail.getQty());
                ps.setObject(7, detail.getPurchase_date());
                ps.setString(8, detail.getType());
                ps.addBatch();
            }
            ps.executeBatch();

        } catch (SQLException ex) {
            LOGGER.error("Failed to insert membership details ", ex);
        }
    }

    public void insertRoleMapping(long roleSnowflake, @Nonnull String purchaseType, @Nullable OffsetDateTime validFrom, @Nullable OffsetDateTime validTo){
        
        try (Connection connection = source.getConnection();
            PreparedStatement ps = connection.prepareStatement(INSERT_ROLE_MAPPING)){

            //role_snowflake, purchase_type, valid_from, valid_until

            ps.setLong(1, roleSnowflake);
            ps.setString(2, purchaseType);
            ps.setObject(3, validFrom);
            ps.setObject(3, validTo);

            ps.executeUpdate();

        } catch (SQLException ex){
            LOGGER.error("Failed to insert role mapping");
        }
    }

    public void fetchMappingsAnalysis(long guildID){

    }


}
