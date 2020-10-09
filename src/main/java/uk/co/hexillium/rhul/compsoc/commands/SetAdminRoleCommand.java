package uk.co.hexillium.rhul.compsoc.commands;

import java.util.List;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import uk.co.hexillium.rhul.compsoc.CommandEvent;

/**
 * This command sets the admin role, or 'god' role that this bot listens to above all others
 * (regardless of whether the role has permissions or not).
 * 
 * This is just a simple pre-hackathon implementation - please modify or delete mercilessly.
 * 
 * @author tom
 *
 */
public class SetAdminRoleCommand extends Command {
  private static final String[] COMMANDS = {"setAdminRole", "setAdmin", "setadminrole"};

  /**
   * Constructor for the command. Sets the name, description and help text. Requires that the caller
   * has admin permissions.
   */
  public SetAdminRoleCommand() {
    super("SetAdminRole", "Give a user the admin role",
        "`{{cmd_prefix}}setAdminRole userTag` " + "make the target user an admin", COMMANDS,
        "features");
    this.requiredUserPermissions = new Permission[] {Permission.ADMINISTRATOR};
  }

  /**
   * Sets the role provided as argument to be the admin role.
   * 
   * Given an event, it extracts the message and searches for the role by ID first and then name.
   * 
   * TODO: Where can we store the role when we've got it? Could we give the selected role an extra
   * permission?
   * 
   * @param event the CommandEvent the event that triggered this, eg "!setAdminRole role"
   */
  @Override
  public void handleCommand(CommandEvent event) {
    // Check the command actually has an argument
    if (event.getArgs().length != 1) {
      event.reply("Usage: `{{cmd_prefix}}setAdminRole role");
      return;
    }
    
    // Get the argument (the role) and the guild, then search for the role within the guild.
    String commandArgument = event.getArgs()[0];
    Guild compSoc = event.getGuild();
    Role adminRole = null;
    try {
      adminRole = compSoc.getRoleById(commandArgument);
    } catch (NumberFormatException e) {
      List<Role> roles = compSoc.getRolesByName(commandArgument, true);
      if (!roles.isEmpty()) {
        adminRole = roles.get(0);
      }
    }
    
    if (adminRole == null) {
      event.reply("Unable to find role " + commandArgument + "."
          + "\nDid you enter a correct ID or role name?");
      return;
    }
    
    /* Add the MANAGE_SERVER permission to the chosen role.
     * Not sure this is entirely what Hexilium wants. I think the idea is the bot can listen to a role even if the role
     * has no permissions? Not sure how we'd implement that - this is just the easiest way at the moment.
     * 
     * Sorry for my shitty code guys :D
     */
    adminRole.getManager().givePermissions(Permission.MANAGE_SERVER).queue();
    // fin?
  }


}
