package uk.co.hexillium.rhul.compsoc.commands;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.Emoji;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.events.interaction.SelectionMenuEvent;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.Button;
import net.dv8tion.jda.api.interactions.components.ButtonStyle;
import net.dv8tion.jda.api.interactions.components.Component;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.interactions.components.selections.SelectionMenu;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import uk.co.hexillium.rhul.compsoc.persistence.Database;
import uk.co.hexillium.rhul.compsoc.persistence.entities.RoleSelection;
import uk.co.hexillium.rhul.compsoc.persistence.entities.RoleSelectionCategory;
import uk.co.hexillium.rhul.compsoc.persistence.entities.RoleSelectionMenu;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

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
                        new SubcommandData("list", "Show the current selection choices.")
                                .addOption(OptionType.STRING, "catname", "If provided, will only show this category", false),
                        new SubcommandData("addcategory", "Add a category to the roles")
                                .addOption(OptionType.STRING, "name", "The name of this new category (must be unique)", true)
//                                .addOption(OptionType.STRING, "buttonstyle", "The button style", true)
                                .addOptions(
                                        new OptionData(OptionType.STRING, "buttonstyle", "The button style that should be used for selecting this category.", true)
                                                .addChoice("PRIMARY", "PRIMARY")
                                                .addChoice("SECONDARY", "SECONDARY")
                                )
                                .addOption(OptionType.STRING, "emoji", "The emoji used to represent this category", false)
                                .addOption(OptionType.STRING, "description", "A description of this role category", false)
                                .addOption(OptionType.INTEGER, "minnum", "The minimum number of roles from this category that can be selected (default: 0)", false)
                                .addOption(OptionType.INTEGER, "maxnum", "The maximum number of roles from this category that can be selected (default: 25)", false),
                        new SubcommandData("addrole", "Add a role to an existing category")
                                .addOption(OptionType.STRING, "category", "The name of the category to add this role to", true)
                                .addOption(OptionType.ROLE, "role", "The role to add", true)
                                .addOption(OptionType.STRING, "description", "The description of this role.", false)
                                .addOption(OptionType.INTEGER, "colour", "The colour to tag this role with (currently unused)", false)
                                .addOption(OptionType.STRING, "emoji", "The emoji to display for this role's selection", false),
                        new SubcommandData("delrole", "Delete a role from an exiting category")
                                .addOption(OptionType.STRING, "category", "The category to select", true)
                                .addOption(OptionType.ROLE, "role", "The role to remove from the category", true),
                        new SubcommandData("delroles", "Show a menu to remove multiple roles from a specified category")
                                .addOption(OptionType.STRING, "category", "The name of the category to manage", true),
                        new SubcommandData("delcategory", "delete a category from the menu.")
                                .addOption(OptionType.STRING, "category", "The name of the category to delete.", true),
                        new SubcommandData("modifycategory", "Modify the number of roles selectable from a given category.")
                                .addOption(OptionType.STRING, "category", "The name of the category to modify", true)
                                .addOption(OptionType.STRING, "emoji", "The emoji used to represent this category", false)
                                .addOptions(
                                        new OptionData(OptionType.STRING, "buttonstyle", "The button style that should be used for selecting this category.", false)
                                                .addChoice("PRIMARY", "PRIMARY")
                                                .addChoice("SECONDARY", "SECONDARY")
                                )
                                .addOption(OptionType.STRING, "description", "A description of this role category", false)
                                .addOption(OptionType.INTEGER, "minnum", "The minimum number of roles from this category that can be selected (default: 0)", false)
                                .addOption(OptionType.INTEGER, "maxnum", "The maximum number of roles from this category that can be selected (default: 25)", false)

                ));
        return commands;
    }

    public void handleShowRoles(Member member, InteractionHook hook) {
        RoleSelectionMenu menu = Database.ROLE_MENU_STORAGE.getSelectionMenu(member.getGuild().getIdLong(), false);
        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle("Role Menu");
        eb.setDescription("Select a category from below to be offered roles for that category!");
        eb.setAuthor(member.getEffectiveName(), null, member.getUser().getEffectiveAvatarUrl());
        eb.setTimestamp(Instant.now());

        List<Component> components = menu.getCategories().stream().map(category ->
                Button.of(category.getStyle(),
                        "b:ro:c|" + category.getCategoryID(), //buttons:roles:category
                        category.getName(),
                        category.getEmoji() == null ? null : Emoji.fromMarkdown(category.getEmoji()
                        )
                )
        ).collect(Collectors.toList());

        if (components.size() > 25) {
            hook.sendMessage("Error! Too many options! Please notify an admin.").setEphemeral(true).queue();
            return;
        }

        //break the big list down into lists of length 5.
        List<List<Component>> builder = new ArrayList<>();
        for (int i = 0; i < components.size(); i++) {
            if (i % 5 == 0) {
                builder.add(new ArrayList<>());
            }
            builder.get(i / 5).add(components.get(i));
        }

        hook.sendMessageEmbeds(eb.build()).addActionRows(builder.stream().map(ActionRow::of).collect(Collectors.toList())).setEphemeral(true).queue();
    }

    public void handleRoleCategoryMenu(Member member, long roleCat, InteractionHook hook) {
        RoleSelectionCategory cat = Database.ROLE_MENU_STORAGE.getCategoryFromID(roleCat, member.getGuild().getIdLong(), true);
        List<RoleSelection> roles = cat.getRoles();
        if (roles.size() > 25) {
            hook.sendMessage("There are too many roles.  Please contact an admin.").setEphemeral(true).queue();
            return;
        }
        SelectionMenu.Builder builder = SelectionMenu.create("s:ro:r|" + roleCat);
        for (RoleSelection role : roles) {
            builder.addOption(role.getName(),
                    "m:ro:a|" + role.getCategoryID() + "|" + role.getRoleID(),
                    role.getDescription(),
                    role.getEmoji() == null ? null : Emoji.fromMarkdown(role.getEmoji()));
        }
        EmbedBuilder emb = new EmbedBuilder();
        emb.setTimestamp(Instant.now());
        emb.setAuthor(member.getEffectiveName(), null, member.getUser().getEffectiveAvatarUrl());
        emb.setTitle("Options for " + cat.getName());
        emb.setColor(member.getColorRaw());
        emb.setDescription(cat.getDescription());
        if (cat.getMin() > 0 || cat.getMax() < 25) {
            String limits = cat.getMin() > 0 ? "\nYou must select at least " + cat.getMin() + " roles." : "";
            limits += cat.getMax() < 25 ? "\nYou must select fewer than " + cat.getMax() + " roles. " : "";
            emb.addField("Special Limits:", "The following limits apply to this menu:" + limits, false);
        }
        hook.sendMessageEmbeds(emb.build()).setEphemeral(true).addActionRow(builder.build()).queue();

    }

    @Override
    public void handleSlashCommand(SlashCommandEvent event) {
        if (event.getMember() == null) return;
        switch (event.getName()) {
            case "roles":
                //show the menu
                event.deferReply(true).queue();
                handleShowRoles(event.getMember(), event.getHook());
                break;
            case "manageroles":
                if (!event.getMember().getRoles().contains(event.getGuild().getRoleById(500612754185650177L))) {
                    event.reply(">:(").setEphemeral(true).queue();
                    return;
                }
                //switch the subcommands
                if (event.getSubcommandName() == null) {
                    //something has gone wrong
                    event.reply("You've broken something... that's not good...").setEphemeral(true).queue();
                    logger.error("User manged to get empty subcommand name " + event);
                    return;
                }

                OptionMapping nameOpt = event.getOption("name");
                OptionMapping descOpt = event.getOption("description");
                OptionMapping emojiOpt = event.getOption("emoji");
                OptionMapping buttOpt = event.getOption("buttonstyle");
                OptionMapping catOpt = event.getOption("category");
                OptionMapping roleOpt = event.getOption("role");
                OptionMapping colourOpt = event.getOption("colour");
                OptionMapping Omin = event.getOption("minnum");
                int min = Omin == null ? 0 : (int) Math.min(25, Omin.getAsLong());
                OptionMapping Omax = event.getOption("maxnum");
                int max = Omax == null ? 25 : (int) Math.max(25, Omax.getAsLong());
                switch (event.getSubcommandName()) {
                    case "list":
                        //args: ?STRING:catname
                        ObjectMapper mapper = new ObjectMapper();
                        event.reply(asString(Database.ROLE_MENU_STORAGE.getSelectionMenu(event.getGuild().getIdLong(), true))).queue();
                        break;
                    case "addcategory":
                        //args: STRING:name, STRING:buttonstyle, ?STRING:emoji, ?STRING:description, ?INT:minnum, ?INT:maxnum
                        if (min > max) {
                            event.reply("Cannot have a larger min than max.").setEphemeral(true).queue();
                            return;
                        }
                        Database.ROLE_MENU_STORAGE.insertCategory(new RoleSelectionCategory(
                                event.getGuild().getIdLong(),
                                nameOpt == null ? null : nameOpt.getAsString(),
                                descOpt == null ? null : descOpt.getAsString(),
                                emojiOpt == null ? null : emojiOpt.getAsString(),
                                buttOpt == null ? null : buttOpt.getAsString(),
                                -1, min,
                                max, Collections.emptyList()
                        ));
                        event.reply("Successfully inserted new category.").setEphemeral(true).queue();
                        break;
                    case "addrole":
                        //args: STRING:category, ROLE:role, ?STRING:description, ?INT:colour, ?STRING:emoji
                        if (roleOpt == null || catOpt == null) {
                            return;
                        }
                        Role role = roleOpt.getAsRole();
                        try {
                            Database.ROLE_MENU_STORAGE.insertRole(event.getGuild().getIdLong(),
                                    new RoleSelection(event.getGuild().getIdLong(), roleOpt.getAsLong(),
                                            role.getName(), colourOpt == null ? role.getColorRaw() : (int) colourOpt.getAsLong(),
                                            emojiOpt == null ? null : emojiOpt.getAsString(), descOpt == null ? null : descOpt.getAsString(),
                                            -1), catOpt.getAsString());
                        } catch (IllegalArgumentException ex) {
                            event.reply(ex.getMessage()).setEphemeral(true).queue();
                        }
                        break;
                    case "delrole":
                        //args: STRING:category, ROLE:role
                        break;
                    case "delroles":
                        //args: STRING:category
                        break;
                    case "delcategory":
                        //args: STRING:category
                        break;
                    case "modifycategory":
                        //args: STRING:category, ?STRING:emoji, ?STRING:buttonstyle, ?STRING:description, ?INT:minnum, ?INT:maxnum
                        break;
                }
                break;
        }

    }

    ObjectMapper mapper = new ObjectMapper();

    public String asString(Object object) {
        try {
            return mapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            return "Failed JSON.";
        }
    }
}
