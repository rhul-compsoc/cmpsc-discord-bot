package uk.co.hexillium.rhul.compsoc.commands;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction;
import uk.co.hexillium.rhul.compsoc.CommandEvent;

public class SetAdminRoleCommand extends Command {
	private static final String[] COMMANDS = {"setAdminRole", "setAdmin", "setadminrole"};

	public SetAdminRoleCommand() {
		super("SetAdminRole", "Give a user the admin role", "`{{cmd_prefix}}setAdminRole userTag` "
				+ "make the target user an admin", COMMANDS, "features");
		this.requiredBotPermissions = new Permission[] {Permission.ADMINISTRATOR};
		this.requiredUserPermissions = new Permission[] {Permission.ADMINISTRATOR};
	}

	/**
	 * Gives the target user the admin role.
	 * @param event the CommandEvent
	 */
	@Override
	public void handleCommand(CommandEvent event) {
		

		String[] args = event.getArgs();
		if (args.length != 1) {
			event.reply("Usage: `{{cmd_prefix}}setAdminRole userTag");
			return;
		}
		
		String tag = args[0];
		Guild compSoc = event.getGuild();
		Member newAdmin = compSoc.getMemberByTag(tag);
		
		AuditableRestAction<Void> action = compSoc.addRoleToMember(newAdmin, compSoc.getRolesByName("admin", true).get(0));
		action.queue();
		
	}


}
