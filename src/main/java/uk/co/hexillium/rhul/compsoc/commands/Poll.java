package uk.co.hexillium.rhul.compsoc.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.events.interaction.SelectionMenuEvent;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.Button;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.interactions.components.selections.SelectionMenu;
import net.dv8tion.jda.api.requests.restaction.MessageAction;
import net.dv8tion.jda.api.utils.TimeFormat;
import net.dv8tion.jda.api.utils.data.DataObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import uk.co.hexillium.rhul.compsoc.CommandDispatcher;
import uk.co.hexillium.rhul.compsoc.CommandEvent;
import uk.co.hexillium.rhul.compsoc.persistence.Database;
import uk.co.hexillium.rhul.compsoc.persistence.entities.Job;
import uk.co.hexillium.rhul.compsoc.persistence.entities.PollData;
import uk.co.hexillium.rhul.compsoc.persistence.entities.PollSelection;
import uk.co.hexillium.rhul.compsoc.time.TimeUtils;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

public class Poll extends Command implements ComponentInteractionHandler, SlashCommandHandler {

    private static final Logger logger = LogManager.getLogger(Poll.class);
    private static final String[] handleIDs = {"c:pob", "c:pos"}; //poll - button, poll - selection
    private static final char[] progressChars = {'\u258F', '\u258E', '\u258D', '\u258C', '\u258B', '\u258A', '\u2589', '\u2588'};

    //this maps from messageID -> the restaction to update it
    private final HashMap<Long, CompletableFuture<?>> updateMap; //todo
    private final HashMap<Long, Long> updateTimes;               //todo

    private JDA jda;

    public Poll() {
        super("poll", "Create and manage polls", "Use /poll to get started", new String[0], "admin");
        updateMap = new HashMap<>();
        updateTimes = new HashMap<>();
    }

    //range of progress = 0->100 incl.
    public static String generateProgressBar(double progress) {
        StringBuilder strbld = new StringBuilder();
        while (progress > 0.25) {
            double remove = Math.min(progress, 4);
            progress -= remove;
            strbld.append(progressChars[(int) (Math.round(remove * 2) - 1)]);
        }
        return strbld.toString();
    }

    @Override
    public void onLoad(JDA jda, CommandDispatcher manager) {
        this.jda = jda;
        getScheduler().registerHandle("poll_expiry", data -> {
            updateMessage(jda, data.getInt("poll_id"), true);
        });
    }

    public MessageEmbed generateEmbed(PollData data) {
        EmbedBuilder builder = new EmbedBuilder();
        builder.setTitle(data.getName());

        StringBuilder desc = new StringBuilder();
        for (int i = 0; i < data.getOptions().length; i++) {
            desc.append(i).append(") ").append(data.getOption(i)).append("\n");
        }
        desc.append("```\n").append(data.getVotesAsGraph()).append("```");

        builder.setDescription(desc.toString());
        builder.addField("Description", data.getDescription(), false);
        builder.addField("Limits", "You may select up to " + data.getMax_options() + " options.", false);
        builder.addField("Stats", data.getStatsAsString(), false);
        builder.addField("Started", TimeFormat.DATE_TIME_LONG.format(data.getStarted()), true);
        builder.addField("Expires", TimeFormat.DATE_TIME_LONG.format(data.getExpires()), true);

        return builder.build();
    }

    public ActionRow genActionRow(int id, boolean asDisabled) {
        Button button = Button.primary(handleIDs[0] + "|v:" + id, "Vote");
        if (asDisabled) {
            button = button.asDisabled();
        }
        return ActionRow.of(button);
    }

    public ActionRow genActionRow(int id) {
        return genActionRow(id, false);
    }

    public ActionRow genVoteMenu(int id, String[] options, int currentSelection, int max) {
        SelectionMenu.Builder bld = SelectionMenu.create(handleIDs[1] + "|" + id);
        bld.setMinValues(0);
        bld.setMaxValues(max);
        for (int i = 0; i < options.length; i++) {
            SelectOption option = SelectOption.of(options[i], "" + i);
            if ((currentSelection & (1 << i)) == 1 << i) {
                option = option.withDefault(true);
            }
            bld.addOptions(option);
        }
        return ActionRow.of(bld.build());
    }

    @Override
    public void handleCommand(CommandEvent event) {

    }

    @Override
    public void initComponentInteractionHandle(JDA jda) {

    }

