package uk.co.hexillium.rhul.compsoc.commands;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.GenericSelectMenuInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.ItemComponent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.data.DataObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import uk.co.hexillium.rhul.compsoc.commands.handlers.ComponentInteractionHandler;
import uk.co.hexillium.rhul.compsoc.commands.handlers.SlashCommandHandler;
import uk.co.hexillium.rhul.compsoc.persistence.Database;
import uk.co.hexillium.rhul.compsoc.persistence.entities.RoleSelection;
import uk.co.hexillium.rhul.compsoc.persistence.entities.RoleSelectionCategory;
import uk.co.hexillium.rhul.compsoc.persistence.entities.RoleSelectionMenu;

import javax.annotation.Nonnull;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class Roles implements ComponentInteractionHandler, SlashCommandHandler {

    private static final Logger logger = LogManager.getLogger(Roles.class);
    ObjectMapper mapper = new ObjectMapper();

    @Override
    public void initComponentInteractionHandle(JDA jda) {

    }

    @Override
    public void handleButtonInteraction(ButtonInteractionEvent interaction, String button) {
        if (interaction.getMember() == null) return;
        String[] components = interaction.getComponentId().split("\\|", 2);
        switch (components[0]) {
            case "b:ro:c":
                //a role category button hath been pressed

                long catID = Long.parseLong(components[1]);
//                interaction.deferReply(true).queue();
                handleRoleCategoryMenu(interaction.getMember(), catID, interaction);
                break;
            case "b:ro:m":
                interaction.editMessageEmbeds(getMenuEmbedBuilder(interaction.getMember()).build())
                        .setContent("").setComponents(
                                getMenuComponents(interaction.getMember())
                                        .stream()
                                        .map(ActionRow::of)
                                        .collect(Collectors.toList())
                        ).queue();
                break;
                case "b:ro:s":
                interaction.replyEmbeds(getMenuEmbedBuilder(interaction.getMember()).build())
                        .setEphemeral(true)
                        .setContent("").setComponents(
                                getMenuComponents(interaction.getMember())
                                        .stream()
                                        .map(ActionRow::of)
                                        .collect(Collectors.toList())
                        ).queue();
                break;
        }
    }

    @Override
    public void handleSelectionMenuInteraction(GenericSelectMenuInteractionEvent<?,?> genericInteraction) {
        if (!(genericInteraction instanceof StringSelectInteractionEvent interaction)) return;
        if (interaction.getMember() == null) return;
        String[] components = interaction.getComponentId().split("\\|", 2);
        switch (components[0]) {
            case "s:ro:r":  //add roles to member
                // The button format is "m:ro:a|CAT_ID|ROLEID"
                List<String> roleIDs = interaction.getValues();
//                interaction.deferReply(true).queue();
                long parsedCatID = Long.parseLong(components[1]);
                RoleSelectionCategory cat = Database.ROLE_MENU_STORAGE.getCategoryFromID(parsedCatID, interaction.getGuild().getIdLong(), false);
                if (cat.getRequiredRoleId() != interaction.getGuild().getIdLong() && interaction.getMember().getRoles().stream().noneMatch(role -> role.getIdLong() == cat.getRequiredRoleId())) {
                    interaction.reply("You do not have the required role, <@&" + cat.getRequiredRoleId() + "> to use this menu.").setEphemeral(true).queue();
                    return;
                }
                List<RoleSelection> roleOptions = Database.ROLE_MENU_STORAGE.getRolesForCategory(interaction.getGuild().getIdLong(), parsedCatID);
                //add all roles that appear in roleIDs, and remove all roles that are only in roleOptions
                List<String> toRemove = roleOptions.stream().map(rs -> String.valueOf(rs.getRoleID())).collect(Collectors.toList());
                List<String> newRoleIDs = new ArrayList<>();
                for (String string : roleIDs) {
                    String[] segments = string.split("\\|", 3);
                    long roleID = Long.parseLong(segments[2]);
                    boolean rolePresent = roleOptions.stream().anyMatch(ro -> ro.getRoleID() == roleID);
                    if (!segments[1].equalsIgnoreCase(String.valueOf(parsedCatID))
                            || !rolePresent) {
                        interaction.reply("This role can no longer be assigned via this category.").setEphemeral(true).queue();
                        return;
                    }
                    newRoleIDs.add(segments[2]);
                }
                toRemove.removeAll(newRoleIDs);
                System.out.println("To add: " + newRoleIDs + ", toRemove: " + toRemove);
                List<Role> rolesToAdd = newRoleIDs.stream().map(role -> interaction.getGuild().getRoleById(role)).collect(Collectors.toList());
                List<Role> rolesToRemove = toRemove.stream().map(role -> interaction.getGuild().getRoleById(role)).collect(Collectors.toList());
                try {
                    interaction.getGuild().modifyMemberRoles(interaction.getMember(), rolesToAdd, rolesToRemove).queue();
                    interaction.editMessage("Roles updated!  \n\nPress the button below to get some more roles.").setEmbeds().setComponents(
                            ActionRow.of(Button.primary("b:ro:m", "More Roles"))
                    ).queue();
                } catch (Exception ex) {
                    interaction.editMessage("Failed to modify roles.  Please report this: " + ex.getMessage()).setEmbeds().setComponents().queue();
                    logger.error("Failed to update roles " + rolesToAdd + " rem:" + rolesToRemove, ex);
                }

                break;
            case "m:ro:d":
                //delete roles from a category
                break;
        }
    }

    @Override
    public List<String> registerHandles() {
        List<String> handles = new ArrayList<>();
//        handles.add("m:ro"); //menu:roles
        handles.add("m:ro:a"); //menu:roles:assign
        handles.add("m:ro:d"); //menu:roles:delete
        handles.add("b:ro:c"); //buttons:roles:category
        handles.add("b:ro:m"); //buttons:roles:menu
        handles.add("b:ro:s"); //buttons:roles:seed
        handles.add("s:ro:r"); //selection:roles:(show)roles
        return handles;
    }

    @Override
    public List<CommandData> registerGlobalCommands() {
        List<CommandData> commands = new ArrayList<>();
        commands.add(Commands.slash("roles", "Get yourself some roles!"));
        commands.add(Commands.slash("manageroles", "Manage the self-selection roles.")
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
                                .addOption(OptionType.INTEGER, "maxnum", "The maximum number of roles from this category that can be selected (default: 25)", false)
                                .addOption(OptionType.ROLE, "reqrole", "The required role for this category.  Leave blank or use @everyone to allow everyone.", false),
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
                                .addOption(OptionType.ROLE, "reqrole", "The required role for this category.  Leave blank or use @everyone to allow everyone.", false),
                        new SubcommandData("sendseed", "Send a seed with a custom message that members can use to gain roles.")
                                .addOption(OptionType.STRING, "message", "The custom message content to send", true)

                ));
        return commands;
    }

    public void handleShowRoles(Member member, InteractionHook hook) {
        EmbedBuilder eb = getMenuEmbedBuilder(member);
        List<List<ItemComponent>> components = getMenuComponents(member);

        if (components.size() > 5) {
            hook.sendMessage("Error! Too many options! Please notify an admin.").setEphemeral(true).queue();
            return;
        }
        hook.sendMessageEmbeds(eb.build())
                .setComponents(
                        components.stream()
                                .map(ActionRow::of)
                                .collect(Collectors.toList())
                )
                .setEphemeral(true).queue();
//        hook.editOriginalEmbeds(eb.build()).setActionRows(builder.stream().map(ActionRow::of).collect(Collectors.toList())).queue();
    }

    private List<List<ItemComponent>> getMenuComponents(Member member) {
        RoleSelectionMenu menu = Database.ROLE_MENU_STORAGE.getSelectionMenu(member.getGuild().getIdLong(), true);
        List<ItemComponent> components = menu.getCategories().stream().map(category -> {
                    Button button = Button.of(category.getStyle(),
                            "b:ro:c|" + category.getCategoryID(), //buttons:roles:category
                            category.getName(),
                            category.getEmoji() == null ? null : Emoji.fromFormatted(category.getEmoji())
                    );
                    return category.getRoles().size() == 0 ? button.asDisabled() : button;
                }
        ).collect(Collectors.toList());
        //break the big list down into lists of length 5.
        List<List<ItemComponent>> builder = new ArrayList<>();
        for (int i = 0; i < components.size(); i++) {
            if (i % 5 == 0) {
                builder.add(new ArrayList<>());
            }
            builder.get(i / 5).add(components.get(i));
        }
        return builder;
    }

    @Nonnull
    private EmbedBuilder getMenuEmbedBuilder(Member member) {
        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle("Role Menu");
        eb.setDescription("Select a category from below to be offered roles for that category!");
        eb.setAuthor(member.getEffectiveName(), null, member.getUser().getEffectiveAvatarUrl());
        eb.setTimestamp(Instant.now());
        return eb;
    }

    public void handleRoleCategoryMenu(Member member, long roleCat, ButtonInteractionEvent hook) {
        RoleSelectionCategory cat = Database.ROLE_MENU_STORAGE.getCategoryFromID(roleCat, member.getGuild().getIdLong(), true);
        List<RoleSelection> roles = cat.getRoles();
        if (roles.size() > 25) {
            hook.editMessage("There are too many roles.  Please contact an admin.").setEmbeds().setComponents().queue();
            return;
        }
        if (cat.getRequiredRoleId() != member.getGuild().getIdLong() && member.getRoles().stream().noneMatch(role -> role.getIdLong() == cat.getRequiredRoleId())) {
            hook.editMessage("You do not have the required role, <@&" + cat.getRequiredRoleId() + "> to use this menu.")
                    .setEmbeds()
                    .setComponents(ActionRow.of(Button.primary("b:ro:m", "More Roles"))).queue();
            return;
        }
        StringSelectMenu.Builder builder = StringSelectMenu.create("s:ro:r|" + roleCat);
        List<Long> roleIDs = member.getRoles().stream().map(Role::getIdLong).collect(Collectors.toList());
        List<SelectOption> defaults = new ArrayList<>();
        for (RoleSelection role : roles) {
            SelectOption option = SelectOption.of(role.getName(),
                            "m:ro:a|" + role.getCategoryID() + "|" + role.getRoleID())
                    .withDescription(role.getDescription())
                    .withEmoji(role.getEmoji() == null ? null : Emoji.fromUnicode(role.getEmoji()));
//                    .withDefault(roleIDs.contains(role.getRoleID()));
            if (roleIDs.contains(role.getRoleID())) {
                defaults.add(option);
            }
            builder.addOptions(option);
        }
        builder.setDefaultOptions(defaults);
        EmbedBuilder emb = new EmbedBuilder();
        emb.setTimestamp(Instant.now());
        emb.setAuthor(member.getEffectiveName(), null, member.getUser().getEffectiveAvatarUrl());
        emb.setTitle("Options for " + cat.getName());
        emb.setColor(member.getColorRaw());
        emb.setDescription(cat.getDescription());
        builder.setMaxValues(cat.getMax());
        builder.setMinValues(cat.getMin());
        if (cat.getMin() > 0 || cat.getMax() < 25) {
            String limits = cat.getMin() > 0 ? "\nYou must select at least " + cat.getMin() + " roles." : "";
            limits += cat.getMax() < 25 ? "\nYou can select a maximum of " + cat.getMax() + " role" + (cat.getMax() > 1 ? "s" : "") + ". " : "";
            emb.addField("Special Limits:", "The following limits apply to this menu:" + limits, false);
        }
//        hook.sendMessageEmbeds(emb.build()).setEphemeral(true).addActionRow(builder.build()).queue();
        hook.editMessageEmbeds(emb.build()).setActionRow(builder.build()).queue(succ -> {
        }, logger::error);
//        logger.info(((SelectionMenuImpl) builder.build()).toData().toString());

    }

    @Override
    public void handleSlashCommand(SlashCommandInteractionEvent event) {
        if (event.getMember() == null) return;
        if (event.getGuild() == null) {
            event.reply("Not run from a guild context; exiting.").setEphemeral(true).queue();
            return;
        }
        switch (event.getName()) {
            case "roles":
                //show the menu
                event.deferReply(true).queue();
                handleShowRoles(event.getMember(), event.getHook());
                break;
            case "manageroles":
                if (!event.getMember().getRoles().contains(event.getGuild().getRoleById(1024355501124898867L))) {
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
                OptionMapping reqroleOpt = event.getOption("reqrole");
                OptionMapping Omin = event.getOption("minnum");
                int min = Omin == null ? 0 : (int) Math.max(0, Omin.getAsLong());
                min = Math.min(25, min);
                OptionMapping Omax = event.getOption("maxnum");
                int max = Omax == null ? 25 : (int) Math.min(25, Omax.getAsLong());
                max = Math.max(0, max);
                long requiredRole = reqroleOpt == null ? event.getGuild().getIdLong() : reqroleOpt.getAsLong();
                switch (event.getSubcommandName()) {
                    case "list": {
                        //args: ?STRING:catname
                        ObjectMapper mapper = new ObjectMapper();
                        String asStr = asString(Database.ROLE_MENU_STORAGE.getSelectionMenu(event.getGuild().getIdLong(), true));
                        if (asStr.length() < 2000){
                            event.reply(asStr).queue();
                        } else {
                            DataObject data = DataObject.fromJson(asStr);
                            event.reply("The content is too large for a single message and has been attached")
                                    .addFiles(FileUpload.fromData(data.toPrettyString().getBytes(StandardCharsets.UTF_8), "roles.json"))
                                    .queue();
                        }
                    }
                    break;
                    case "addcategory": {
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
                                -1,
                                min,
                                max,
                                Collections.emptyList(),
                                requiredRole
                        ));
                        event.reply("Successfully inserted new category.").setEphemeral(true).queue();
                    }
                    break;
                    case "addrole": {
                        //args: STRING:category, ROLE:role, ?STRING:description, ?INT:colour, ?STRING:emoji
                        if (roleOpt == null || catOpt == null) {
                            return;
                        }
                        Role role = roleOpt.getAsRole();
                        if (!role.getGuild().getSelfMember().canInteract(role)) {
                            event.reply("This role cannot be added, as I cannot assign roles higher than myself to members.\n" +
                                    "Please adjust the roles such that this role is lower than my highest role.").setEphemeral(true).queue();
                            return;
                        }
                        try {
                            Database.ROLE_MENU_STORAGE.insertRole(event.getGuild().getIdLong(),
                                    new RoleSelection(event.getGuild().getIdLong(), roleOpt.getAsLong(),
                                            role.getName(), colourOpt == null ? role.getColorRaw() : (int) colourOpt.getAsLong(),
                                            emojiOpt == null ? null : emojiOpt.getAsString(), descOpt == null ? null : descOpt.getAsString(),
                                            -1), catOpt.getAsString());
                            event.reply("Success!").setEphemeral(true).queue();
                        } catch (IllegalArgumentException ex) {
                            event.reply(ex.getMessage()).setEphemeral(true).queue();
                        }
                    }
                    break;
                    case "delrole": {
                        if (catOpt == null || roleOpt == null) {
                            event.reply("Non-null options supplied as null. (Nothing changed).").setEphemeral(true).queue();
                            return;
                        }
                        RoleSelectionMenu menu = Database.ROLE_MENU_STORAGE.getSelectionMenu(event.getGuild().getIdLong(), true);
                        RoleSelectionCategory cat = menu.getCategories().stream().filter(menuStr -> menuStr.getName().equalsIgnoreCase(catOpt.getAsString())).findAny().orElse(null);
                        if (cat == null) {
                            event.reply("No category by the name `" + catOpt.getAsString() + "` was found. (Nothing changed).").setEphemeral(true).queue();
                            return;
                        }
                        if (cat.getRoles().stream().map(RoleSelection::getRoleID).noneMatch(roleId -> roleId.equals(roleOpt.getAsLong()))) {
                            event.reply("No roles by that ID were found in this category. (Nothing changed).").setEphemeral(true).queue();
                            return;
                        }
                        Database.ROLE_MENU_STORAGE.deleteRoleOption(cat.getCategoryID(), event.getGuild().getIdLong(), roleOpt.getAsLong());
                        event.reply("Successfully removed all instances of that role in this category.").setEphemeral(true).queue();
                    }
                    break;
                    case "delroles": {
                        event.reply("Not implemented yet.  Please use the single deletes instead.").setEphemeral(true).queue();
                        //todo
                        //args: STRING:category
                    }
                    break;
                    case "delcategory": {
                        if (catOpt == null) {
                            event.reply("Non-null options supplied as null. (Nothing changed).").setEphemeral(true).queue();
                            return;
                        }
                        RoleSelectionMenu menu = Database.ROLE_MENU_STORAGE.getSelectionMenu(event.getGuild().getIdLong(), false);
                        RoleSelectionCategory cat = menu.getCategories().stream().filter(menuStr -> menuStr.getName().equalsIgnoreCase(catOpt.getAsString())).findAny().orElse(null);
                        if (cat == null) {
                            event.reply("No category by the name `" + catOpt.getAsString() + "` was found. (Nothing changed).").setEphemeral(true).queue();
                            return;
                        }
                        Database.ROLE_MENU_STORAGE.deleteCategory(cat.getCategoryID(), cat.getGuildID());
                        event.reply("Successfully deleted category.").setEphemeral(true).queue();
                    }
                    break;
                    case "modifycategory": {
                        if (catOpt == null) {
                            event.reply("Non-null options supplied as null. (Nothing changed).").setEphemeral(true).queue();
                            return;
                        }
                        RoleSelectionMenu menu = Database.ROLE_MENU_STORAGE.getSelectionMenu(event.getGuild().getIdLong(), false);
                        RoleSelectionCategory cat = menu.getCategories().stream().filter(menuStr -> menuStr.getName().equalsIgnoreCase(catOpt.getAsString())).findAny().orElse(null);
                        if (cat == null) {
                            event.reply("No category by the name `" + catOpt.getAsString() + "` was found. (Nothing changed).").setEphemeral(true).queue();
                            return;
                        }
                        boolean modified = false;
                        if (emojiOpt != null) {
                            cat.setEmoji(emojiOpt.getAsString());
                            modified = true;
                        }
                        if (buttOpt != null) {
                            cat.setStyle(buttOpt.getAsString());
                            modified = true;
                        }
                        if (descOpt != null) {
                            cat.setDescription(descOpt.getAsString());
                            modified = true;
                        }
                        if (Omin != null) {
                            cat.setMin(min);
                            modified = true;
                        }
                        if (Omax != null) {
                            cat.setMax(max);
                            modified = true;
                        }
                        if (reqroleOpt != null) {
                            cat.setRequiredRoleId(requiredRole);
                            modified = true;
                        }
                        if (cat.getMin() > cat.getMax()) {
                            event.reply("Minimum may not be more than the maximum. (Nothing has been changed)").queue();
                            return;
                        }
                        if (!modified) {
                            event.reply("No optional arguments were presented, and nothing has been changed.").setEphemeral(true).queue();
                            return;
                        }
                        Database.ROLE_MENU_STORAGE.updateSelectionCategory(event.getGuild().getIdLong(), cat.getCategoryID(), cat);
                        event.reply("Updated Category.").setEphemeral(true).queue();
                    }
                    break;
                    case "sendseed":
                        OptionMapping message = event.getOption("message");
                        if (message == null){
                            event.reply("You need a message.").setEphemeral(true).queue();
                            return;
                        }
                        event.reply("Message queued!").setEphemeral(true).queue();
                        event.getGuildChannel().sendMessage(message.getAsString())
                                .setComponents(ActionRow.of(Button.primary("b:ro:s", "Role Menu")))
                                .queue();
                }
                break;
        }

    }

    public String asString(Object object) {
        try {
            return mapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            return "Failed JSON.";
        }
    }
}
