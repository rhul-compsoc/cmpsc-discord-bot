package uk.co.hexillium.rhul.compsoc.commands;

import net.dv8tion.jda.api.Permission;
import uk.co.hexillium.rhul.compsoc.CommandEvent;

public class SetAdminRoleCommand extends Command {
	private static final String[] COMMANDS = {"setAdminRole", "setAdmin", "setadminrole"};

	public SetAdminRoleCommand() {
		super("SetAdminRole", "Give a user the admin role", "`{{cmd_prefix}}setAdminRole targetUser` "
				+ "make the target user an admin", COMMANDS, "features");
		this.requiredBotPermissions = new Permission[] {Permission.ADMINISTRATOR};
		this.requiredUserPermissions = new Permission[] {Permission.ADMINISTRATOR};
	}

	@Override
	public void handleCommand(CommandEvent event) {
		// TODO Auto-generated method stub
		
	}

}
