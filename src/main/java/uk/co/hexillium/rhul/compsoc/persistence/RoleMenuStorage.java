package uk.co.hexillium.rhul.compsoc.persistence;

import com.zaxxer.hikari.HikariDataSource;
import net.dv8tion.jda.api.interactions.components.selections.SelectionMenu;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import uk.co.hexillium.rhul.compsoc.persistence.entities.RoleSelection;
import uk.co.hexillium.rhul.compsoc.persistence.entities.RoleSelectionCategory;
import uk.co.hexillium.rhul.compsoc.persistence.entities.RoleSelectionMenu;
import uk.co.hexillium.rhul.compsoc.persistence.entities.TriviaScore;

import javax.annotation.CheckReturnValue;
import javax.xml.crypto.Data;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

public class RoleMenuStorage {

    private static final Logger LOGGER = LogManager.getLogger(RoleMenuStorage.class);
    private HikariDataSource source;

    static String SELECT_CATEGORIES = "select " +
            "       guild_id, category_name, category_description, category_emoji, category_button_type, category_id, " +
            "       category_min_selection, category_max_selection " +
            "from role_categories where guild_id = ?;";
    static String SELECT_CAT_ROLES = "select\n" +
            "       guildid, roleid, role_name, colour, emoji, description, categoryid\n" +
            "from role_options where guildid = ? and categoryid = ?;";
    static String SELECT_GUILD_ROLES = "select " +
            "    guildid, roleid, role_name, colour, emoji, description, categoryid " +
            "from role_options where guildid = ?;";

    static String INSERT_CATEGORY = "insert into role_categories (guild_id, category_name, category_description, category_emoji, category_button_type,\n" +
            "                             category_min_selection, category_max_selection)\n" +
            "values (?, ?, ?, ?, ?, ?, ?);";
    static String INSERT_ROLE = "insert into role_options (guildid, roleid, role_name, colour, emoji, description, categoryid)\n" +
            "values (?, ?, ?, ?, ?, ?, ?);";

    static String DELETE_CATEGORY = "delete from role_categories where category_id = ? and guild_id = ?;";
    static String DELETE_ROLE_OPTION = "delete from role_options where categoryid = ? and roleid = ?;";

    static String UPDATE_CATEGORY = "update role_categories\n" +
            "set category_button_type = ?,\n" +
            "    category_name = ?,\n" +
            "    category_description = ?,\n" +
            "    category_emoji = ?,\n" +
            "    category_min_selection = ?,\n" +
            "    category_max_selection = ?\n" +
            "where guild_id = ? and category_id = ?;";
    static String UPDATE_ROLE = "update role_options\n" +
            "set colour = ?,\n" +
            "    description = ?,\n" +
            "    emoji = ?,\n" +
            "    role_name = ?,\n" +
            "    roleid = ?\n" +
            "where guildid = ? and categoryid = ? and roleid = ?;";

    static String GET_CATEGORY_DATA = "select guild_id,\n" +
            "       category_name,\n" +
            "       category_description,\n" +
            "       category_emoji,\n" +
            "       category_button_type,\n" +
            "       category_id,\n" +
            "       category_min_selection,\n" +
            "       category_max_selection\n" +
            "from role_categories where category_id = ? and guild_id = ?;";

    public RoleMenuStorage(HikariDataSource source) {
        this.source = source;
    }

    @CheckReturnValue
    public RoleSelectionMenu getSelectionMenu(long guildID, boolean populateCategories) {
        RoleSelectionMenu menu = getSelectionMenu(guildID);
        if (!populateCategories || menu == null)
            return menu;
        List<RoleSelection> roles = getRolesForGuild(guildID);
        //match time
        HashMap<Long, List<RoleSelection>> idToRoleMap = new HashMap<>();
        for (RoleSelectionCategory rsc : menu.getCategories()) {
            idToRoleMap.put(rsc.getCategoryID(), rsc.getRoles());
        }
        for (RoleSelection role : roles) {
            idToRoleMap.get(role.getCategoryID()).add(role);
        }
        return menu;
    }