    private void updateMessage(JDA jda, int id, boolean expired) {
        PollData data = Database.POLL_STORAGE.fetchPollData(id, true);
        try {
            Guild guild = jda.getGuildById(data.getGuildId());
            if (guild == null) {
                logger.error("Cannot find guild");
                return;
            }
            TextChannel channel = guild.getTextChannelById(data.getChannelId());
            if (channel == null) {
                logger.error("Cannot find channel");
                return;
            }

            MessageAction action = channel.editMessageEmbedsById(data.getMessageId(), generateEmbed(data));
            if (expired || data.isFinished() || data.getExpires().isBefore(OffsetDateTime.now())) {
                action.setActionRows(genActionRow(id, true)).queue(null, logger::error);
            } else {
                action.queue(null, logger::error);
            }
        } catch (NullPointerException ex) {
            logger.error("OWO OOPSIE", ex);
        }
    }

    private void updateMessage(JDA jda, int id) {
        updateMessage(jda, id, false);
    }

    @Override
    public void handleButtonInteraction(ButtonClickEvent interaction, String button) {
        String[] data = button.split(":");
        switch (data[0]) {
            case "v":
                int id = Integer.parseInt(data[1]);
                interaction.deferReply(true).queue();
                Database.runLater(() -> {
                    PollSelection selection = Database.POLL_STORAGE.fetchPollSelectionForUser(id, interaction.getUser().getIdLong());
                    PollData poll = Database.POLL_STORAGE.fetchPollData(id, false);
                    interaction
                            .getHook().sendMessage("Make or adjust your selection: ")
//                            .reply("Make or adjust your selection:")
                            .addActionRows(genVoteMenu(id, poll.getOptions(), selection == null ? 0 : selection.getChoices(), poll.getMax_options()))
                            .queue();
                });
        }
    }

    @Override
    public void handleSelectionMenuInteraction(SelectionMenuEvent interaction) {
        int id = Integer.parseInt(interaction.getComponentId().split("\\|")[1]);
        int set = 0;
        for (String selection : interaction.getValues()) {
            set |= 1 << Integer.parseInt(selection);
        }
        int finalSet = set;
        Database.runLater(() -> {
            Database.POLL_STORAGE.upsertPollSelectionForMember(id, interaction.getUser().getIdLong(), finalSet);
            updateMessage(interaction.getJDA(), id);
        });
        interaction.editMessage("Thanks for voting!").setActionRows().queue();
    }

    @Override
    public List<String> registerHandles() {
        return List.of(handleIDs);
    }

    @Override
    public List<CommandData> registerGlobalCommands() {
//        if (true) return Collections.emptyList();
        return List.of(
                new CommandData("poll", "Manage and create polls via this command")
                        .addSubcommands(
                                new SubcommandData("create", "Create your own poll.")
                                        .addOption(OptionType.STRING, "name", "The name or title of the poll", true)
                                        .addOption(OptionType.STRING, "description", "The description of this poll", true)
                                        .addOption(OptionType.INTEGER, "max", "The maximum number of selections a participant can make", true)
                                        .addOption(OptionType.STRING, "time", "The time before this will expire (give a relative time, like \"2h, 1m3s\")", true)
                                        .addOption(OptionType.CHANNEL, "channel", "The target channel (currently unused)", true)
                                        .addOptions(new OptionData(OptionType.INTEGER, "visibility", "The level of live information given about this poll.", true)
                                                /*
                                                  1<<0 stats
                                                  1<<1 tallies
                                                 */
                                                .addChoice("Stats and tallies", 3)
                                                .addChoice("Just stats", 1)
                                                .addChoice("Just tallies", 2)
                                                .addChoice("No information", 0)
                                        )
                                        .addOption(OptionType.STRING, "opt-1", "The first option (the first two are mandatory)", true)
                                        .addOption(OptionType.STRING, "opt-2", "The second option (the first two are mandatory)", true)
                                        .addOption(OptionType.STRING, "opt-3", "Additional options (the rest are optional)", false)
                                        .addOption(OptionType.STRING, "opt-4", "Additional options (the rest are optional)", false)
                                        .addOption(OptionType.STRING, "opt-5", "Additional options (the rest are optional)", false)
                                        .addOption(OptionType.STRING, "opt-6", "Additional options (the rest are optional)", false)
                                        .addOption(OptionType.STRING, "opt-7", "Additional options (the rest are optional)", false)
                                        .addOption(OptionType.STRING, "opt-8", "Additional options (the rest are optional)", false)
                                        .addOption(OptionType.STRING, "opt-9", "Additional options (the rest are optional)", false)
                                        .addOption(OptionType.STRING, "opt-10", "Additional options (the rest are optional)", false)
                                        .addOption(OptionType.STRING, "opt-11", "Additional options (the rest are optional)", false)
                                        .addOption(OptionType.STRING, "opt-12", "Additional options (the rest are optional)", false)
                                        .addOption(OptionType.STRING, "opt-13", "Additional options (the rest are optional)", false)
                                        .addOption(OptionType.STRING, "opt-14", "Additional options (the rest are optional)", false)
                                        .addOption(OptionType.STRING, "opt-15", "Additional options (the rest are optional)", false)
                                        .addOption(OptionType.STRING, "opt-16", "Additional options (the rest are optional)", false)
//                                        .addOption(OptionType.STRING, "opt-17", "Additional options (the rest are optional)", false)
//                                        .addOption(OptionType.STRING, "opt-18", "Additional options (the rest are optional)", false)
//                                        .addOption(OptionType.STRING, "opt-19", "Additional options (the rest are optional)", false)
//                                        .addOption(OptionType.STRING, "opt-20", "Additional options (the rest are optional)", false)
                                /* When oh when will Discord add varargs to slash commands 😔 */
                                ,
                                new SubcommandData("manage", "Manage an active poll (dummy command).")
                        )
        );
    }

