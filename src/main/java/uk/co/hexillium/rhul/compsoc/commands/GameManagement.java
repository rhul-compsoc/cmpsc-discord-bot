package uk.co.hexillium.rhul.compsoc.commands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.*;
import uk.co.hexillium.rhul.compsoc.Disabled;
import uk.co.hexillium.rhul.compsoc.commands.handlers.SlashCommandHandler;
import uk.co.hexillium.rhul.compsoc.persistence.Database;
import uk.co.hexillium.rhul.compsoc.persistence.entities.GameAccountBinding;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Disabled
public class GameManagement implements SlashCommandHandler {

    @Override
    public List<CommandData> registerGlobalCommands() {
        return List.of(
                Commands.slash("games", "Manage and register your game bindings")
                        .addSubcommands(
                                new SubcommandData("list", "Show all of your current bindings")
                                        .addOptions(new OptionData(OptionType.STRING, "game", "Filter by a specific game", false)
                                                .addChoices(
                                                        Arrays.stream(GameType.values())
                                                                .map(g -> new Command.Choice(g.displayName, g.gameName))
                                                                .collect(Collectors.toList()))
                                        )
                                ,
                                new SubcommandData("add", "Add a new binding")
                                        .addOptions(new OptionData(OptionType.STRING, "game", "The game you're adding a binding to", true)
                                                .addChoices(
                                                        Arrays.stream(GameType.values())
                                                                .map(g -> new Command.Choice(g.displayName, g.gameName))
                                                                .collect(Collectors.toList()))
                                        )
                                        .addOption(OptionType.STRING, "username", "Your username for this game", true)
                                        .addOption(OptionType.STRING, "uuid", "Your unique identifier for this game. This is calculated for you where possible", false)
                                ,
                                new SubcommandData("remove", "Delete a binding from your account")
                                        .addOption(OptionType.INTEGER, "id", "The ID of the binding you wish to delete")
                        )
        );
    }

    private String formatGameAccountBindings(List<GameAccountBinding> bindings) {
        StringBuilder builder = new StringBuilder();
        int maxID = 10;
        int maxGameNameLength = "Game".length();
        int maxUsernameLength = "Username".length();
        int maxUUIDLength = "Game UUID".length();
        for (GameAccountBinding binding : bindings) {
            if (binding.getBindingId() > maxID) {
                maxID = binding.getBindingId();
            }
            if (binding.getGameUsername().length() > maxUsernameLength) {
                maxUsernameLength = binding.getGameUsername().length();
            }
            if (binding.getGameId().length() > maxGameNameLength) {
                maxGameNameLength = binding.getGameId().length();
            }
            if (binding.getGameUserId().length() > maxUUIDLength) {
                maxUUIDLength = binding.getGameUserId().length();
            }
        }
        int maxIDDecimals = (int) Math.floor(Math.log10(maxID));
        builder.append(String.format("%-" + maxIDDecimals + "d | %-" + maxGameNameLength + "s | %-" + maxUsernameLength + "s | %-" + maxUUIDLength + "s",
                "id", "Game", "Username", "Game UUID"));
        builder.append("-".repeat(maxIDDecimals + maxGameNameLength + maxUsernameLength + maxUUIDLength + 6));
        for (GameAccountBinding binding : bindings){
            builder.append(String.format("%-" + maxIDDecimals + "d | %-" + maxGameNameLength + "s | %-" + maxUsernameLength + "s | %-" + maxUUIDLength + "s",
                    binding.getBindingId(), binding.getGameId(), binding.getGameUsername(), binding.getGameUserId()));
        }
        return builder.toString();
    }

    @Override
    public void handleSlashCommand(SlashCommandInteractionEvent event) {
        if (event.getGuild() == null) {
            event.reply("This command must be executed from a Guild context.").setEphemeral(true).queue();
        }
        OptionMapping gameOpt = event.getOption("game");
        OptionMapping usernameOpt = event.getOption("username");
        OptionMapping uuidOpt = event.getOption("uuid");
        OptionMapping idOpt = event.getOption("id");
        switch (event.getCommandId()) {
            case "games":
                switch (event.getSubcommandName()) {
                    case "list": {
                        List<GameAccountBinding> bindings;
                        if (gameOpt == null) {
                            bindings = Database.GAME_BINDING_STORAGE.getGameBindingsForMember(event.getUser().getIdLong(), event.getGuild().getIdLong());
                        } else {
                            String game = gameOpt.getAsString();
                            bindings = Database.GAME_BINDING_STORAGE.getGameBindingsForMemberGame(event.getUser().getIdLong(), event.getGuild().getIdLong(), game);
                        }
                        event.reply("```" + formatGameAccountBindings(bindings) + "```").setEphemeral(true).queue();
                        break;
                    }
                    case "add": {
                        if (gameOpt == null || usernameOpt == null){
                            event.reply("Required arguments missing.").setEphemeral(true).queue();
                            return;
                        }

                        //do verification check
                        GameType type = GameType.fromName(gameOpt.getAsString());
                        break;
                    }
                    case "remove": {
                        break;
                    }
                }
        }

    }

    private enum GameType {
        MINECRAFT("minecraft", "Minecraft", false),
        ;
        String gameName;
        String displayName;
        boolean requiresUUID;

        GameType(String name, String displayName, boolean requiresUUID) {
            this.gameName = name;
            this.displayName = displayName;
            this.requiresUUID = requiresUUID;
        }

        static GameType fromName(String name){
            for (GameType type : values()){
                if (type.gameName.equals(name)){
                    return type;
                }
            }
            return null;
        }

        void retrieveUUID(String name, Consumer<String> uuid){
            throw new IllegalStateException(this.name() + " does not support UUID retrieval.");
        }
    }
}
