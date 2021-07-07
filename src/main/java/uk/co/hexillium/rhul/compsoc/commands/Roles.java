package uk.co.hexillium.rhul.compsoc.commands;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.events.interaction.SelectionMenuEvent;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

public class Roles implements ComponentInteractionHandler, SlashCommandHandler {

    private static final Logger logger = LogManager.getLogger(Roles.class);

    @Override
    public void initComponentInteractionHandle(JDA jda) {

    }

    @Override
    public void handleButtonInteraction(ButtonClickEvent interaction, String button) {

    }

    @Override
    public void handleSelectionMenuInteraction(SelectionMenuEvent interaction, String button) {

    }

    @Override
    public List<String> registerHandles() {
        List<String> handles = new ArrayList<>();
//        handles.add("m:ro"); //menu:roles
        handles.add("m:ro:a"); //menu:roles:add
        handles.add("m:ro:d"); //menu:roles:delete
        handles.add("b:ro:c"); //buttons:roles:category
        return handles;
    }

    @Override
    public List<CommandData> registerGlobalCommands() {
        List<CommandData> commands = new ArrayList<>();
        commands.add(new CommandData("roles", "Get yourself some roles!"));
        commands.add(new CommandData("manageroles", "Manage the self-selection roles.")
                .addSubcommands(
                        new SubcommandData("list", "Show the current selection choices."),
                        new SubcommandData("addcategory", "Add a category to the roles")
                                .addOption(OptionType.STRING, "name", "The name of this new category (must be unique)", true)
                                .addOption(OptionType.INTEGER, "maxnum", "The maximum number of roles from this category that can be selected (default: 25)", false),
                        new SubcommandData("addrole", "Add a role to an existing category")
                                .addOption(OptionType.STRING, "category", "The category to select", true)
                                .addOption(OptionType.ROLE, "role", "The role to add", true),
                        new SubcommandData("delrole", "Delete a role from an exiting category")
                                .addOption(OptionType.STRING, "category", "The category to select", true)
                                .addOption(OptionType.ROLE, "role", "The role to remove from the category", true),
                        new SubcommandData("delroles", "Show a menu to remove multiple roles from a specified category"),
                        new SubcommandData("delcategory", "delete a category from the menu.")
                                .addOption(OptionType.STRING, "category", "The category to delete."),
                        new SubcommandData("modifycategory", "Modify the number of roles selectable from a given category.")
                                .addOption(OptionType.STRING, "category", "The category to select")
                                .addOption(OptionType.INTEGER, "maxnum", "The new maximum number of selectable roles from this category.")
                ));
        return commands;
    }

    @Override
    public void handleSlashCommand(SlashCommandEvent event) {
        switch (event.getName()) {
            case "roles":
                //show the menu
                break;
            case "manageroles":
                //switch the subcommands
                if (event.getSubcommandName() == null) {
                    //something has gone wrong
                    event.reply("You've broken something... that's not good...").setEphemeral(true).queue();
                    logger.error("User manged to get empty subcommand name " + event);
                    return;
                }
                switch (event.getSubcommandName()) {
                    case "list":
                        break;
                    case "addcategory":
                        break;
                    case "addrole":
                        break;
                    case "delrole":
                        break;
                    case "delroles":
                        break;
                    case "delcategory":
                        break;
                    case "modifycategory":
                        break;
                }
                break;
        }

    }
}