    @Override
    public void handleSlashCommand(SlashCommandEvent event) {
        switch (event.getName()) {
            case "poll":
                if (event.getMember().getRoles().stream().noneMatch(role -> role.getIdLong() == 500612754185650177L)) {
                    event.reply(">:(").setEphemeral(true).queue();
                    return;
                }
                if (event.getSubcommandName() == null) {
                    //something has gone wrong
                    event.reply("You've broken something... that's not good...").setEphemeral(true).queue();
                    logger.error("User manged to get empty subcommand name " + event);
                    return;
                }
                switch (event.getSubcommandName()) {
                    case "create":
                        OptionMapping maxOpt = event.getOption("max");
                        OptionMapping nameOpt = event.getOption("name");
                        OptionMapping timeOpt = event.getOption("time");
                        OptionMapping descOpt = event.getOption("description");

                        if (maxOpt == null || nameOpt == null || timeOpt == null || descOpt == null) {
                            event.reply("Somehow you've set an optional argument to null. Go and think about what you've done.").setEphemeral(true).queue();
                            return;
                        }

                        String name = nameOpt.getAsString();
                        if (name.length() > MessageEmbed.TITLE_MAX_LENGTH) {
                            event.reply("Name may not be more than " + MessageEmbed.TITLE_MAX_LENGTH + " characters").setEphemeral(true).queue();
                            return;
                        }
                        String description = descOpt.getAsString();
                        if (description.length() > 1024) {
                            event.reply("Description may not be more than 1024 characters").setEphemeral(true).queue();
                            return;
                        }
                        long max = maxOpt.getAsLong();
                        max = Math.max(1, Math.min(25, max)); //bound it between 1 and 25;
                        String time = timeOpt.getAsString();
                        Matcher matcher = TimeUtils.timePattern.matcher(time);
                        if (!matcher.matches()) {
                            event.reply("Time does not appear to be a valid offset.  Some valid examples: `5m 8h`, `11s`, `13s, 55m`")
                                    .setEphemeral(true).queue();
                            return;
                        }
                        OffsetDateTime expires = TimeUtils.parseTarget(time);
                        List<String> options = event.getOptions().stream()
                                .filter(opt -> opt.getName().startsWith("opt-"))
//                                .sorted(Comparator.comparing(optionMapping -> Integer.parseInt(optionMapping.getName().split("-")[1]))) //the options may already be sorted
                                .map(OptionMapping::getAsString)
                                .collect(Collectors.toList());
                        if (options.stream().anyMatch(opt -> opt.length() > 100)) {
                            event.reply("Options must each be less than 100 characters.").setEphemeral(true).queue();
                            return;
                        }
                        event.reply("Processing request.").setEphemeral(true).queue();
                        long finalMax = max;
                        Database.runLater(() -> {
//                            OffsetDateTime expires = OffsetDateTime.now().plus(2, ChronoUnit.DAYS);
                            int pollId = Database.POLL_STORAGE.insertNewPollData(options.toArray(new String[0]),
                                    OffsetDateTime.now(), false, expires,
                                    (int) finalMax, name, description);
                            PollData data = Database.POLL_STORAGE.fetchPollData(pollId, true);
                            DataObject dobj = DataObject.empty();
                            dobj.put("poll_id", pollId);
                            getScheduler().submitJob(new Job(-1, System.currentTimeMillis(), Instant.from(expires).toEpochMilli(), "poll_expiry", dobj));
                            event.getTextChannel().sendMessageEmbeds(generateEmbed(data))
                                    .setActionRows(genActionRow(pollId))
                                    .queue(msg ->
                                            Database.runLater(
                                                    () -> {
                                                        Database.POLL_STORAGE.updatePollMessageLocation(pollId,
                                                                msg.getChannel().getIdLong(),
                                                                msg.getGuild().getIdLong(),
                                                                msg.getIdLong()
                                                        );
                                                    }
                                            ));
                        });


                }
        }

    }
}
