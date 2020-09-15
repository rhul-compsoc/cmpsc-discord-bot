package uk.co.hexillium.rhul.compsoc.commands;

import uk.co.hexillium.rhul.compsoc.CommandDispatcher;
import uk.co.hexillium.rhul.compsoc.CommandEvent;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;

import java.util.Arrays;

public abstract class Command {

    protected String description;
    protected String help;
    protected String[] commands;
    protected String name;
    protected String category;
    protected Permission[] requiredBotPermissions = new Permission[]{};
    protected Permission[] requiredUserPermissions = new Permission[]{};

    public Command(String name, String description, String help, String[] commands, String category){
        this.name = name;
        this.description = description;
        this.help = help;
        this.commands = commands;
        this.category = category;
    }

    /**
     * Get the message sent to a user if the bot doesn't have permission to run this command. Override as necessary.
     * @return the String for the no perms message
     */
    public String getNoBotPermissions(){
        return "I don't have all of the required permissions to do this!.\n\nI need: " + Arrays.toString(requiredBotPermissions);
    }

    /**
     * Get the message sent to a user if the user doesn't have permission to run this command. Override as necessary.
     * @return the String for the no perms message
     */
    public String getNoUserPermissions(){
        return "You don't have all of the required permissions to do this!.\n\nYou need: " + Arrays.toString(requiredUserPermissions);
    }

    /**
     * Gets the help that was passed into this command.
     * @return the help message
     */
    public String getHelp(CommandEvent event){
        return help;
    }

    /**
     * Gets the name of this command.
     * @return the name
     */
    public String getName(){
        return name;
    }

    /**
     * Gets the category that was assigned to this command
     * @return the category
     */
    public String getCategory(){
        return category;
    }

    /**
     * Gets the array of triggers for this command
     * @return all of the triggers.
     */
    public String[] getCommands(){
        return commands;
    }

    /**
     * Gets the description of the command.
     * @return the description
     */
    public String getDescription(){
        return description;
    }

    /**
     * Tests if the bot has permission to run this command
     * @param event the CommandEvent context from which it was run
     * @return true if all permissions are present, else false
     */
    public boolean testBotPermissions(CommandEvent event){
        if (event.isGuildMessage() && event.getSelfMember().hasPermission(event.getTextChannel(), requiredBotPermissions)){
            return true;
        }
        return !event.isGuildMessage();
    }

    /**
     * Tests if the user has permission to run this command
     * @param event the CommandEvent context from which it was run
     * @return true if all permissions are present, else false
     */
    public boolean testUserPermissions(CommandEvent event){
        if (event.isGuildMessage() && event.getMember() != null && event.getMember().hasPermission(event.getTextChannel(), requiredUserPermissions)){
            return true;
        }
        return !event.isGuildMessage();
    }

    public final void internalHandleCommand(CommandEvent event){
        if (!testBotPermissions(event)){
            event.sendEmbed("No Permissions", getNoBotPermissions(),
                    0xFF0000);
            return;
        }
        if (!testUserPermissions(event)){
            event.sendEmbed("No Permissions", getNoUserPermissions(),
                    0xFF0000);
            return;
        }
        handleCommand(event);
    }

    /**
     * The main command trigger
     * @param event the context from which it was run
     */
    abstract public void handleCommand(CommandEvent event);

    /**
     * If the Command requires a guild to be executed
     * @return true if requires guild
     */
    public boolean requireGuild(){
        return true;
    }

    /**
     * This will be called when JDA signals its ready.
     * @param jda the JDA context
     * @param manager the commandDispatcher context.
     */
    public void onLoad(JDA jda, CommandDispatcher manager){}

}