    @CheckReturnValue
    private RoleSelectionMenu getSelectionMenu(long guildID) {
        try (Connection connection = source.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_CATEGORIES)) {

            statement.setLong(1, guildID);

            ResultSet set = statement.executeQuery();

            List<RoleSelectionCategory> categories = new ArrayList<>();
            while (set.next()) {
                RoleSelectionCategory category = new RoleSelectionCategory(
                        set.getLong(1),
                        set.getString(2),
                        set.getString(3),
                        set.getString(4),
                        set.getString(5),
                        set.getLong(6),
                        set.getInt(7),
                        set.getInt(8),
                        new ArrayList<>()
                );
            }

            return new RoleSelectionMenu(categories, guildID);

        } catch (SQLException ex) {
            LOGGER.error("Failed to select the menu " + guildID, ex);
            return null;
        }
    }

    @CheckReturnValue
    public List<RoleSelection> getRolesForGuild(long guildID) {
        try (Connection connection = source.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_GUILD_ROLES)) {

            statement.setLong(1, guildID);

            ResultSet set = statement.executeQuery();

            List<RoleSelection> selections = new ArrayList<>();
            while (set.next()) {
                selections.add(new RoleSelection(
                        set.getLong(1),
                        set.getLong(2),
                        set.getString(3),
                        set.getInt(4),
                        set.getString(5),
                        set.getString(6),
                        set.getLong(7)
                ));
                //god I love SQL
            }
            return selections;
        } catch (SQLException ex) {
            LOGGER.error("Failed to fetch guild roles for selection menus", ex);
            return Collections.emptyList();
        }
    }

    @CheckReturnValue
    public List<RoleSelection> getRolesForCategory(long guildID, long categoryID) {
        try (Connection connection = source.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_CAT_ROLES)) {

            statement.setLong(1, guildID);
            statement.setLong(2, categoryID);

            ResultSet set = statement.executeQuery();

            List<RoleSelection> selections = new ArrayList<>();
            while (set.next()) {
                selections.add(new RoleSelection(
                        set.getLong(1),
                        set.getLong(2),
                        set.getString(3),
                        set.getInt(4),
                        set.getString(5),
                        set.getString(6),
                        set.getLong(7)
                ));
            }
            return selections;
        } catch (SQLException ex) {
            LOGGER.error("Failed to fetch guild roles for selection menus", ex);
            return Collections.emptyList();
        }
    }

    @CheckReturnValue
    private RoleSelectionCategory getCategoryFromID(long categoryID, long guildID) {
        try (Connection connection = source.getConnection();
             PreparedStatement statement = connection.prepareStatement(GET_CATEGORY_DATA)) {

            statement.setLong(1, categoryID);
            statement.setLong(2, guildID);

            ResultSet set = statement.executeQuery();
            if (!set.next()) {
                return null;
            }

            return new RoleSelectionCategory(
                    set.getLong(1),
                    set.getString(2),
                    set.getString(3),
                    set.getString(4),
                    set.getString(5),
                    set.getLong(6),
                    set.getInt(7),
                    set.getInt(8),
                    Collections.emptyList()
            );
        } catch (SQLException ex) {
            LOGGER.error("Failed to fetch selection menu category data", ex);
            return null;
        }
    }

    @CheckReturnValue
    public RoleSelectionCategory getCategoryFromID(long categoryID, long guildID, boolean populate){
        RoleSelectionCategory cat = getCategoryFromID(categoryID, guildID);
        if (!populate || cat == null)
            return cat;
        cat.getRoles().addAll(getRolesForCategory(guildID, categoryID));
        return cat;
    }

    public void insertCategory(RoleSelectionCategory category) {
        try (Connection connection = source.getConnection();
             PreparedStatement statement = connection.prepareStatement(INSERT_CATEGORY)) {

            statement.setLong(1, category.getGuildID());
            statement.setString(2, category.getName());
            statement.setString(3, category.getDescription());
            statement.setString(4, category.getEmoji());
            statement.setString(5, category.getStyle().name());
            statement.setInt(6, category.getMin());
            statement.setInt(7, category.getMax());

            statement.executeUpdate();

        } catch (SQLException exception) {
            LOGGER.error("Failed to insert category ", exception);
        }

    }

    public void insertRole(long guildID, RoleSelection role, String categoryName) {
        RoleSelectionMenu menu = getSelectionMenu(guildID, false);
        for (RoleSelectionCategory cat : menu.getCategories()){
            if (cat.getName().trim().equalsIgnoreCase(categoryName.trim())){
                insertRole(guildID, role, cat.getCategoryID());
            }
        }
        throw new IllegalArgumentException("Category name was not valid.");
    }

    private void insertRole(long guildID, RoleSelection role, long categoryID) {
        try (Connection connection = source.getConnection();
             PreparedStatement statement = connection.prepareStatement(INSERT_ROLE)) {

            statement.setLong(1, guildID);
            statement.setLong(2, role.getRoleID());
            statement.setString(3, role.getName());
            statement.setInt(4, role.getColour());
            statement.setString(5, role.getEmoji());
            statement.setString(6, role.getDescription());
            statement.setLong(7, categoryID);

            statement.executeUpdate();

        } catch (SQLException ex) {
            LOGGER.error("Failed to insert new RoleSelection.");
        }
    }

    public void deleteCategory(long categoryID, long guildID) {
        try (Connection connection = source.getConnection();
             PreparedStatement statement = connection.prepareStatement(DELETE_CATEGORY)) {

            statement.setLong(1, categoryID);
            statement.setLong(2, guildID);

            statement.executeUpdate();

        } catch (SQLException ex) {
            LOGGER.error("Failed to delete category ", ex);
        }
    }

    public void deleteRoleOption(long categoryID, long roleID) {
        try (Connection connection = source.getConnection();
             PreparedStatement statement = connection.prepareStatement(DELETE_ROLE_OPTION)) {

            statement.setLong(1, categoryID);
            statement.setLong(2, roleID);

            statement.executeUpdate();

        } catch (SQLException ex) {
            LOGGER.error("Failed to delete role option ", ex);
        }
    }

}
